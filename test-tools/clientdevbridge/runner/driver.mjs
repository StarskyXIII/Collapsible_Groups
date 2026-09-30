import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {sleep} from './session.mjs';
import {StableFrames} from './stability.mjs';
import {checkVisual} from './visual.mjs';
export function usable(s,viewer,requireIndex=true) { return s.fresh===true&&!s.observerError&&(!requireIndex||s.indexReady===true)&&s.viewer===viewer&&s.modifiersReleased===true; }
export function center(r) {
 assert(r&&Number.isFinite(r.x)&&Number.isFinite(r.y)&&r.w>0&&r.h>0,'Missing target bounds');
 return {x:r.x+r.w/2,y:r.y+r.h/2};
}
export function memberIds(s) { return [...s.previewItems,...s.previewFluids,...(s.previewGeneric??[])].sort(); }
export class Driver {
 constructor(session,folder,report) { this.session=session;this.b=session.bridge;this.viewer=session.manifest.viewer??session.manifest.profile.slice(4);this.folder=folder;this.report=report;this.inputCalls=0;this.phase='setup'; }
 async state(predicate=()=>true,timeout=15000,requireIndex=true) {
  let s;const stable=new StableFrames();const end=Date.now()+timeout;
  while(Date.now()<end) {
   s=await this.b.call('cgtest.observe');
   if(s.observerError)throw Error('Observer failed: '+s.observerError);
   if(stable.accept(s,usable(s,this.viewer,requireIndex)&&s.inputCalls>=this.inputCalls&&predicate(s))) {
    this.current=s;this.samples=stable.samples;return s;
   }
   await sleep(120);
  }
  await fs.writeFile(path.join(this.folder,'failure-state.json'),JSON.stringify(s,null,2));
  throw Error('Timeout in '+this.phase+'; last screen='+s?.screen+', viewer='+s?.viewer+', indexReady='+s?.indexReady+' (see failure-state.json)');
 }
 async input(p) { const r=await this.b.call('cgtest.input',p);assert.equal(r.route,'MouseHandler/KeyboardHandler');this.inputCalls=r.calls;return r; }
 async click(r,modifiers=0) {return this.input({action:'click',...center(r),modifiers});}
 async text(r,value) {assert.equal(typeof r.value,'string');return this.input({action:'text',...center(r),text:value,previousLength:[...r.value].length});}
 async key(key) {return this.input({action:'key',key});}
 async check(name,predicate,timeout) {
  this.phase=name;const state=await this.state(predicate,timeout);
  this.report.cases.push({name,status:'passed',state,samples:this.samples});
  if(this.report.cases.length>100)throw Error('Unexpected case count');
  await this.save();return state;
 }
 async save() {await fs.writeFile(path.join(this.folder,'result.json'),JSON.stringify(this.report,null,2)+'\n');}
 async capture(name) {
  const s=await this.b.call('cgtest.observe');
  if(s.guiHeight>4)await this.input({action:'move',x:2,y:s.guiHeight-2});
  const shot=await this.b.call('screenshot',{afterTicks:3});
  const file=path.join(this.folder,name+'.png');await fs.writeFile(file,Buffer.from(shot.png,'base64'));
  await checkVisual(this,name,file);
 }
 async manager() {
  let s=await this.state();
  if(s.screen!=='GroupManagerScreen') {
   assert(s.managerButton,'No real Groups button');await this.click(s.managerButton);
  }
  return this.state(s=>s.screen==='GroupManagerScreen'&&!s.pending);
 }
 async editor(id) {
  let s=await this.manager();const group=s.groups.find(g=>g.id===id);assert(group,'No group '+id);
  await this.text(s.search,group.name);s=await this.state(s=>s.cards?.some(c=>c.id===id));
  await this.click(s.cards.find(c=>c.id===id).edit);
  return this.state(s=>s.screen==='GroupEditorScreen'&&!s.loading);
 }
 async inventory() {
  const s=await this.state(()=>true,15000,false);
  if(s.screen==='GroupManagerScreen')await this.click(s.controls.back);
  else if(s.screen==='none')await this.key(69);
  return this.state(s=>s.managerButton&&s.viewerSearch);
 }
 async findHeader(id) {
  this.phase='locate-header';
  let s=await this.state();
  for(let page=0;page<30;page++) {
   if(s.overlayCells.some(c=>c.role==='header'&&c.groupId===id))return s;
   assert(s.overlayCells.length,'No overlay entries to page through');
   await this.input({action:'scroll',...center(s.overlayCells[0]),dy:-1});
   s=await this.state();
  }
  throw Error('Header not found after 30 native page scrolls: '+id);
 }
 async selectItem(id,query,modifiers=0) {
  let s=await this.state(s=>s.screen==='GroupEditorScreen'&&!s.loading);
  await this.text(s.search,query);
  s=await this.state(s=>s.search.value===query&&s.sourceCells.some(c=>c.id===id));
  const matches=s.sourceCells.filter(c=>c.id===id);assert(matches.length,'No visible ingredient '+id);
  await this.click(matches[0],modifiers);
  return this.state(s=>memberIds(s).includes(id));
 }
}

import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {betaCore,betaPersistence} from './beta-cases.mjs';
import {memberIds,center} from './driver.mjs';
import {inputSmoke} from './input-cases.mjs';
import {advanced} from './advanced-cases.mjs';
import {sleep} from './session.mjs';
export async function ensureWorld(d) {
 const game=d.session.manifest.gameDir;
 let s=await d.b.call('cgtest.observe');
 const deadline=Date.now()+60000;
 const ready=()=>s.fresh&&(s.inWorld||['TitleScreen','class_442'].includes(s.screen));
 while(!ready()&&Date.now()<deadline){await sleep(150);s=await d.b.call('cgtest.observe');}
 assert(ready(),'Startup did not reach an overlay-free title screen');
 if(!s.inWorld) {
  const saves=await fs.readdir(path.join(game,'saves')).catch(()=>[]);
  if(saves.includes('CG Bridge Smoke'))await d.b.call('world.load',{name:'CG Bridge Smoke'},90000);
  else await d.b.call('world.reset',{name:'CG Bridge Smoke'},90000);
 }
}
export async function core(d) {
 const game=d.session.manifest.gameDir;
 await ensureWorld(d);
 let s;
 s=await d.inventory();await d.text(s.viewerSearch,'');await d.check('viewer-and-native-entry',s=>Boolean(s.managerButton&&s.viewerSearch&&s.overlayCells.length));
 if(d.report.negativeControl)await d.check('intentional-wrong-viewer',s=>s.viewer===(d.viewer==='jei'?'emi':'jei'),1500);
 await d.manager();await d.check('manager-ready',s=>s.screen==='GroupManagerScreen'&&!s.pending&&s.filteredCount>0);
 s=await d.state();await d.click(s.controls.new);s=await d.state(s=>s.screen==='GroupEditorScreen'&&!s.loading);
 await d.text(s.nameField,'Bridge Smoke');
 await d.selectItem('item:minecraft:stone','stone');
 await d.selectItem('item:minecraft:dirt','dirt');
 await d.check('draft-two-items',s=>JSON.stringify(memberIds(s))===JSON.stringify(['item:minecraft:dirt','item:minecraft:stone']));
 await d.capture('editor-two-items');
 s=d.current;await d.click(s.controls.save);
 s=await d.check('save-and-manager',s=>s.screen==='GroupManagerScreen'&&s.groups.some(g=>g.name==='Bridge Smoke'));
 const group=s.groups.find(g=>g.name==='Bridge Smoke'),id=group.id;d.report.groupId=id;
 const file=path.join(game,'config/collapsiblegroups/groups',id+'.json');const first=JSON.parse(await fs.readFile(file,'utf8'));
 assert.equal(first.id,id);assert.equal(first.name.fallback,'Bridge Smoke');assert.equal(first.enabled,true);
 assert.deepEqual(first.filter,{any:[{type:'item',id:'minecraft:stone'},{type:'item',id:'minecraft:dirt'}]});
 await d.editor(id);await d.check('reopen-two-items',s=>JSON.stringify(memberIds(s))===JSON.stringify(['item:minecraft:dirt','item:minecraft:stone']));
 s=d.current;await d.click(s.controls.FLUIDS);s=await d.state(s=>s.contentType==='FLUIDS');
 await d.selectItem('fluid:minecraft:water','water');
 s=await d.check('mixed-item-fluid-preview',s=>JSON.stringify(memberIds(s))===JSON.stringify(['fluid:minecraft:water','item:minecraft:dirt','item:minecraft:stone']));
 await d.capture('editor-mixed');await d.click(s.controls.save);
 s=await d.state(s=>s.screen==='GroupManagerScreen');
 await d.text(s.search,'Bridge Smoke');
 s=await d.check('mixed-save-counts',s=>s.cards?.some(c=>c.id===id&&c.items===2&&c.fluids===1));
 const mixed=JSON.parse(await fs.readFile(file,'utf8'));
 assert.deepEqual(mixed.filter,{any:[{type:'item',id:'minecraft:stone'},{type:'item',id:'minecraft:dirt'},{type:'fluid',id:'minecraft:water'}]});
 await d.capture('manager-mixed');
 s=await d.inventory();await d.text(s.viewerSearch,'');s=await d.findHeader(id);
 let header=s.overlayCells.find(c=>c.role==='header'&&c.groupId===id);
 if(header.expanded) {await d.click(header);s=await d.state(s=>s.overlayCells.some(c=>c.groupId===id&&c.role==='header'&&!c.expanded));}
 await d.click(s.overlayCells.find(c=>c.role==='header'&&c.groupId===id));
 await d.check('expand-mixed-header',s=>s.overlayCells.some(c=>c.role==='header'&&c.groupId===id&&c.expanded)&&['item:minecraft:stone','item:minecraft:dirt','fluid:minecraft:water'].every(id=>s.overlayCells.some(c=>c.id===id)));
 await d.capture('overlay-expanded');
 s=d.current;await d.click(s.overlayCells.find(c=>c.role==='header'&&c.groupId===id));
 s=await d.check('collapse-mixed-header',s=>s.overlayCells.some(c=>c.role==='header'&&c.groupId===id&&!c.expanded)&&!s.overlayCells.some(c=>['item:minecraft:stone','item:minecraft:dirt','fluid:minecraft:water'].includes(c.id)));
 await d.text(s.viewerSearch,'cg_no_such_item_93df2e');
 s=await d.check('search-negative',s=>s.viewerSearch?.value==='cg_no_such_item_93df2e'&&s.overlayCells.length===0);
 await d.text(s.viewerSearch,'stone');
 s=await d.check('search-positive-small-group-ungrouped',s=>s.viewerSearch?.value==='stone'&&s.overlayCells.some(c=>c.id==='item:minecraft:stone')&&!s.overlayCells.some(c=>c.role==='header'&&c.groupId===id));
 await d.text(s.viewerSearch,'');
 await d.manager();s=await d.state();await d.text(s.search,'Bridge Smoke');s=await d.state(s=>s.cards.some(c=>c.id===id));await d.click(s.cards.find(c=>c.id===id).toggle);
 await d.check('disable-group',s=>s.cards?.some(c=>c.id===id&&!c.enabled));
 s=await d.inventory();await d.text(s.viewerSearch,'stone');await d.check('disabled-overlay',s=>!s.overlayCells.some(c=>c.groupId===id)&&s.overlayCells.some(c=>c.id==='item:minecraft:stone'));
 await d.text(d.current.viewerSearch,'');
 await d.manager();s=await d.state();await d.text(s.search,'Bridge Smoke');s=await d.state(s=>s.cards.some(c=>c.id===id));await d.click(s.cards.find(c=>c.id===id).toggle);await d.check('enable-group',s=>s.cards?.some(c=>c.id===id&&c.enabled));
 const before=await fs.readFile(file);
 await d.editor(id);s=d.current;await d.text(s.nameField,'');
 s=await d.state(s=>s.nameField.value==='');await d.click(s.controls.save);
 await d.check('invalid-empty-name-blocks-save',s=>s.screen==='GroupEditorScreen'&&s.nameField.value==='');
 assert((await fs.readFile(file)).equals(before),'Invalid save changed disk');
 s=d.current;await d.click(s.controls.cancel);s=await d.state(s=>s.dialog);
 await d.click(s.dialogPrimary);await d.check('discard-leaves-file-unchanged',s=>s.screen==='GroupManagerScreen');
 assert((await fs.readFile(file)).equals(before),'Cancel changed disk');
 await d.editor(id);s=await d.check('mixed-reopen',s=>JSON.stringify(memberIds(s))===JSON.stringify(['fluid:minecraft:water','item:minecraft:dirt','item:minecraft:stone']));
 await d.click(s.controls.cancel);await d.state(s=>s.screen==='GroupManagerScreen');
 await inputSmoke(d);
 await advanced(d,id);
 await betaCore(d,id);
 s=await d.inventory();s=await d.findHeader(id);header=s.overlayCells.find(c=>c.groupId===id&&c.role==='header');if(!header.expanded)await d.click(header);
 await d.check('persist-expanded',s=>s.overlayCells.some(c=>c.groupId===id&&c.role==='header'&&c.expanded));
 d.report.persisted=JSON.parse(await fs.readFile(file,'utf8'));return id;
}
export async function persistence(d,id) {
 await ensureWorld(d);
 await d.inventory();
 await d.findHeader(id);
 await d.check('cold-restart-expanded',s=>s.groups.filter(g=>g.id===id).length===1&&s.overlayCells.some(c=>c.role==='header'&&c.groupId===id&&c.expanded));
 await d.editor(id);
 await d.check('cold-restart-mixed-members',s=>JSON.stringify(memberIds(s))===JSON.stringify(['fluid:minecraft:water','item:minecraft:dirt','item:minecraft:stone']));
 const file=path.join(d.session.manifest.gameDir,'config/collapsiblegroups/groups',id+'.json');
 assert.deepEqual(JSON.parse(await fs.readFile(file,'utf8')),d.report.persisted,'Cold restart changed saved group');
 await d.capture('restart-editor');
 await betaPersistence(d,id);
}

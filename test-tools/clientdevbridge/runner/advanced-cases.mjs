import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {memberIds,center} from './driver.mjs';
const mixed=['fluid:minecraft:water','item:minecraft:dirt','item:minecraft:stone'];
const children=(s,id)=>s.overlayCells.filter(c=>c.role==='child'&&c.groupId===id).map(c=>c.id).sort();
const same=(a,b)=>JSON.stringify(a)===JSON.stringify([...b].sort());
async function expand(d,id) {
 let s=await d.findHeader(id),h=s.overlayCells.find(c=>c.role==='header'&&c.groupId===id);
 if(!h.expanded)await d.click(h);
 return d.state(s=>s.overlayCells.some(c=>c.role==='header'&&c.groupId===id&&c.expanded));
}
async function card(d,id) {
 let s=await d.manager();const group=s.groups.find(g=>g.id===id);assert(group);
 await d.text(s.search,group.name);return d.state(s=>s.cards.some(c=>c.id===id));
}
export async function advanced(d,id) {
 d.phase='rules-entry';await d.editor(id);let s=d.current;
 const file=path.join(d.session.manifest.gameDir,'config/collapsiblegroups/groups',id+'.json'),before=await fs.readFile(file);
 await d.click(s.controls.RULES);
 s=await d.check('rules-existing-tree',s=>s.mode==='RULES'&&s.ruleRows.length===4&&s.ruleRows[0].kind==='ANY');
 await d.click(s.ruleRows.find(r=>r.value==='minecraft:stone').edit);
 s=await d.state(s=>s.rulesModal==='FORM');
 await d.input({action:'text',...center(s.ruleForm.formPrimary),previousLength:0,text:'!'});
 s=await d.state(s=>s.ruleForm.formPrimary.value==='minecraft:stone!');
 await d.click(s.ruleForm.confirm);
 s=await d.check('invalid-rule-retains-last-valid-preview',s=>s.rulesModal==='NONE'&&s.ruleValidationErrors.length>0&&same(memberIds(s),mixed));
 await d.capture('invalid-rule-preview');
 await d.click(s.controls.save);
 s=await d.check('invalid-rule-blocks-save',s=>s.screen==='GroupEditorScreen'&&s.ruleValidationErrors.length>0);
 assert((await fs.readFile(file)).equals(before),'Invalid rule saved to disk');
 await d.click(s.ruleRows.find(r=>r.value==='minecraft:stone!').edit);
 s=await d.state(s=>s.rulesModal==='FORM');await d.text(s.ruleForm.formPrimary,'minecraft:stone');
 s=await d.state(s=>s.ruleForm.formPrimary.value==='minecraft:stone');await d.click(s.ruleForm.confirm);
 s=await d.check('rule-repair-restores-valid-preview',s=>s.rulesModal==='NONE'&&s.ruleValidationErrors.length===0&&same(memberIds(s),mixed));
 await d.click(s.ruleRows[0].chip);
 s=await d.check('rule-any-to-all-updates-preview',s=>s.ruleRows[0].kind==='ALL'&&memberIds(s).length===0&&s.ruleValidationErrors.length===0);
 await d.click(s.ruleRows[0].chip);
 s=await d.check('rule-all-to-any-restores-members',s=>s.ruleRows[0].kind==='ANY'&&same(memberIds(s),mixed));
 await d.click(s.controls.cancel);s=await d.state(s=>s.dialog);await d.click(s.dialogPrimary);
 await d.check('rule-cancel-preserves-disk',s=>s.screen==='GroupManagerScreen');
 assert((await fs.readFile(file)).equals(before),'Cancelled rule changes altered disk');
 s=d.current;await d.click(s.controls.new);s=await d.state(s=>s.screen==='GroupEditorScreen'&&!s.loading);
 await d.text(s.nameField,'Bridge Overlap');
 for(const item of ['stone','granite','andesite'])await d.selectItem('item:minecraft:'+item,item);
 s=await d.state();await d.click(s.controls.save);
 s=await d.state(s=>s.screen==='GroupManagerScreen'&&s.groups.some(g=>g.name==='Bridge Overlap'));
 const other=s.groups.find(g=>g.name==='Bridge Overlap').id;
 await d.inventory();await expand(d,other);
 await d.check('overlap-second-group-excludes-owned-stone',s=>same(children(s,other),['item:minecraft:granite','item:minecraft:andesite']));
 await expand(d,id);
 await d.check('overlap-first-group-owns-stone-once',s=>same(children(s,id),mixed)&&s.overlayCells.filter(c=>c.id==='item:minecraft:stone').length===1);
 s=await card(d,id);await d.click(s.cards.find(c=>c.id===id).toggle);
 await d.state(s=>s.groups.some(g=>g.id===id&&!g.enabled));await d.inventory();await expand(d,other);
 await d.check('overlap-disabled-owner-transfers-stone',s=>same(children(s,other),['item:minecraft:stone','item:minecraft:granite','item:minecraft:andesite'])&&!s.overlayCells.some(c=>c.role==='header'&&c.groupId===id));
 await d.capture('ownership-transfer');
 s=await card(d,id);await d.click(s.cards.find(c=>c.id===id).toggle);
 await d.state(s=>s.groups.some(g=>g.id===id&&g.enabled));await d.inventory();await expand(d,id);
 await d.check('overlap-reenabled-owner-reclaims-stone',s=>same(children(s,id),mixed)&&s.overlayCells.filter(c=>c.id==='item:minecraft:stone').length===1);
 s=await card(d,other);await d.click(s.cards.find(c=>c.id===other).delete,1);
 await d.check('overlap-cleanup',s=>!s.groups.some(g=>g.id===other)&&s.groups.some(g=>g.id===id&&g.enabled));
}

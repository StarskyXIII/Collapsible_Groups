import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
export const category='local:11111111-1111-4111-8111-111111111111';
const configDir=d=>path.join(d.session.manifest.gameDir,'config/collapsiblegroups');
export async function prepareBeta(m) {
 const dir=path.join(m.gameDir,'config/collapsiblegroups');await fs.mkdir(dir,{recursive:true});
 await fs.writeFile(path.join(dir,'categories.json'),JSON.stringify({version:1,custom_categories:{[category]:'Bridge Category'},source_names:{},group_categories:{}}));
}
async function action(d,name) {
 let s=await d.state();await d.click(s.controls.new);
 s=await d.state(s=>s.batchMenuOpen);await d.click(s.batchActions[name]);
}
export async function betaCore(d,id) {
 let s=await d.manager();await d.click(s.controls.new);
 s=await d.state(s=>s.screen==='GroupEditorScreen'&&!s.loading);await d.text(s.nameField,'Bridge Batch');
 await d.selectItem('item:minecraft:oak_planks','oak_planks');s=await d.state();await d.click(s.controls.save);
 s=await d.state(s=>s.screen==='GroupManagerScreen'&&s.groups.some(g=>g.name==='Bridge Batch'));
 const second=s.groups.find(g=>g.name==='Bridge Batch').id;d.report.beta={second,category};
 await d.text(s.search,'Bridge ');s=await d.state(s=>s.cards.length===2&&s.cards.every(c=>[id,second].includes(c.id)));
 await d.click(s.controls.batch);s=await d.state(s=>s.batchMode);
 for(const card of s.cards)await d.input({action:'click',x:card.x+60,y:card.y+14});
 await d.check('beta-batch-selects-two',s=>s.selectedCount===2);
 await action(d,'DISABLE');await d.check('beta-batch-disable',s=>s.cards.length===2&&s.cards.every(c=>!c.enabled));
 for(const group of [id,second])assert.equal(JSON.parse(await fs.readFile(path.join(configDir(d),'groups',group+'.json'),'utf8')).enabled,false);
 await action(d,'ENABLE');await d.check('beta-batch-enable',s=>s.cards.length===2&&s.cards.every(c=>c.enabled));
 for(const group of [id,second])assert.equal(JSON.parse(await fs.readFile(path.join(configDir(d),'groups',group+'.json'),'utf8')).enabled,true);
 await action(d,'MOVE');s=await d.state(s=>s.screen==='CategoryPickerScreen');
 await d.text(s.search,'Bridge Category');s=await d.state(s=>s.categoryRows.some(r=>r.id===category));
 await d.click(s.categoryRows.find(r=>r.id===category));s=await d.state(s=>s.selectedCategory===category);
 await d.click(s.controls.confirm);
 s=await d.check('beta-move-two-groups',s=>s.screen==='GroupManagerScreen'&&[id,second].every(g=>s.categories.group_categories[g]===category));
 const disk=JSON.parse(await fs.readFile(path.join(configDir(d),'categories.json'),'utf8'));
 assert.deepEqual(disk.group_categories,{[id]:category,[second]:category});
 if(s.batchMode){await d.click(s.controls.batch);s=await d.state(s=>!s.batchMode);}
 await d.capture('beta-manager-category');
 const before=s.sidebarOpen;await d.click(s.controls.category);
 s=await d.check('beta-category-sidebar-toggle',s=>s.sidebarOpen!==before);
 await d.click(s.controls.category);await d.state(s=>s.sidebarOpen===before);
}
export async function betaPersistence(d,id) {
 let s=await d.state();await d.click(s.controls.cancel);s=await d.state(s=>s.screen==='GroupManagerScreen');
 await d.check('beta-cold-restart-category',s=>[id,d.report.beta.second].every(g=>s.categories.group_categories[g]===category&&s.groups.some(row=>row.id===g&&row.enabled)));
}

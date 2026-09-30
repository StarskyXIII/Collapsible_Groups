import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {ensureWorld} from './cases.mjs';
import {profilePath,readJson,hash,occupied,owned} from './session.mjs';
const unknownId='cg_bridge_unknown';
const unknownFilter={any:[{type:'item',id:'minecraft:stone'},{type:'bridge:unavailable',id:'bridge:opaque'}]};
const chemistry=['mekanism:chemical:mekanism:hydrogen','mekanism:chemical:mekanism:oxygen'].sort();
const same=(a,b)=>JSON.stringify([...a].sort())===JSON.stringify([...b].sort());
const groupFile=(d,id)=>path.join(d.session.manifest.gameDir,'config/collapsiblegroups/groups',id+'.json');
async function writeGroup(game,id,name,filter) {
 const folder=path.join(game,'config/collapsiblegroups/groups');await fs.mkdir(folder,{recursive:true});
 await fs.writeFile(path.join(folder,id+'.json'),JSON.stringify({id,name,enabled:true,filter},null,2));
}
export async function prepareExtended(m) {
 if(m.scenario==='generic')await writeGroup(m.gameDir,unknownId,{translate:'collapsible_groups.group.'+unknownId,fallback:'Bridge Unknown'},unknownFilter);
 if(m.scenario==='locale') {
  await writeGroup(m.gameDir,'cg_bridge_locale',{translate:'collapsible_groups.group.cg_bridge_locale',fallback:'Bridge Locale Fallback'},{type:'item',id:'minecraft:stone'});
  await writeGroup(m.gameDir,'cg_bridge_fallback',{translate:'bridge.missing.translation',fallback:'Bridge 保留名稱'},{type:'item',id:'minecraft:dirt'});
  const dir=path.join(m.gameDir,'config/collapsiblegroups/lang');await fs.mkdir(dir,{recursive:true});
  await fs.writeFile(path.join(dir,'zh_tw.json'),JSON.stringify({'collapsible_groups.group.cg_bridge_locale':'Bridge 測試群組'}));
 }
}
async function unknownRoundtrip(d) {
 let s=await d.editor(unknownId);await d.click(s.controls.RULES);
 s=await d.check('unknown-rule-visible',s=>s.ruleRows.some(r=>r.value==='bridge:opaque'));
 await d.text(s.nameField,'Bridge Unknown Preserved');await d.click(s.controls.save);
 await d.check('unknown-rule-save',s=>s.screen==='GroupManagerScreen');
 assert.deepEqual((await readJson(groupFile(d,unknownId))).filter,unknownFilter,'Unknown leaf lost on save');
}
export async function genericCore(d) {
 await ensureWorld(d);await d.inventory();await d.manager();await unknownRoundtrip(d);
 let s=await d.state();await d.click(s.controls.new);s=await d.state(s=>s.screen==='GroupEditorScreen'&&!s.loading);
 await d.text(s.nameField,'Bridge Chemical');await d.click(s.controls.OTHER_TYPES);
 await d.check('generic-tab-available',s=>s.hasGenericIngredients&&s.contentType==='OTHER_TYPES');
 for(const name of ['oxygen','hydrogen'])await d.selectItem('mekanism:chemical:mekanism:'+name,name);
 s=await d.check('generic-two-chemical-preview',s=>same(s.previewGeneric,chemistry)&&s.previewItems.length===0&&s.previewFluids.length===0);
 await d.capture('generic-editor');await d.click(s.controls.save);
 s=await d.check('generic-save',s=>s.screen==='GroupManagerScreen'&&s.groups.some(g=>g.name==='Bridge Chemical'));
 const id=s.groups.find(g=>g.name==='Bridge Chemical').id;d.report.groupId=id;
 const disk=await readJson(groupFile(d,id));assert.deepEqual(disk.filter,{any:[{type:'mekanism:chemical',id:'mekanism:oxygen'},{type:'mekanism:chemical',id:'mekanism:hydrogen'}]});
 d.report.genericFilter=disk.filter;
 await d.text(s.search,'Bridge Chemical');await d.check('generic-manager-counts',s=>s.cards.some(c=>c.id===id&&c.generic===2&&c.items===0&&c.fluids===0));
 await d.inventory();s=await d.findHeader(id);let h=s.overlayCells.find(c=>c.role==='header'&&c.groupId===id);if(!h.expanded)await d.click(h);
 await d.check('generic-overlay-members',s=>same(s.overlayCells.filter(c=>c.role==='child'&&c.groupId===id).map(c=>c.id),chemistry));
 await d.capture('generic-overlay');return id;
}
export async function genericMissing(d,id) {
 await ensureWorld(d);await d.inventory();let s=await d.editor(id);
 await d.check('missing-provider-keeps-empty-preview',s=>!s.hasGenericIngredients&&s.previewGeneric.length===0);
 await d.click(s.controls.RULES);
 s=await d.check('missing-provider-keeps-rules',s=>s.ruleRows.some(r=>r.value==='mekanism:oxygen')&&s.ruleRows.some(r=>r.value==='mekanism:hydrogen'));
 await d.text(s.nameField,'Bridge Chemical Retained');await d.click(s.controls.save);
 await d.check('missing-provider-save',s=>s.screen==='GroupManagerScreen');
 assert.deepEqual((await readJson(groupFile(d,id))).filter,d.report.genericFilter,'Missing provider lost selectors');
 await d.capture('missing-provider-manager');
}
export async function genericRecovered(d,id) {
 await ensureWorld(d);await d.inventory();await d.editor(id);
 await d.check('restored-provider-resolves-same-chemicals',s=>s.hasGenericIngredients&&same(s.previewGeneric,chemistry));
 assert.deepEqual((await readJson(groupFile(d,id))).filter,d.report.genericFilter);
 await d.capture('restored-provider-editor');
}
export async function changeMekanism(m,present,out) {
 await owned(m);assert.equal(await occupied(m.port),false,'Dependency change requires a stopped client');
 const file=profilePath(m.profile),current=await readJson(file),dep=m.mods.mekanism;
 assert(dep,'Original provider metadata missing');
 const enabled=path.join(m.gameDir,'mods',dep.file),disabled=enabled+'.cgbridge-disabled';
 const source=present?disabled:enabled,target=present?enabled:disabled;
 if(Boolean(current.mods.mekanism)===present)return;
 assert.equal(hash(await fs.readFile(source)),dep.sha256);
 await fs.writeFile(path.join(out,'provider-original-manifest.json'),JSON.stringify(m,null,2));
 await fs.rename(source,target);
 if(present)current.mods.mekanism=dep;else delete current.mods.mekanism;
 await fs.writeFile(file,JSON.stringify(current,null,2)+'\n');
}
export async function localeCore(d) {
 await ensureWorld(d);await d.inventory();let s=await d.manager();
 await d.text(s.search,'Bridge');
 s=await d.check('locale-language-and-fallback-metadata',s=>s.language==='zh_tw'&&s.cards.some(c=>c.id==='cg_bridge_locale'&&c.name==='Bridge Locale Fallback')&&s.cards.some(c=>c.id==='cg_bridge_fallback'&&c.name==='Bridge 保留名稱'));
 await d.capture('locale-manager');
 await d.click(s.cards.find(c=>c.id==='cg_bridge_fallback').edit);
 s=await d.check('locale-editor-name-and-members',s=>s.screen==='GroupEditorScreen'&&s.nameField.value==='Bridge 保留名稱'&&same(s.previewItems,['item:minecraft:dirt']));
 await d.text(s.nameField,'Bridge 中文編輯');await d.click(s.controls.save);
 await d.check('locale-unicode-save',s=>s.screen==='GroupManagerScreen'&&s.groups.some(g=>g.id==='cg_bridge_fallback'&&g.name==='Bridge 中文編輯'));
 assert.equal((await readJson(groupFile(d,'cg_bridge_fallback'))).name.fallback,'Bridge 中文編輯');
 await d.capture('locale-saved');return 'cg_bridge_fallback';
}
export async function localePersistence(d,id) {
 await ensureWorld(d);await d.inventory();await d.editor(id);
 await d.check('locale-cold-restart-unicode',s=>s.language==='zh_tw'&&s.nameField.value==='Bridge 中文編輯'&&same(s.previewItems,['item:minecraft:dirt']));
 await d.capture('locale-restart-editor');
}

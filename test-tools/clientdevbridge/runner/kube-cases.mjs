import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {root} from './session.mjs';
import {ensureWorld} from './cases.mjs';
export const kubeIds={
 alpha:'__kjs_cg:6272696467653a616c706861:6272696467653a706c616e6b73',
 beta:'__kjs_cg:6272696467653a62657461:6272696467653a626c6f636b73',
 legacy:'__kjs_bridge_legacy'
};
const expected1=Object.values(kubeIds).sort(),expected2=[kubeIds.beta,kubeIds.legacy].sort();
const ids=s=>s.groups.filter(g=>g.id.startsWith('__kjs_')).map(g=>g.id).sort();
const same=(a,b)=>JSON.stringify([...a].sort())===JSON.stringify([...b].sort());
const accepted=s=>s.publication.applied&&s.publication.attempts.some(a=>a.owner==='neoforge:kubejs7'&&a.state==='ACCEPTED');
const scriptFile=game=>path.join(game,'kubejs/client_scripts/cg_bridge_groups.js');
async function stage(game,fixture) {
 await fs.mkdir(path.dirname(scriptFile(game)),{recursive:true});
 await fs.copyFile(path.join(root,'fixtures/kubejs',fixture+'.js'),scriptFile(game));
}
export async function prepareKube(manifest,out) {
 const file=scriptFile(manifest.gameDir);
 const original=await fs.readFile(file).catch(e=>{if(e.code==='ENOENT')return null;throw e;});
 if(original)await fs.writeFile(path.join(out,'kube-script-before.js'),original);
 await stage(manifest.gameDir,'v1');
 return {async restore(){
  if(original===null)await fs.unlink(file);else await fs.writeFile(file,original);
  const restored=await fs.readFile(file).catch(e=>{if(e.code==='ENOENT')return null;throw e;});
  assert(original===null?restored===null:restored?.equals(original),'KubeJS script restoration mismatch');
  return {status:'passed',original:original!==null};
 }};
}
async function inventory(d) {let s=await d.inventory();await d.text(s.viewerSearch,'');return d.state();}
async function members(d,id,expected,name) {
 let s=await d.findHeader(id);let h=s.overlayCells.find(c=>c.groupId===id&&c.role==='header');
 if(!h.expanded)await d.click(h);
 return d.check(name,s=>same(s.overlayCells.filter(c=>c.role==='child'&&c.groupId===id).map(c=>c.id),expected));
}
async function reload(d,fixture,wanted,{failed=false}={}) {
 d.phase='kube-reload-'+fixture;
 const before=await d.state(),game=d.session.manifest.gameDir;
 const logFile=path.join(game,'logs/latest.log'),offset=(await fs.readFile(logFile)).length;
 await stage(game,fixture);
 let closing=before;
 for(let i=0;i<3&&closing.screen!=='none';i++) {
  await d.key(256);closing=await d.state();
 }
 assert.equal(closing.screen,'none','Escape did not close the viewer');
 await d.input({action:'chord',keys:[292,84]});
 await d.state(s=>s.screen==='none',60000,false);
 await inventory(d);
 const after=await d.check('kube-'+fixture+'-publication',s=>
  s.publication.activity>before.publication.activity&&same(ids(s),wanted)&&
  (failed?!s.publication.applied&&s.publication.attempts.some(a=>a.state==='PENDING'):accepted(s)),60000);
 const log=(await fs.readFile(logFile)).subarray(offset).toString('utf8');
 assert(log.includes('Reloading ResourceManager'),'No resource reload log');
 assert(log.includes('fixture-'+fixture+' loaded'),'New script marker absent');
 assert(log.includes('publication-'+fixture),'Listener was not posted');
 if(failed)assert(log.includes('cg-bridge-intentional-publication-error'));
 const history=await d.b.call('cgtest.history',{afterFrame:before.frame});
 assert(history.oldestFrame<=before.frame+1,'Publication history was truncated');
 assert(history.frames.length>=3,'Missing publication frame witnesses');
 const allowed=[ids(before),[],wanted];
 for(const frame of history.frames)assert(allowed.some(set=>same(frame.ids,set)),'Partial script publication was visible: '+JSON.stringify(frame));
 await fs.writeFile(path.join(d.folder,'kube-'+fixture+'-history.json'),JSON.stringify(history,null,2));
 await fs.writeFile(path.join(d.folder,'kube-'+fixture+'.log'),log);
 await inventory(d);return after;
}
export async function kubeCore(d) {
 await ensureWorld(d);await inventory(d);
 await d.check('kube-initial-sources',s=>same(ids(s),expected1)&&accepted(s));
 await members(d,kubeIds.alpha,['item:minecraft:oak_planks','item:minecraft:birch_planks'],'kube-alpha-members');
 await members(d,kubeIds.beta,['item:minecraft:stone','item:minecraft:cobblestone'],'kube-beta-original-members');
 await d.manager();let s=await d.state();await d.text(s.search,'Bridge Script');
 await d.check('kube-manager-source-cards',s=>s.filteredCount===3&&s.cards.every(c=>c.id.startsWith('__kjs_')));
 await d.capture('kube-initial-manager');await d.inventory();
 await reload(d,'reload',expected2);
 await d.check('kube-removed-source-has-no-stale-header',s=>!s.groups.some(g=>g.id===kubeIds.alpha)&&!s.overlayCells.some(c=>c.groupId===kubeIds.alpha));
 await members(d,kubeIds.beta,['item:minecraft:dirt','item:minecraft:cobblestone'],'kube-beta-replaced-members');
 await members(d,kubeIds.legacy,['item:minecraft:coal','item:minecraft:redstone'],'kube-unaffected-legacy-source');
 await d.capture('kube-reloaded');
 await reload(d,'tags',['__kjs_bridge_tags','__kjs_fluid_bridge_water']);
 let tags=await d.findHeader('__kjs_bridge_tags'),header=tags.overlayCells.find(c=>c.groupId==='__kjs_bridge_tags'&&c.role==='header');
 if(!header.expanded)await d.click(header);
 await d.check('kube-native-item-tag-members',s=>{
  const actual=s.overlayCells.filter(c=>c.role==='child'&&c.groupId==='__kjs_bridge_tags').map(c=>c.id);
  return actual.length>=10&&actual.every(id=>id.endsWith('_planks'))&&['oak','birch','crimson','warped'].every(wood=>actual.includes('item:minecraft:'+wood+'_planks'));
 });
 await d.manager();s=await d.state();await d.text(s.search,'Bridge Script Tagged Water');
 await d.check('kube-native-fluid-tag-count',s=>s.filteredCount===1&&s.cards.some(c=>c.id==='__kjs_fluid_bridge_water'&&c.fluids===1));
 await d.inventory();
 s=await d.state();await d.text(s.viewerSearch,'water');
 await d.check('kube-single-fluid-tag-remains-visible',s=>s.overlayCells.some(c=>c.id==='fluid:minecraft:water'&&c.role==='ingredient')&&!s.overlayCells.some(c=>c.role==='header'&&c.groupId==='__kjs_fluid_bridge_water'));
 await d.capture('kube-native-tags');
 await reload(d,'namespace',['__kjs_bridge_namespace','__kjs_fluid_bridge_namespace']);
 await d.manager();s=await d.state();await d.text(s.search,'Bridge Script Vanilla');
 await d.check('kube-native-namespace-counts',s=>s.filteredCount===2&&s.cards.some(c=>c.id==='__kjs_bridge_namespace'&&c.items>1000)&&s.cards.some(c=>c.id==='__kjs_fluid_bridge_namespace'&&c.fluids===3));
 await d.inventory();
 await members(d,'__kjs_fluid_bridge_namespace',['fluid:minecraft:water','fluid:minecraft:lava','fluid:minecraft:milk'],'kube-native-fluid-namespace-members');
 await reload(d,'failure',[],{failed:true});
 await d.check('kube-failure-has-no-partial-header',s=>!s.overlayCells.some(c=>c.groupId?.startsWith('__kjs_')));
 await reload(d,'v1',expected1);
 await members(d,kubeIds.beta,['item:minecraft:stone','item:minecraft:cobblestone'],'kube-recovery-members');
 await d.b.call('world.leave',{},60000);await d.b.call('world.load',{name:'CG Bridge Smoke'},90000);await inventory(d);
 await d.check('kube-world-reentry-no-duplicates',s=>same(ids(s),expected1)&&accepted(s));
 await d.capture('kube-reentry');return 'kube';
}
export async function kubePersistence(d) {
 await ensureWorld(d);await inventory(d);
 await d.check('kube-cold-restart-no-duplicates',s=>same(ids(s),expected1)&&accepted(s));
 await members(d,kubeIds.beta,['item:minecraft:stone','item:minecraft:cobblestone'],'kube-cold-restart-members');
}

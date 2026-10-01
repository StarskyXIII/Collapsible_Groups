import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {root,readJson,profilePath,hash,occupied} from './session.mjs';
import {definitions} from './profiles.mjs';
const selected=new Map();
const folders=(await fs.readdir(path.join(root,'evidence'))).filter(x=>x.startsWith('suite-')).sort();
for(const folder of folders) {
 const suite=await readJson(path.join(root,'evidence',folder,'result.json')).catch(e=>{if(e.code==='ENOENT')return null;throw e;});
 for(const report of suite?.profiles??[])if(!report.negativeControl)selected.set(report.profile,{folder,report});
}
const profiles=[];
const requested=process.argv.slice(2);if(!requested.length)requested.push('neo-jei','fabric-jei');
for(const id of requested) {
 assert(Object.hasOwn(definitions,id),'Unknown profile '+id);
 const entry=selected.get(id);assert(entry,'No evidence for '+id);
 const {folder,report}=entry,m=await readJson(profilePath(id));
 assert.equal(report.status,'passed',id+' latest run failed; older successes cannot hide it');
 assert.equal(await occupied(m.port),false,id+' is still running');
 assert.deepEqual(report.manifest.mods,m.mods,id+' evidence uses different jars');
 for(const mod of Object.values(m.mods))assert.equal(hash(await fs.readFile(path.join(m.gameDir,'mods',mod.file))),mod.sha256,id+' deployed jar changed');
 assert.equal(report.restoration?.status,'passed',id+' config restoration');
 for(const exit of [report.firstQuit,report.quit,...(report.missingQuit?[report.missingQuit]:[])])assert(exit?.normalQuit&&exit.processExited,id+' shutdown');
 assert.equal(report.cases.length,({kube:22,generic:11,locale:4})[m.scenario]??46,id+' case count');
 assert(report.cases.every(c=>c.status==='passed'));
 for(const c of report.cases) {
  assert.equal(new Set(c.samples.map(s=>s.frame)).size,3,id+' frame witnesses');
  assert.equal(new Set(c.samples.map(s=>s.tick)).size,3,id+' tick witnesses');
 }
 assert.equal(report.visuals?.length??0,({kube:0,generic:0,locale:1})[m.scenario]??3,id+' visual count');
 assert((report.visuals??[]).every(v=>v.passed));
 if(m.scenario==='kube')assert.equal(report.scriptRestoration?.status,'passed');
 profiles.push({profile:id,cases:report.cases.length,visuals:report.visuals?.length??0,evidence:'evidence/'+folder+'/'+id,product:m.mods.collapsible_groups.sha256,probe:m.mods.cgbridgeprobe.sha256});
}
const summary={status:'passed',at:new Date().toISOString(),cases:profiles.reduce((n,p)=>n+p.cases,0),visuals:profiles.reduce((n,p)=>n+p.visuals,0),profiles};
const file=path.join(root,'evidence/final-verification.json');await fs.writeFile(file,JSON.stringify(summary,null,2)+'\n');console.log(JSON.stringify(summary));

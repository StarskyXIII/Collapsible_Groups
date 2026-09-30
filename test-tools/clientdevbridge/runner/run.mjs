import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {root,readJson,profilePath,connect,launch,occupied,owned} from './session.mjs';
import {quit} from './lifecycle.mjs';
import {Driver} from './driver.mjs';
import {core,persistence} from './cases.mjs';
import {backupConfig} from './configs.mjs';
import {prepareBeta} from './beta-cases.mjs';
import {prepareKube,kubeCore,kubePersistence} from './kube-cases.mjs';
import {prepareExtended,genericCore,genericMissing,genericRecovered,changeMekanism,localeCore,localePersistence} from './extended-cases.mjs';
const args=process.argv.slice(2),attach=args.includes('--attach'),profiles=args.filter(x=>!x.startsWith('--'));
assert(args.filter(x=>x.startsWith('--')).every(x=>['--attach','--negative-control'].includes(x)),'Unknown option');
assert(profiles.length&&new Set(profiles).size===profiles.length);
for(const id of profiles)profilePath(id);
const lockPath=path.join(root,'suite.lock'),lock=await fs.open(lockPath,'wx');
const folder=path.join(root,'evidence','suite-'+new Date().toISOString().replaceAll(/[:.]/g,'-'));
await fs.mkdir(folder,{recursive:true});
const suite={status:'running',started:new Date().toISOString(),profiles:[]};
let exitCode=0;
try {
 for(const id of profiles) {
  const manifest=await readJson(profilePath(id));
  await owned(manifest);
  if(!attach)assert.equal(await occupied(manifest.port),false,'Client already running; no fixture files were changed');
  const out=path.join(folder,id);await fs.mkdir(out);
  const report={profile:id,status:'running',negativeControl:args.includes('--negative-control'),cases:[],manifest};suite.profiles.push(report);
  const configs=await backupConfig(manifest.gameDir,out);
  let s,d,scriptBackup,closed=false,starting=false;
  try {
   if(manifest.scenario==='kube')scriptBackup=await prepareKube(manifest,out);
   await prepareExtended(manifest);
   if(manifest.scenario==='core')await prepareBeta(manifest);
   starting=true;s=attach?await connect(id):await launch(id);starting=false;report.identity=s.identity;
   d=new Driver(s,out,report);const group=await (manifest.scenario==='kube'?kubeCore(d):manifest.scenario==='generic'?genericCore(d):manifest.scenario==='locale'?localeCore(d):core(d));
   report.firstQuit=await quit(s);closed=true;s.bridge.close();
   await fs.copyFile(path.join(manifest.gameDir,'logs/latest.log'),path.join(out,'first-run.log'));
   if(manifest.scenario==='generic')await changeMekanism(manifest,false,out);
   starting=true;s=await launch(id);starting=false;closed=false;report.restartIdentity=s.identity;
   d=new Driver(s,out,report);
   if(manifest.scenario==='kube')await kubePersistence(d);
   else if(manifest.scenario==='locale')await localePersistence(d,group);
   else if(manifest.scenario==='generic') {
    await genericMissing(d,group);report.missingQuit=await quit(s);closed=true;s.bridge.close();
    await fs.copyFile(path.join(manifest.gameDir,'logs/latest.log'),path.join(out,'missing-provider.log'));
    await changeMekanism(manifest,true,out);
    starting=true;s=await launch(id);starting=false;closed=false;report.recoveryIdentity=s.identity;
    d=new Driver(s,out,report);await genericRecovered(d,group);
   } else await persistence(d,group);
   report.status='passed';
  } catch(error) {
   report.status='failed';report.phase=d?.phase;report.error=String(error);report.stack=error.stack;exitCode=1;
   try {await d?.capture('failure');}catch(captureError){report.captureError=String(captureError);}
  } finally {
   if(s&&!closed)try {report.quit=await quit(s);closed=true;}catch(error){report.cleanupError=String(error);report.status='failed';exitCode=1;}
   s?.bridge.close();
   if(!starting&&closed&&!await occupied(manifest.port))try {if(manifest.scenario==='generic')await changeMekanism(manifest,true,out);report.restoration=await configs.restore();if(scriptBackup)report.scriptRestoration=await scriptBackup.restore();}catch(error){report.restoreError=String(error);report.status='failed';exitCode=1;}
   else {report.restoration={status:'blocked',reason:'Client exit has not been verified; original files remain in config-before'};report.status='failed';exitCode=1;}
   await fs.copyFile(path.join(manifest.gameDir,'logs/latest.log'),path.join(out,'latest.log')).catch(()=>{});
   await fs.writeFile(path.join(out,'result.json'),JSON.stringify(report,null,2));
  }
  if(report.status!=='passed')break;
 }
 suite.status=exitCode?'failed':'passed';
} catch(error) {
 suite.status='failed';suite.error=String(error);exitCode=1;
} finally {
 suite.finished=new Date().toISOString();await fs.writeFile(path.join(folder,'result.json'),JSON.stringify(suite,null,2));
 await lock.close();await fs.unlink(lockPath);process.exitCode=exitCode;
}
console.log(JSON.stringify({status:suite.status,folder,profiles:suite.profiles.map(r=>({profile:r.profile,status:r.status,cases:r.cases.length,phase:r.phase,error:r.error}))}));

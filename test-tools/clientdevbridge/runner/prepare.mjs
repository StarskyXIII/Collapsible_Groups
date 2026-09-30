import fs from 'node:fs/promises';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';
import {root, readJson, hash, occupied} from './session.mjs';
import {definitions} from './profiles.mjs';
import {productFor} from './product.mjs';
const local=await readJson(path.join(root,'local.json')), deps=await readJson(path.join(root,'dependencies.json'));
try {await fs.access(path.join(root,'suite.lock'));throw Error('A suite owns the profiles');}catch(error){if(error.code!=='ENOENT')throw error;}
await fs.mkdir(path.join(root,'profiles'),{recursive:true});
const requested=process.argv.slice(2);if(!requested.length)requested.push('neo-jei','fabric-jei');
for(const id of requested) {
  assert(Object.hasOwn(definitions,id),'Unknown profile');
  const def=definitions[id],platform=def.loader;
  const pack=platform==='fabric'?{formatVersion:1,components:[{uid:'net.minecraft',version:deps.minecraft,important:true},{uid:'net.fabricmc.fabric-loader',version:deps.fabricLoader}]}:await readJson(local.packTemplate);
  assert.equal(pack.components.find(c=>c.uid==='net.minecraft')?.version,deps.minecraft);
  if(platform==='neoforge')assert.equal(pack.components.find(c=>c.uid==='net.neoforged')?.version,deps.neoforge);
  const lib=path.join(root,platform==='fabric'?'companion-fabric':'companion','libs');
  const product=productFor(platform);
  const probe={file:'cg-bridge-probe-26.1.2-'+platform+'-0.1.0.jar',version:'0.1.0'};
  const instance='CG Bridge 26 '+id+' 20260930',port=def.port,session=randomUUID();
  const instances=await fs.realpath(path.join(local.prismData,'instances'));
  const target=path.resolve(instances,instance);
  assert.equal(path.dirname(target),instances);
  const profileFile=path.join(root,'profiles',id+'.json');
  try { await fs.access(target); throw Error('Instance already exists: '+target); } catch(error) { if(error.code!=='ENOENT')throw error; }
  assert.equal(await occupied(port),false);
  const staging=path.resolve(local.prismData,'cgbridge-staging-'+randomUUID());
  assert.equal(path.dirname(staging),path.resolve(local.prismData));
  const gameDir=path.join(staging,'minecraft');await fs.mkdir(path.join(gameDir,'mods'),{recursive:true});
  const mods={collapsible_groups:product,cgbridgeprobe:probe};
  for(const key of def.mods){const dep=deps.mods[key]??deps.extras[key];assert(dep,'Missing locked dependency '+key);mods[dep.modId??key]=dep;}
  const manifest={profile:id,instance,port,session,loader:platform,viewer:def.viewer,scenario:def.scenario??'core',language:def.language??'en_us',loaderVersion:platform==='fabric'?deps.fabricLoader:deps.neoforge,gameDir:path.join(target,'minecraft'),mods:{}};
  for(const [modId,mod] of Object.entries(mods)) {
    const source=modId==='cgbridgeprobe'?path.join(root,platform==='fabric'?'companion-fabric':'companion','build','libs',mod.file):modId==='collapsible_groups'?path.resolve(root,'../../'+platform+'/build/libs',mod.file):path.join(lib,mod.file);
    const bytes=await fs.readFile(source),sha256=hash(bytes);
    if(mod.sha256)assert.equal(sha256,mod.sha256,modId+' locked dependency');
    await fs.writeFile(path.join(gameDir,'mods',mod.file),bytes);
    manifest.mods[modId]={...mod,sha256,source};
    for(const [provided,version] of Object.entries(mod.provides??{}))manifest.mods[provided]={file:mod.file,version,sha256,source};
  }
  const args=['-Dfile.encoding=UTF-8','-Djdk.net.unixdomain.tmpdir=D:/cg-bridge-no-socket','-Dclientdevbridge.enabled=true','-Dclientdevbridge.port='+port,'-Dcgbridgeprobe.enabled=true','-Dcgbridgeprobe.session='+session].join(' ');
  const cfg=['[General]','ConfigVersion=1.3','InstanceType=OneSix','name='+instance,'iconKey=default','OverrideJavaLocation=true','JavaPath='+local.java,'AutomaticJava=false','OverrideJavaArgs=true','JvmArgs="'+args+'"','OverrideMemory=true','MinMemAlloc=1024','MaxMemAlloc=4096','OverrideWindow=true','MinecraftWinWidth=1280','MinecraftWinHeight=800','LaunchMaximized=false','OverrideCommands=true','PreLaunchCommand=','PostExitCommand=','WrapperCommand=','ShowConsole=false','ShowConsoleOnError=true','UseAccountForInstance=false'].join('\n')+'\n';
  await fs.writeFile(path.join(staging,'instance.cfg'),cfg);await fs.writeFile(path.join(staging,'mmc-pack.json'),JSON.stringify(pack,null,2));
  await fs.writeFile(path.join(staging,'.cgbridge-owned.json'),JSON.stringify({profile:id,session}));
  await fs.writeFile(path.join(gameDir,'options.txt'),['lang:'+manifest.language,'guiScale:2','pauseOnLostFocus:false','maxFps:60','renderDistance:5','simulationDistance:5','enableVsync:false','onboardAccessibility:false','soundCategory_master:0.0','tutorialStep:none'].join('\n')+'\n');
  await fs.rename(staging,target);await fs.writeFile(profileFile,JSON.stringify(manifest,null,2)+'\n');
  console.log(JSON.stringify({profile:id,instance,port,mods:manifest.mods}));
}

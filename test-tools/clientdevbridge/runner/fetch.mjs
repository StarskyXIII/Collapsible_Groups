import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {root,readJson,hash} from './session.mjs';
import {definitions} from './profiles.mjs';
const deps=await readJson(path.join(root,'dependencies.json'));
const profiles=process.argv.slice(2);if(!profiles.length)profiles.push('neo-jei','fabric-jei');
for(const id of profiles)assert(Object.hasOwn(definitions,id),'Unknown profile '+id);
for(const key of new Set(profiles.flatMap(id=>definitions[id].mods))) {
 const dep=deps.mods[key]??deps.extras[key];assert(dep,'Missing dependency '+key);
 assert.equal(path.basename(dep.file),dep.file);assert.equal(new URL(dep.url).protocol,'https:');
 const dir=path.join(root,dep.loader==='fabric'?'companion-fabric':'companion','libs');
 await fs.mkdir(dir,{recursive:true});const target=path.join(dir,dep.file);
 let bytes;try {bytes=await fs.readFile(target);}catch(error){if(error.code!=='ENOENT')throw error;}
 const cached=Boolean(bytes);
 if(!bytes) {
  const response=await fetch(dep.url,{signal:AbortSignal.timeout(60000)});
  assert(response.ok,`${key}: HTTP ${response.status}`);bytes=Buffer.from(await response.arrayBuffer());
 }
 assert.equal(hash(bytes),dep.sha256,key+' dependency SHA mismatch');
 if(!cached)await fs.writeFile(target,bytes,{flag:'wx'});
 console.log(JSON.stringify({dependency:key,sha256:dep.sha256,cached}));
}

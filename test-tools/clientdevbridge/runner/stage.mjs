import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {root,readJson,hash} from './session.mjs';
import {definitions} from './profiles.mjs';
import {productFor} from './product.mjs';

const local=await readJson(path.join(root,'local.json'));
const deps=await readJson(path.join(root,'dependencies.json'));
const profiles=process.argv.slice(2);if(!profiles.length)profiles.push('neo-jei','fabric-jei');
for(const id of profiles)assert(Object.hasOwn(definitions,id),'Unknown profile '+id);
const required=new Set(profiles.flatMap(id=>definitions[id].mods));
for(const id of required) {
 const dep=deps.mods[id]??deps.extras[id];assert(dep,'Missing dependency '+id);
 const libs=path.join(root,dep.loader==='fabric'?'companion-fabric':'companion','libs');await fs.mkdir(libs,{recursive:true});
 const target=path.join(libs,dep.file);
 const source=local.dependencyFiles?.[id]??target;
 const bytes=await fs.readFile(source);
 assert.equal(hash(bytes),dep.sha256,id+' dependency SHA mismatch');
 if(path.resolve(source)!==path.resolve(target))await fs.writeFile(target,bytes);
}
for(const loader of new Set(profiles.map(id=>definitions[id].loader))) {
 const product=productFor(loader).file;
 const bytes=await fs.readFile(path.resolve(root,'../../'+loader+'/build/libs',product));
 const libs=path.join(root,loader==='fabric'?'companion-fabric':'companion','libs');await fs.mkdir(libs,{recursive:true});
 await fs.writeFile(path.join(libs,product),bytes);
 console.log(JSON.stringify({product,sha256:hash(bytes),dependencies:'verified'}));
}

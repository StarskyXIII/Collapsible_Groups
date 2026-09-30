import fs from 'node:fs/promises';
import path from 'node:path';
import net from 'node:net';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import assert from 'node:assert/strict';
import {Bridge} from './bridge.mjs';
import {definitions} from './profiles.mjs';
export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
export const hash = bytes => createHash('sha256').update(bytes).digest('hex');
export const readJson = async file => JSON.parse(await fs.readFile(file, 'utf8'));
export function profilePath(id) { assert(Object.hasOwn(definitions,id),'Unknown profile '+id); return path.join(root,'profiles',id+'.json'); }
export async function owned(manifest) {
  const marker=await readJson(path.join(manifest.gameDir,'..','.cgbridge-owned.json'));
  assert.equal(marker.session,manifest.session,'Unowned test instance');
  assert.equal(marker.profile,manifest.profile,'Unowned test profile');
}
export function verifyRuntime(identity,manifest) {
  assert.equal(identity.protocol,1);
  assert.equal(identity.probeVersion,manifest.mods.cgbridgeprobe.version);
  assert(Number.isSafeInteger(identity.pid)&&identity.pid>0,'Invalid client PID');
  const loader=manifest.loader??'neoforge';
  assert.equal(identity.loader,loader);assert.equal(identity.mcVersion,'26.1.2');
  assert.equal(identity.mods.minecraft.version,'26.1.2');
  assert.equal(identity.mods[loader==='fabric'?'fabricloader':'neoforge'].version,manifest.loaderVersion);
}
export async function occupied(port) {
  return new Promise(resolve => {
    const socket=net.connect({host:'127.0.0.1',port});socket.setTimeout(500);
    socket.once('connect',()=>{socket.destroy();resolve(true);});
    socket.once('error',()=>resolve(false));
    socket.once('timeout',()=>{socket.destroy();resolve(false);});
  });
}
export async function connect(id) {
  const manifest=await readJson(profilePath(id)), bridge=await Bridge.connect(manifest.port);
  try {
    await owned(manifest);
    const identity=await bridge.call('cgtest.identity');
    assert.equal(identity.session,manifest.session,'Session mismatch');
    verifyRuntime(identity,manifest);
    assert.equal((await fs.realpath(identity.gameDir)).toLowerCase(),(await fs.realpath(manifest.gameDir)).toLowerCase(),'gameDir mismatch');
    const jarFiles=(await fs.readdir(path.join(manifest.gameDir,'mods'))).filter(x=>x.endsWith('.jar')).sort();
    assert.deepEqual(jarFiles,[...new Set(Object.values(manifest.mods).map(x=>x.file))].sort(),'Unexpected mod set');
    for(const [modId,mod] of Object.entries(manifest.mods)) {
      const actual=identity.mods[modId];assert(actual,'Missing '+modId);assert.equal(actual.version,mod.version,modId);
      const expected=await fs.realpath(path.join(manifest.gameDir,'mods',mod.file));
      assert.equal((await fs.realpath(actual.path)).toLowerCase(),expected.toLowerCase(),modId+' source');
      assert.equal(hash(await fs.readFile(expected)),mod.sha256,modId+' SHA');
    }
    assert.equal(Boolean(identity.mods.emi),Boolean(manifest.mods.emi));assert.equal(Boolean(identity.mods.jei),Boolean(manifest.mods.jei));
    return {bridge,manifest,identity};
  } catch(error) {bridge.close();throw error;}
}
export async function launch(id) {
  const manifest=await readJson(profilePath(id)),local=await readJson(path.join(root,'local.json'));
  await owned(manifest);
  assert.equal(await occupied(manifest.port),false,'Profile port already occupied; refusing another launch');
  const child=spawn(local.prism,['--launch',manifest.instance],{windowsHide:true,detached:true,stdio:'ignore'});
  await new Promise((resolve,reject)=>{child.once('spawn',resolve);child.once('error',reject);});child.unref();
  const deadline=Date.now()+150000;
  while(Date.now()<deadline) {
    if(await occupied(manifest.port)) return connect(id);
    await sleep(1200);
  }
  throw Error('Bridge port not ready; see '+path.join(manifest.gameDir,'logs','latest.log'));
}

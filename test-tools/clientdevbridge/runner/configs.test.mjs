import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {backupConfig,filesUnder} from './configs.mjs';
async function removeTemp(dir) {
 assert.equal(path.dirname(path.resolve(dir)),path.resolve(os.tmpdir()));
 assert(path.basename(dir).startsWith('cgbridge-'));
 await fs.rm(dir,{recursive:true,force:true});
}

test('restoration preserves original bytes and removes only added CG files',async()=>{
 const temp=await fs.mkdtemp(path.join(os.tmpdir(),'cgbridge-restore-'));
 const game=path.join(temp,'minecraft'),config=path.join(game,'config','collapsiblegroups');
 try {
  await fs.mkdir(path.join(config,'groups'),{recursive:true});
  const original=Buffer.from([0xef,0xbb,0xbf,0x61,0x0d,0x0a,0xff]);
  await fs.writeFile(path.join(config,'groups','existing.json'),original);
  await fs.writeFile(path.join(game,'config','viewer.json'),'viewer config');
  const backup=await backupConfig(game,path.join(temp,'evidence'));
  await fs.writeFile(path.join(config,'groups','existing.json'),'modified');
  await fs.writeFile(path.join(config,'groups','new.json'),'added');
  assert.deepEqual(await backup.restore(),{status:'passed',files:1});
  assert((await fs.readFile(path.join(config,'groups','existing.json'))).equals(original));
  assert.equal(await fs.readFile(path.join(game,'config','viewer.json'),'utf8'),'viewer config');
  await assert.rejects(fs.access(path.join(config,'groups','new.json')),{code:'ENOENT'});
 } finally {await removeTemp(temp);}
});

test('restoration returns initially absent config to no files',async()=>{
 const temp=await fs.mkdtemp(path.join(os.tmpdir(),'cgbridge-empty-'));
 const game=path.join(temp,'minecraft'),config=path.join(game,'config','collapsiblegroups');
 try {
  const backup=await backupConfig(game,path.join(temp,'evidence'));
  await fs.mkdir(config,{recursive:true});await fs.writeFile(path.join(config,'defaults.json'),'created');
  assert.deepEqual(await backup.restore(),{status:'passed',files:0});
  assert.equal((await filesUnder(config)).size,0);
 } finally {await removeTemp(temp);}
});

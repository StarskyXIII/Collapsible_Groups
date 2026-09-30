import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {root,readJson,profilePath,hash,occupied} from './session.mjs';
try {await fs.access(path.join(root,'suite.lock'));throw Error('A suite owns the profiles');}catch(error){if(error.code!=='ENOENT')throw error;}
for(const id of process.argv.slice(2)) {
 const file=profilePath(id),m=await readJson(file);
 assert.equal(await occupied(m.port),false,'Client still running');
 const marker=await readJson(path.join(m.gameDir,'..','.cgbridge-owned.json'));
 assert.equal(marker.session,m.session);assert.equal(marker.profile,id);
 for(const modId of ['collapsible_groups','cgbridgeprobe']) {
  const mod=m.mods[modId];
  if(modId==='collapsible_groups')mod.source=path.resolve(root,'../../'+(m.loader??'neoforge')+'/build/libs',mod.file);
  const bytes=await fs.readFile(mod.source);
  await fs.writeFile(path.join(m.gameDir,'mods',mod.file),bytes);
  mod.sha256=hash(bytes);
 }
 await fs.writeFile(file,JSON.stringify(m,null,2)+'\n');
 console.log(JSON.stringify({profile:id,product:m.mods.collapsible_groups.sha256,probe:m.mods.cgbridgeprobe.sha256}));
}

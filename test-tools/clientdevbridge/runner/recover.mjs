import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {readJson,owned,occupied,root} from './session.mjs';
import {filesUnder,restoreConfig} from './configs.mjs';
const [folder]=process.argv.slice(2);assert(folder);
try{await fs.access(path.join(root,'suite.lock'));throw Error('Suite still running');}catch(e){if(e.code!=='ENOENT')throw e;}
const report=await readJson(path.join(folder,'result.json')),m=report.manifest;await owned(m);
assert.equal(await occupied(m.port),false);
assert(report.identity?.pid,'No verified identity; inspect process ownership manually');
for(const identity of [report.identity,report.restartIdentity,report.recoveryIdentity].filter(Boolean)) {
 let alive;try{process.kill(identity.pid,0);alive=true;}catch(e){if(e.code!=='ESRCH')throw e;alive=false;}
 assert.equal(alive,false,'Verified PID still exists; inspect before recovery');
}
assert(m.scenario!=='generic'&&m.scenario!=='kube','Use the saved script/provider manifest for this scenario');
const restoration=await restoreConfig(m.gameDir,await filesUnder(path.join(folder,'config-before')));
await fs.writeFile(path.join(folder,'manual-recovery.json'),JSON.stringify({at:new Date().toISOString(),restoration},null,2));
console.log(restoration);

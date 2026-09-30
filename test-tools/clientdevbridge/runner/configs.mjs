import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
export async function filesUnder(dir) {
 const out=new Map();
 async function visit(at) {
  for(const entry of await fs.readdir(at,{withFileTypes:true}).catch(error=>{if(error.code==='ENOENT')return [];throw error;})) {
   const file=path.join(at,entry.name);assert(!entry.isSymbolicLink(),'Symlink in test config');
   if(entry.isDirectory())await visit(file);else if(entry.isFile())out.set(path.relative(dir,file),await fs.readFile(file));
  }
 }
 await visit(dir);return out;
}
export async function backupConfig(gameDir,folder) {
 const dir=path.resolve(gameDir,'config','collapsiblegroups');
 assert(dir.startsWith(path.resolve(gameDir)+path.sep));
 const original=await filesUnder(dir);
 for(const [name,bytes] of original) {const to=path.join(folder,'config-before',name);await fs.mkdir(path.dirname(to),{recursive:true});await fs.writeFile(to,bytes);}
 return {restore:()=>restoreConfig(gameDir,original)};
}
export async function restoreConfig(gameDir,original) {
  const dir=path.resolve(gameDir,'config','collapsiblegroups');
  for(const name of original.keys())assert(path.resolve(dir,name).startsWith(dir+path.sep),'Backup path escaped config');
  const current=await filesUnder(dir);
  for(const name of current.keys())if(!original.has(name))await fs.unlink(path.join(dir,name));
  for(const [name,bytes] of original) {const to=path.join(dir,name);await fs.mkdir(path.dirname(to),{recursive:true});await fs.writeFile(to,bytes);}
  const after=await filesUnder(dir);assert.deepEqual([...after.keys()].sort(),[...original.keys()].sort());
  for(const [name,bytes] of original)assert(after.get(name).equals(bytes),name+' restoration');
  return {status:'passed',files:original.size};
 }

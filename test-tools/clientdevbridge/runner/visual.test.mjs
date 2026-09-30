import {test} from 'node:test';
import assert from 'node:assert/strict';
import {compare,decodePng} from './visual.mjs';
import fs from 'node:fs/promises';
import path from 'node:path';
import {root} from './session.mjs';
test('visual comparator detects a removed control and ignores pixels outside the reviewed region',()=>{
 const a={w:20,h:20,channels:3,pixels:Buffer.alloc(1200,90)};const b={...a,pixels:Buffer.from(a.pixels)};
 const region=[[0,0,10,10]];assert(compare(a,b,region).passed);
 b.pixels.fill(0,900,1200);assert(compare(a,b,region).passed);
 b.pixels.fill(0,0,30);assert.equal(compare(a,b,region).passed,false);
 assert.throws(()=>compare(a,{...b,w:21},region));
});
test('reviewed screenshots decode and compare without changes',async()=>{
 const manifest=JSON.parse(await fs.readFile(path.join(root,'baselines/manifest.json')));
 for(const entry of Object.values(manifest.captures)) {const a=decodePng(await fs.readFile(path.join(root,'baselines',entry.file)));assert(compare(a,a,entry.regions).passed);}
});

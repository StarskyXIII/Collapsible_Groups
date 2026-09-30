import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {inflateSync} from 'node:zlib';
import {root,readJson,hash} from './session.mjs';
export function decodePng(png) {
 assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
 let w,h,channels;const chunks=[];
 for(let at=8;at<png.length;) {
  const size=png.readUInt32BE(at),type=png.toString('ascii',at+4,at+8),data=png.subarray(at+8,at+8+size);at+=size+12;
  if(type==='IHDR') {w=data.readUInt32BE(0);h=data.readUInt32BE(4);assert.equal(data[8],8);assert([2,6].includes(data[9]));channels=data[9]===6?4:3;assert.equal(data[12],0);}
  if(type==='IDAT')chunks.push(data);
 }
 assert(w>0&&h>0&&w*h<20000000);const stride=w*channels,raw=inflateSync(Buffer.concat(chunks));assert.equal(raw.length,(stride+1)*h);
 const pixels=Buffer.alloc(stride*h);
 const paeth=(a,b,c)=>{const p=a+b-c,pa=Math.abs(p-a),pb=Math.abs(p-b),pc=Math.abs(p-c);return pa<=pb&&pa<=pc?a:pb<=pc?b:c;};
 for(let y=0;y<h;y++) {
  const filter=raw[y*(stride+1)];assert(filter<=4);
  for(let x=0;x<stride;x++) {
   const i=y*stride+x,a=x>=channels?pixels[i-channels]:0,b=y?pixels[i-stride]:0,c=y&&x>=channels?pixels[i-stride-channels]:0;
   const prediction=[0,a,b,Math.floor((a+b)/2),paeth(a,b,c)][filter];pixels[i]=(raw[y*(stride+1)+1+x]+prediction)&255;
  }
 }
 return {w,h,channels,pixels};
}
export function compare(a,b,regions,tolerance=24,maxChangedRatio=0.003) {
 assert.equal(a.w,b.w,'Screenshot width changed');assert.equal(a.h,b.h,'Screenshot height changed');
 const results=regions.map(([x,y,w,h])=>{
  assert(x>=0&&y>=0&&w>0&&h>0&&x+w<=a.w&&y+h<=a.h);let changed=0;
  for(let py=y;py<y+h;py++)for(let px=x;px<x+w;px++) {
   const ai=(py*a.w+px)*a.channels,bi=(py*b.w+px)*b.channels;
   if([0,1,2].some(c=>Math.abs(a.pixels[ai+c]-b.pixels[bi+c])>tolerance))changed++;
  }
  return {bounds:[x,y,w,h],changed,pixels:w*h,ratio:changed/(w*h)};
 });
 return {passed:results.every(r=>r.ratio<=maxChangedRatio),tolerance,maxChangedRatio,regions:results};
}
export async function checkVisual(d,name,file) {
 const config=await readJson(path.join(root,'baselines/manifest.json'));
 const def=config.captures[d.session.manifest.language==='zh_tw'?'zh:'+name:name];if(!def)return;
 const reference=await fs.readFile(path.join(root,'baselines',def.file));assert.equal(hash(reference),def.sha256,'Baseline modified without manifest update');
 const result=compare(decodePng(reference),decodePng(await fs.readFile(file)),def.regions,def.tolerance,def.maxChangedRatio);
 await fs.writeFile(path.join(d.folder,name+'-visual.json'),JSON.stringify(result,null,2));
 d.report.visuals??=[];d.report.visuals.push({name,...result});await d.save();
 assert(result.passed,'Visual regression: '+name+' (see '+name+'-visual.json)');
}

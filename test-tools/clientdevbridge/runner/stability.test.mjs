import test from 'node:test';
import assert from 'node:assert/strict';
import {StableFrames} from './stability.mjs';
import {usable,center} from './driver.mjs';
const sample=(frame,tick=frame,extra={})=>({frame,tick,screenId:'screen-1',generation:'index-1',...extra});
test('polling the same rendered frame cannot manufacture three witnesses',()=>{
 const s=new StableFrames();
 for(let n=0;n<20;n++)assert.equal(s.accept(sample(1),true),false);
 assert.equal(s.accept(sample(2),true),false);
 assert.equal(s.accept(sample(3),true),true);
});
test('a failing intermediate frame resets an otherwise successful sequence',()=>{
 const s=new StableFrames();
 s.accept(sample(1),true);s.accept(sample(2),true);
 assert.equal(s.accept(sample(3),false),false);
 assert.equal(s.accept(sample(4),true),false);assert.equal(s.accept(sample(5),true),false);
 assert.equal(s.accept(sample(6),true),true);
});
test('screen replacement and index publication cannot reuse previous witnesses',()=>{
 for(const extra of [{screenId:'screen-2'},{generation:'index-2'}]) {
  const s=new StableFrames();s.accept(sample(1),true);s.accept(sample(2),true);
  assert.equal(s.accept(sample(3,3,extra),true),false);
  assert.equal(s.accept(sample(4,4,extra),true),false);
  assert.equal(s.accept(sample(5,5,extra),true),true);
 }
});
test('rendering more frames in a paused tick is not settled gameplay',()=>{
 const s=new StableFrames();for(let frame=1;frame<10;frame++)assert.equal(s.accept(sample(frame,1),true),false);
});
test('unready, stale, wrong-viewer and broken-observer snapshots fail closed',()=>{
 const ready={fresh:true,indexReady:true,viewer:'emi',modifiersReleased:true};
 assert(usable(ready,'emi'));
 for(const mutation of [{fresh:false},{indexReady:false},{viewer:'jei'},{observerError:'missing hook'},{modifiersReleased:false}])assert.equal(usable({...ready,...mutation},'emi'),false);
 assert.throws(()=>center({x:0,y:0,w:0,h:16}));
});

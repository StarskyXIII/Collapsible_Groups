import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {sleep,occupied} from './session.mjs';
export async function quit(session) {
  const {bridge,manifest}=session;
  await bridge.call('world.leave',{},60000);
  const snapshot=await bridge.call('screen.snapshot');
  assert.equal(snapshot.screenClass,'net.minecraft.client.gui.screens.TitleScreen');
  const buttons=[];
  function walk(n) {
    if(n?.visible&&n?.active&&n?.extra?.kind==='button') {
      let component;try { component=typeof n.extra.component==='string'?JSON.parse(n.extra.component):n.extra.component; }catch{}
      if(component?.translate==='menu.quit')buttons.push(n);
    }
    for(const child of n?.children??[])walk(child);
  }
  walk(snapshot.root);assert.equal(buttons.length,1,'Native Quit button not found');
  const r=buttons[0].bounds;
  try {await bridge.call('cgtest.input',{action:'click',x:r.x+r.w/2,y:r.y+r.h/2});}
  catch(error) {if(!String(error).includes('disconnected'))throw error;}
  for(let i=0;i<100&&await occupied(manifest.port);i++)await sleep(100);
  assert.equal(await occupied(manifest.port),false,'Client did not quit');
  const alive=()=>{try {process.kill(session.identity.pid,0);return true;}catch(error){if(error.code==='ESRCH')return false;throw error;}};
  for(let i=0;i<150&&alive();i++)await sleep(200);
  assert.equal(alive(),false,'Verified client process did not exit');
  await sleep(750);
  const log=await fs.readFile(path.join(manifest.gameDir,'logs/latest.log'),'utf8');
  assert(log.includes('Stopping!'),'No normal shutdown log');
  return {normalQuit:true,processExited:true,pid:session.identity.pid,button:buttons[0].path};
}

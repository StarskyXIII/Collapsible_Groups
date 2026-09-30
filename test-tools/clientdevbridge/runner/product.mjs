import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {root} from './session.mjs';

const properties=Object.fromEntries((await fs.readFile(path.resolve(root,'../../gradle.properties'),'utf8'))
 .split(/\r?\n/).filter(line=>/^[a-z_]+\s*=/.test(line)).map(line=>{
  const at=line.indexOf('=');return [line.slice(0,at).trim(),line.slice(at+1).trim()];
 }));
export const minecraft=properties.minecraft_version;
export const version=properties.version;
assert(minecraft&&version&&/^[\w.+-]+$/.test(minecraft)&&/^[\w.+-]+$/.test(version));
export const productFor=loader=>({file:`collapsible_groups-${loader}-${minecraft}-${version}.jar`,version});

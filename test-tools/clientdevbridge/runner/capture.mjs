import fs from 'node:fs/promises';
import path from 'node:path';
import {connect} from './session.mjs';
const s=await connect(process.argv[2]);try {const shot=await s.bridge.call('screenshot',{afterTicks:3});const file=path.resolve(process.argv[3]);await fs.mkdir(path.dirname(file),{recursive:true});await fs.writeFile(file,Buffer.from(shot.png,'base64'));console.log(file);}finally{s.bridge.close();}

import {connect} from './session.mjs';
import {quit} from './lifecycle.mjs';
const s=await connect(process.argv[2]);try { console.log(JSON.stringify(await quit(s))); } finally { s.bridge.close(); }

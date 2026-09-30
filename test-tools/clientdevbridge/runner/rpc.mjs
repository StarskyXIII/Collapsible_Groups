import {connect,launch} from './session.mjs';
const [id,method,arg]=process.argv.slice(2);
const session=method==='launch'?await launch(id):await connect(id);
try { console.log(JSON.stringify(method==='launch'?session.identity:await session.bridge.call(method,arg?JSON.parse(arg):{},90000))); }
finally {session.bridge.close();}

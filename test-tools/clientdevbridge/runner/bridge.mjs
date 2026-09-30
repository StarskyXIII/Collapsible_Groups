export class Bridge {
  constructor(socket, timeout = 15000) {
    this.socket = socket; this.timeout = timeout; this.pending = new Map(); this.sequence = 0;
    this.hello = new Promise((resolve, reject) => {
      const timer = setTimeout(() => { reject(Error('Bridge handshake timed out')); socket.close(); }, timeout);
      socket.addEventListener('message', event => {
        let message; try { message = JSON.parse(event.data); } catch { return; }
        if (message.method === 'hello') {
          clearTimeout(timer);
          if (message.params.protocol !== 1) { reject(Error('Unsupported protocol')); socket.close(); }
          else resolve(message.params);
        }
        const request = this.pending.get(message.id);
        if (request) {
          clearTimeout(request.timer); this.pending.delete(message.id);
          if (message.error) request.reject(Error(JSON.stringify(message.error)));
          else request.resolve(message.result);
        }
      });
      const fail = () => {
        clearTimeout(timer); reject(Error('Bridge disconnected'));
        for (const request of this.pending.values()) { clearTimeout(request.timer); request.reject(Error('Bridge disconnected')); }
        this.pending.clear();
      };
      socket.addEventListener('close', fail); socket.addEventListener('error', fail);
    });
  }
  static async connect(port) {
    if (!Number.isInteger(port) || port < 1 || port > 65535) throw Error('Invalid port');
    const bridge = new Bridge(new WebSocket('ws://127.0.0.1:' + port));
    await bridge.hello; return bridge;
  }
  call(method, params = {}, timeout = this.timeout) {
    if(this.socket.readyState!==WebSocket.OPEN)return Promise.reject(Error('Bridge disconnected'));
    const id = ++this.sequence;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(Error('RPC timed out: ' + method)); }, timeout);
      this.pending.set(id, {resolve, reject, timer});
      try { this.socket.send(JSON.stringify({jsonrpc:'2.0', id, method, params})); }
      catch(error) { clearTimeout(timer); this.pending.delete(id); reject(error); }
    });
  }
  close() { this.socket.close(); }
}

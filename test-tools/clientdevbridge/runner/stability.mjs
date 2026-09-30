export class StableFrames {
  constructor(required = 3) { this.required = required; this.reset(); }
  reset() { this.key = null; this.frame = -1; this.tick = -1; this.samples = []; }
  accept(s, matches) {
    if (!matches) { this.reset(); return false; }
    const key = JSON.stringify([s.screenId,s.generation,s.viewerSearch?.value,s.search?.value,s.mode]);
    if (this.key !== key) { this.reset(); this.key = key; }
    if (s.frame > this.frame && s.tick > this.tick) {
      this.samples.push(s); this.frame = s.frame; this.tick = s.tick;
      if (this.samples.length > this.required) this.samples.shift();
    }
    return this.samples.length >= this.required;
  }
}

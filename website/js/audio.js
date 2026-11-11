export class SoundEffectsManager {
  constructor() {
    this.isEnabled = true;
    this._ctx = null;
  }

  _context() {
    if (!this.isEnabled || typeof window === "undefined") return null;
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return null;
    if (!this._ctx) {
      this._ctx = new AudioCtx();
    }
    if (this._ctx.state === "suspended") {
      this._ctx.resume().catch(() => {});
    }
    return this._ctx;
  }

  _playTone(freq, durationMs, type = "sine", volume = 0.08, delayMs = 0) {
    const ctx = this._context();
    if (!ctx) return;
    try {
      const start = ctx.currentTime + delayMs / 1000;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type;
      osc.frequency.setValueAtTime(freq, start);
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(volume, start + 0.015);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + durationMs / 1000);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(start);
      osc.stop(start + durationMs / 1000 + 0.02);
    } catch {
      // Ignore audio errors on blocked autoplay
    }
  }

  playCardTap() {
    this._playTone(540, 70, "triangle", 0.07, 0);
    this._playTone(720, 55, "sine", 0.04, 30);
  }

  playDrawCard() {
    this._playTone(340, 90, "sine", 0.05, 0);
  }

  playJumpSkip() {
    this._playTone(620, 80, "triangle", 0.07, 0);
    this._playTone(880, 110, "triangle", 0.07, 75);
  }

  playAttackTwo() {
    this._playTone(460, 110, "sawtooth", 0.05, 0);
    this._playTone(390, 150, "sawtooth", 0.05, 95);
  }

  playSevenCut() {
    this._playTone(523.25, 110, "triangle", 0.08, 0);
    this._playTone(659.25, 110, "triangle", 0.08, 95);
    this._playTone(1046.5, 220, "sine", 0.09, 190);
  }

  playCoinChime() {
    this._playTone(987.77, 90, "sine", 0.07, 0);
    this._playTone(1318.51, 180, "sine", 0.08, 80);
  }

  playWinFanfare() {
    this._playTone(523.25, 110, "triangle", 0.08, 0);
    this._playTone(659.25, 110, "triangle", 0.08, 100);
    this._playTone(783.99, 110, "triangle", 0.08, 200);
    this._playTone(1046.5, 280, "triangle", 0.09, 310);
  }

  playErrorTone() {
    this._playTone(210, 140, "sawtooth", 0.05, 0);
  }
}

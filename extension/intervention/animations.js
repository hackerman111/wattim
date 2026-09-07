/**
 * wattim Browser Extension - 60 FPS Canvas Breathing Animations
 * Styles: FILL, PULSE, ZEN_ORBIT
 * Phase Cycle: Inhale -> Hold -> Exhale -> Rest
 */

export class BreathAnimationController {
  /**
   * @param {HTMLCanvasElement} canvas
   * @param {Object} config
   * @param {number} config.durationSeconds - Total breathing duration (e.g. 10)
   * @param {'FILL'|'PULSE'|'ZEN_ORBIT'} config.style - Animation style
   * @param {Object} config.colors - Theme colors palette
   */
  constructor(canvas, config = {}) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.duration = Math.max(1, config.durationSeconds || 10);
    this.style = config.style || 'PULSE';
    this.colors = config.colors || {
      accent: '#88c0d0',
      accentGlow: 'rgba(136, 192, 208, 0.25)',
      accentSecondary: '#81a1c1',
      bgPrimary: '#2e3440'
    };

    this.onPhaseChange = null;
    this.onComplete = null;
    this.isRunning = false;
    this.animationFrameId = null;
    this.startTime = null;

    // Particles for Zen Orbit
    this.orbitParticles = [];
    for (let i = 0; i < 30; i++) {
      this.orbitParticles.push({
        angle: (i / 30) * Math.PI * 2,
        distanceOffset: (Math.random() - 0.5) * 20,
        speed: 0.02 + Math.random() * 0.015,
        size: 2 + Math.random() * 2.5,
        alpha: 0.3 + Math.random() * 0.7
      });
    }

    this.handleResize = this.resize.bind(this);
    window.addEventListener('resize', this.handleResize);
    this.resize();
  }

  resize() {
    if (!this.canvas) return;
    const dpr = window.devicePixelRatio || 1;
    const rect = this.canvas.getBoundingClientRect();
    this.width = rect.width || window.innerWidth;
    this.height = rect.height || window.innerHeight;

    this.canvas.width = this.width * dpr;
    this.canvas.height = this.height * dpr;
    this.ctx.scale(dpr, dpr);
  }

  setStyle(style) {
    this.style = style;
  }

  setColors(colors) {
    this.colors = colors;
  }

  /**
   * Calculates current breath phase based on elapsed time progress (0..1).
   * Phase breakdown:
   *  0.00 .. 0.35: Inhale (Вдох)
   *  0.35 .. 0.50: Hold (Задержка)
   *  0.50 .. 0.85: Exhale (Выдох)
   *  0.85 .. 1.00: Rest (Покой)
   */
  getPhaseState(totalProgress) {
    let phase = 'inhale';
    let phaseProgress = 0;
    let label = 'Вдох...';
    let expansion = 0; // 0..1 scale for visual effects

    if (totalProgress < 0.35) {
      phase = 'inhale';
      phaseProgress = totalProgress / 0.35;
      label = 'Вдох...';
      // Sine ease-in-out expansion: 0 -> 1
      expansion = (1 - Math.cos(phaseProgress * Math.PI)) / 2;
    } else if (totalProgress < 0.50) {
      phase = 'hold';
      phaseProgress = (totalProgress - 0.35) / 0.15;
      label = 'Задержка...';
      expansion = 1.0;
    } else if (totalProgress < 0.85) {
      phase = 'exhale';
      phaseProgress = (totalProgress - 0.50) / 0.35;
      label = 'Выдох...';
      // Sine ease-in-out contraction: 1 -> 0
      expansion = 1.0 - (1 - Math.cos(phaseProgress * Math.PI)) / 2;
    } else {
      phase = 'rest';
      phaseProgress = (totalProgress - 0.85) / 0.15;
      label = 'Покой...';
      expansion = 0.0;
    }

    return { phase, phaseProgress, label, expansion };
  }

  start() {
    this.isRunning = true;
    this.startTime = performance.now();
    this.lastReportedPhase = '';
    this.render();
  }

  stop() {
    this.isRunning = false;
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
  }

  destroy() {
    this.stop();
    window.removeEventListener('resize', this.handleResize);
  }

  render() {
    if (!this.isRunning) return;

    const now = performance.now();
    const elapsedSeconds = (now - this.startTime) / 1000;
    const totalProgress = Math.min(1.0, elapsedSeconds / this.duration);
    const remainingSeconds = Math.max(0, Math.ceil(this.duration - elapsedSeconds));

    const phaseState = this.getPhaseState(totalProgress);

    // Notify listener on phase/tick changes
    if (this.onPhaseChange) {
      this.onPhaseChange({
        ...phaseState,
        totalProgress,
        remainingSeconds,
        elapsedSeconds
      });
    }

    // Clear Canvas
    this.ctx.clearRect(0, 0, this.width, this.height);

    // Render chosen style
    switch (this.style) {
      case 'FILL':
        this.renderFill(phaseState, now);
        break;
      case 'ZEN_ORBIT':
        this.renderZenOrbit(phaseState, now);
        break;
      case 'PULSE':
      default:
        this.renderPulse(phaseState, now);
        break;
    }

    if (totalProgress >= 1.0) {
      this.isRunning = false;
      if (this.onComplete) {
        this.onComplete();
      }
      return;
    }

    this.animationFrameId = requestAnimationFrame(() => this.render());
  }

  /**
   * Style: FILL - Wave level filling the screen from bottom to top
   */
  renderFill(phaseState, timestamp) {
    const { expansion } = phaseState;
    const ctx = this.ctx;
    const w = this.width;
    const h = this.height;

    // Target fill height: 15% at rest, up to 85% at peak inhalation
    const fillHeight = h * (0.15 + 0.70 * expansion);
    const baseY = h - fillHeight;

    const gradient = ctx.createLinearGradient(0, baseY - 50, 0, h);
    gradient.addColorStop(0, this.colors.accent);
    gradient.addColorStop(0.3, this.colors.accentSecondary);
    gradient.addColorStop(1, this.colors.bgPrimary);

    ctx.fillStyle = gradient;
    ctx.beginPath();
    ctx.moveTo(0, h);
    ctx.lineTo(0, baseY);

    // Dynamic wave harmonics
    const waveFreq = 0.008;
    const waveAmp = 12 + 10 * expansion;
    const waveTime = timestamp * 0.003;

    for (let x = 0; x <= w; x += 10) {
      const y = baseY + Math.sin(x * waveFreq + waveTime) * waveAmp;
      ctx.lineTo(x, y);
    }

    ctx.lineTo(w, h);
    ctx.closePath();
    ctx.fill();

    // Subtle luminous crest line
    ctx.strokeStyle = this.colors.accent;
    ctx.lineWidth = 2;
    ctx.beginPath();
    for (let x = 0; x <= w; x += 10) {
      const y = baseY + Math.sin(x * waveFreq + waveTime) * waveAmp;
      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
  }

  /**
   * Style: PULSE - Expanding, concentric glowing awareness sphere
   */
  renderPulse(phaseState, timestamp) {
    const { expansion } = phaseState;
    const ctx = this.ctx;
    const cx = this.width / 2;
    const cy = this.height / 2;

    const minRadius = Math.min(this.width, this.height) * 0.12;
    const maxRadius = Math.min(this.width, this.height) * 0.32;
    const currentRadius = minRadius + (maxRadius - minRadius) * expansion;

    // Outer faint aura
    const auraGradient = ctx.createRadialGradient(cx, cy, currentRadius * 0.5, cx, cy, currentRadius * 1.5);
    auraGradient.addColorStop(0, this.colors.accentGlow);
    auraGradient.addColorStop(0.8, 'rgba(0, 0, 0, 0)');
    ctx.fillStyle = auraGradient;
    ctx.beginPath();
    ctx.arc(cx, cy, currentRadius * 1.5, 0, Math.PI * 2);
    ctx.fill();

    // Concentric ripple rings
    const rippleCount = 3;
    for (let i = 1; i <= rippleCount; i++) {
      const r = currentRadius * (1 + (i * 0.15) * (0.5 + 0.5 * expansion));
      ctx.strokeStyle = this.colors.accentGlow;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.stroke();
    }

    // Main glowing sphere
    const sphereGradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, currentRadius);
    sphereGradient.addColorStop(0, this.colors.accent);
    sphereGradient.addColorStop(0.7, this.colors.accentSecondary);
    sphereGradient.addColorStop(1, this.colors.accentGlow);

    ctx.fillStyle = sphereGradient;
    ctx.beginPath();
    ctx.arc(cx, cy, currentRadius, 0, Math.PI * 2);
    ctx.fill();

    // Central focal point
    ctx.fillStyle = '#ffffff';
    ctx.globalAlpha = 0.8;
    ctx.beginPath();
    ctx.arc(cx, cy, 4 + 3 * expansion, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = 1.0;
  }

  /**
   * Style: ZEN_ORBIT - Celestial orbiting particles with light trails
   */
  renderZenOrbit(phaseState, timestamp) {
    const { expansion } = phaseState;
    const ctx = this.ctx;
    const cx = this.width / 2;
    const cy = this.height / 2;

    const baseRadius = Math.min(this.width, this.height) * 0.18;
    const currentOrbitRadius = baseRadius * (0.8 + 0.5 * expansion);

    // Orbit ring guideline
    ctx.strokeStyle = this.colors.accentGlow;
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 8]);
    ctx.beginPath();
    ctx.arc(cx, cy, currentOrbitRadius, 0, Math.PI * 2);
    ctx.stroke();
    ctx.setLineDash([]);

    // Update and draw orbiting particles
    for (const p of this.orbitParticles) {
      p.angle += p.speed * (0.7 + 0.6 * expansion);
      const r = currentOrbitRadius + p.distanceOffset * expansion;
      const px = cx + Math.cos(p.angle) * r;
      const py = cy + Math.sin(p.angle) * r;

      ctx.fillStyle = this.colors.accent;
      ctx.globalAlpha = p.alpha * (0.4 + 0.6 * expansion);
      ctx.beginPath();
      ctx.arc(px, py, p.size * (0.8 + 0.4 * expansion), 0, Math.PI * 2);
      ctx.fill();
    }

    // Center focal star
    ctx.globalAlpha = 0.9;
    ctx.fillStyle = this.colors.accent;
    ctx.beginPath();
    ctx.arc(cx, cy, 8 + 6 * expansion, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = 1.0;
  }
}

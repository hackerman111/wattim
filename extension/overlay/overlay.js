/**
 * wattim Browser Extension - In-Page Shadow DOM Overlay
 * Runs at document_start to immediately intercept targeted pages.
 * Displays mindful breathing intervention in an isolated Shadow DOM.
 * Upon clicking «ПРОДОЛЖИТЬ», unmounts smoothly with zero page reload.
 */

(function () {
  if (window.__wattimOverlayInitialized) return;
  window.__wattimOverlayInitialized = true;

  // Theme palettes dictionary for Shadow DOM injection
  const THEME_PALETTES = {
    nord: {
      bgPrimary: '#2e3440',
      bgSurface: '#3b4252',
      bgElevated: '#434c5e',
      textPrimary: '#eceff4',
      textMuted: '#d8dee9',
      textFaint: '#4c566a',
      accent: '#88c0d0',
      accentGlow: 'rgba(136, 192, 208, 0.25)',
      accentSecondary: '#81a1c1',
      border: '#4c566a',
      error: '#bf616a',
      success: '#a3be8c'
    },
    catppuccin: {
      bgPrimary: '#1e1e2e',
      bgSurface: '#313244',
      bgElevated: '#45475a',
      textPrimary: '#cdd6f4',
      textMuted: '#a6adc8',
      textFaint: '#585b70',
      accent: '#cba6f7',
      accentGlow: 'rgba(203, 166, 247, 0.25)',
      accentSecondary: '#b4befe',
      border: '#45475a',
      error: '#f38ba8',
      success: '#a6e3a1'
    },
    dracula: {
      bgPrimary: '#282a36',
      bgSurface: '#44475a',
      bgElevated: '#6272a4',
      textPrimary: '#f8f8f2',
      textMuted: '#6272a4',
      textFaint: '#44475a',
      accent: '#bd93f9',
      accentGlow: 'rgba(189, 147, 249, 0.25)',
      accentSecondary: '#ff79c6',
      border: '#6272a4',
      error: '#ff5555',
      success: '#50fa7b'
    },
    gruvbox: {
      bgPrimary: '#282828',
      bgSurface: '#3c3836',
      bgElevated: '#504945',
      textPrimary: '#ebdbb2',
      textMuted: '#d5c4a1',
      textFaint: '#665c54',
      accent: '#fabd2f',
      accentGlow: 'rgba(250, 189, 47, 0.25)',
      accentSecondary: '#fe8019',
      border: '#504945',
      error: '#fb4934',
      success: '#b8bb26'
    },
    tokyo_night: {
      bgPrimary: '#1a1b26',
      bgSurface: '#24283b',
      bgElevated: '#2f354f',
      textPrimary: '#c0caf5',
      textMuted: '#a9b1d6',
      textFaint: '#565f89',
      accent: '#7aa2f7',
      accentGlow: 'rgba(122, 162, 247, 0.25)',
      accentSecondary: '#bb9af7',
      border: '#414868',
      error: '#f7768e',
      success: '#9ece6a'
    },
    cyber_terminal: {
      bgPrimary: '#0a0e14',
      bgSurface: '#131924',
      bgElevated: '#1f2737',
      textPrimary: '#e6edf3',
      textMuted: '#8b949e',
      textFaint: '#30363d',
      accent: '#00ff66',
      accentGlow: 'rgba(0, 255, 102, 0.25)',
      accentSecondary: '#00cc55',
      border: '#238636',
      error: '#ff3344',
      success: '#00ff66'
    }
  };

  /**
   * 60 FPS Canvas Breathing Animator
   */
  class CanvasAnimator {
    constructor(canvas, duration, style, palette) {
      this.canvas = canvas;
      this.ctx = canvas.getContext('2d');
      this.duration = Math.max(1, duration || 10);
      this.style = style || 'PULSE';
      this.palette = palette;
      this.isRunning = false;
      this.animId = null;
      this.startTime = null;
      this.onPhaseChange = null;
      this.onComplete = null;

      this.particles = [];
      for (let i = 0; i < 30; i++) {
        this.particles.push({
          angle: (i / 30) * Math.PI * 2,
          offset: (Math.random() - 0.5) * 20,
          speed: 0.02 + Math.random() * 0.015,
          size: 2 + Math.random() * 2.5,
          alpha: 0.3 + Math.random() * 0.7
        });
      }

      this.resize();
    }

    resize() {
      const dpr = window.devicePixelRatio || 1;
      this.width = window.innerWidth;
      this.height = window.innerHeight;
      this.canvas.width = this.width * dpr;
      this.canvas.height = this.height * dpr;
      this.ctx.scale(dpr, dpr);
    }

    start() {
      this.isRunning = true;
      this.startTime = performance.now();
      this.render();
    }

    stop() {
      this.isRunning = false;
      if (this.animId) cancelAnimationFrame(this.animId);
    }

    render() {
      if (!this.isRunning) return;

      const now = performance.now();
      const elapsed = (now - this.startTime) / 1000;
      const progress = Math.min(1.0, elapsed / this.duration);
      const remainingSeconds = Math.max(0, Math.ceil(this.duration - elapsed));

      let phase = 'Вдох...';
      let expansion = 0;

      if (progress < 0.35) {
        phase = 'Вдох...';
        const p = progress / 0.35;
        expansion = (1 - Math.cos(p * Math.PI)) / 2;
      } else if (progress < 0.50) {
        phase = 'Задержка...';
        expansion = 1.0;
      } else if (progress < 0.85) {
        phase = 'Выдох...';
        const p = (progress - 0.50) / 0.35;
        expansion = 1.0 - (1 - Math.cos(p * Math.PI)) / 2;
      } else {
        phase = 'Покой...';
        expansion = 0.0;
      }

      if (this.onPhaseChange) {
        this.onPhaseChange(phase, remainingSeconds);
      }

      const ctx = this.ctx;
      ctx.clearRect(0, 0, this.width, this.height);

      if (this.style === 'FILL') {
        const fillHeight = this.height * (0.15 + 0.70 * expansion);
        const baseY = this.height - fillHeight;
        const grad = ctx.createLinearGradient(0, baseY - 40, 0, this.height);
        grad.addColorStop(0, this.palette.accent);
        grad.addColorStop(1, this.palette.bgPrimary);
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.moveTo(0, this.height);
        ctx.lineTo(0, baseY);
        for (let x = 0; x <= this.width; x += 10) {
          const y = baseY + Math.sin(x * 0.008 + now * 0.003) * (12 + 10 * expansion);
          ctx.lineTo(x, y);
        }
        ctx.lineTo(this.width, this.height);
        ctx.fill();
      } else if (this.style === 'ZEN_ORBIT') {
        const cx = this.width / 2;
        const cy = this.height / 2;
        const r = Math.min(this.width, this.height) * 0.18 * (0.8 + 0.5 * expansion);
        for (const p of this.particles) {
          p.angle += p.speed * (0.7 + 0.6 * expansion);
          const px = cx + Math.cos(p.angle) * (r + p.offset * expansion);
          const py = cy + Math.sin(p.angle) * (r + p.offset * expansion);
          ctx.fillStyle = this.palette.accent;
          ctx.globalAlpha = p.alpha;
          ctx.beginPath();
          ctx.arc(px, py, p.size, 0, Math.PI * 2);
          ctx.fill();
        }
        ctx.globalAlpha = 1.0;
      } else {
        // PULSE
        const cx = this.width / 2;
        const cy = this.height / 2;
        const minR = Math.min(this.width, this.height) * 0.12;
        const maxR = Math.min(this.width, this.height) * 0.32;
        const radius = minR + (maxR - minR) * expansion;

        const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
        grad.addColorStop(0, this.palette.accent);
        grad.addColorStop(0.7, this.palette.accentSecondary);
        grad.addColorStop(1, this.palette.accentGlow);

        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(cx, cy, radius, 0, Math.PI * 2);
        ctx.fill();
      }

      if (progress >= 1.0) {
        this.isRunning = false;
        if (this.onComplete) this.onComplete();
        return;
      }

      this.animId = requestAnimationFrame(() => this.render());
    }
  }

  function formatDuration(totalSec) {
    if (totalSec >= 3600 && totalSec % 3600 === 0) return `${totalSec / 3600} ч`;
    if (totalSec >= 60 && totalSec % 60 === 0) return `${totalSec / 60} мин`;
    if (totalSec >= 60) return `${Math.floor(totalSec / 60)}м ${totalSec % 60}с`;
    return `${totalSec} сек`;
  }

  /**
   * Mounts the in-page Shadow DOM overlay onto the current document.
   */
  async function showOverlay(data) {
    const configuredSeconds = data.reInterventionSeconds || 900;
    const themeName = (data.settings && data.settings.theme) || 'nord';
    const palette = THEME_PALETTES[themeName] || THEME_PALETTES.nord;
    const phrases = (data.settings && data.settings.phrases) || ['Сделай глубокий вдох...'];
    const randomPhrase = phrases[Math.floor(Math.random() * phrases.length)];

    // Create Host container
    const host = document.createElement('div');
    host.id = 'wattim-overlay-host';
    host.style.cssText = 'position: fixed !important; inset: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; margin: 0 !important; padding: 0 !important;';

    const shadow = host.attachShadow({ mode: 'closed' });

    // Fetch CSS from extension
    let cssText = '';
    try {
      const cssUrl = chrome.runtime.getURL('overlay/overlay.css');
      const res = await fetch(cssUrl);
      cssText = await res.text();
    } catch (e) {
      console.warn('[wattim] Failed to fetch overlay.css:', e);
    }

    // Inject CSS variables
    const styleEl = document.createElement('style');
    styleEl.textContent = `
      :host {
        --bg-primary: ${palette.bgPrimary};
        --bg-surface: ${palette.bgSurface};
        --bg-surface-elevated: ${palette.bgElevated};
        --text-primary: ${palette.textPrimary};
        --text-muted: ${palette.textMuted};
        --text-faint: ${palette.textFaint};
        --accent: ${palette.accent};
        --accent-glow: ${palette.accentGlow};
        --accent-secondary: ${palette.accentSecondary};
        --border: ${palette.border};
        --error: ${palette.error};
        --success: ${palette.success};
      }
      ${cssText}
    `;
    shadow.appendChild(styleEl);

    // Build Backdrop
    const backdrop = document.createElement('div');
    backdrop.className = 'overlay-backdrop';

    const isHardBlock = data.mode === 'hard_block';

    backdrop.innerHTML = `
      <canvas id="breathCanvas"></canvas>
      <main class="terminal-container">
        <header class="terminal-header">
          <div class="terminal-dots">
            <span class="dot dot-red"></span>
            <span class="dot dot-yellow"></span>
            <span class="dot dot-green"></span>
          </div>
          <div class="terminal-prompt">${isHardBlock ? '$ wattim --hard-block' : '$ wattim --breathe'}</div>
          <div class="terminal-target-badge">${data.targetDomain || window.location.hostname}</div>
        </header>

        ${isHardBlock ? `
          <section class="intervention-card card-blocked">
            <div class="blocked-icon">🔒</div>
            <h1 class="blocked-title">Сайт заблокирован</h1>
            <p class="blocked-desc">Действует расписание полной блокировки. Пора вернуться к важным делам.</p>
            <div class="actions-group">
              <button class="btn btn-exit" id="btnExit">
                <span>🚪</span>
                <span>ЗАКРЫТЬ ВКЛАДКУ</span>
              </button>
            </div>
          </section>
        ` : `
          <section class="intervention-card">
            ${data.attempts > 0 ? `
              <div class="backoff-badge">
                ⚡ Экспоненциальный рост: +${data.growthPercent || 20}% (попытка #${data.attempts + 1})
              </div>
            ` : ''}
            <div class="phase-container">
              <h1 class="phase-label" id="phaseLabel">Вдох...</h1>
              <div class="timer-countdown" id="timerDisplay">${data.delay || 10}с</div>
            </div>
            <p class="quote-text" id="quoteText">«${randomPhrase}»</p>
            <div class="actions-group">
              <button class="btn btn-exit" id="btnExit">
                <span>🚪</span>
                <span>ВЫЙТИ</span>
                <span class="btn-subtext">(закрыть вкладку)</span>
              </button>
              <button class="btn btn-continue" id="btnContinue" style="display: none;">
                <span>🌿</span>
                <span>ПРОДОЛЖИТЬ НА САЙТ</span>
                <span class="btn-subtext">(допуск на ${formatDuration(configuredSeconds)} · без перезагрузки)</span>
              </button>
            </div>
            <div class="emergency-wrapper">
              <button class="btn-link" id="btnEmergencyTrigger">⚡ Экстренный вход...</button>
            </div>
          </section>
        `}

        <footer class="terminal-footer">
          <span>🌱 Сбережено: <strong>${(data.stats && data.stats.savedMinutes) || 0}</strong> минут · <strong>${(data.stats && data.stats.savedImpulses) || 0}</strong> импульсов</span>
        </footer>
      </main>

      <!-- Emergency Modal -->
      <div class="modal-backdrop" id="emergencyModal" style="display: none;">
        <div class="modal-card">
          <div class="modal-header">
            <span class="modal-title">⚠️ Вы уверены?</span>
            <button class="modal-close" id="btnModalClose">&times;</button>
          </div>
          <p class="modal-text">
            Импульсивный вход прерывает вашу осознанность. Действительно необходимо открыть сайт прямо сейчас?
          </p>
          <div class="modal-actions">
            <button class="btn btn-modal-primary" id="btnEmergencyOnce">
              Войти разово прямо сейчас
            </button>
            <button class="btn btn-modal-cancel" id="btnEmergencyCancel">
              Вернуться к дыханию
            </button>
          </div>
        </div>
      </div>
    `;

    shadow.appendChild(backdrop);

    // Mount to document
    (document.body || document.documentElement).appendChild(host);

    // Pause any media playing on the underlying page
    const mediaElements = document.querySelectorAll('video, audio');
    mediaElements.forEach(m => {
      try { if (!m.paused) m.pause(); } catch (_) {}
    });

    const canvas = shadow.getElementById('breathCanvas');
    const phaseLabel = shadow.getElementById('phaseLabel');
    const timerDisplay = shadow.getElementById('timerDisplay');
    const btnExit = shadow.getElementById('btnExit');
    const btnContinue = shadow.getElementById('btnContinue');
    const btnEmergencyTrigger = shadow.getElementById('btnEmergencyTrigger');
    const emergencyModal = shadow.getElementById('emergencyModal');
    const btnModalClose = shadow.getElementById('btnModalClose');
    const btnEmergencyOnce = shadow.getElementById('btnEmergencyOnce');
    const btnEmergencyCancel = shadow.getElementById('btnEmergencyCancel');

    let animator = null;

    if (!isHardBlock && canvas) {
      const animStyle = (data.settings && data.settings.animation) || 'PULSE';
      animator = new CanvasAnimator(canvas, data.delay || 10, animStyle, palette);

      animator.onPhaseChange = (phase, remaining) => {
        if (phaseLabel) phaseLabel.textContent = phase;
        if (timerDisplay) timerDisplay.textContent = `${remaining}с`;
      };

      animator.onComplete = () => {
        if (phaseLabel) phaseLabel.textContent = 'Осознанность 🌿';
        if (timerDisplay) timerDisplay.textContent = 'Готово';
        if (btnContinue) {
          btnContinue.style.display = 'flex';
          btnContinue.focus();
        }
      };

      animator.start();
    }

    /**
     * Closes current tab and logs impulse.
     */
    const handleExit = () => {
      if (animator) animator.stop();
      chrome.runtime.sendMessage({
        type: 'CLOSE_TAB',
        domain: data.targetDomain || window.location.hostname,
        minutesSaved: (data.settings && data.settings.avgSessionMinutes) || 10
      });
    };

    /**
     * Unmounts overlay with smooth fade-out (Zero Reload).
     */
    const handleContinue = (durationSeconds = configuredSeconds) => {
      if (animator) animator.stop();
      chrome.runtime.sendMessage({
        type: 'GRANT_PASS',
        domain: data.targetDomain || window.location.hostname,
        durationSeconds
      });

      backdrop.classList.add('fade-out');
      setTimeout(() => {
        if (host && host.parentNode) {
          host.parentNode.removeChild(host);
        }
      }, 350);
    };

    if (btnExit) btnExit.addEventListener('click', handleExit);
    if (btnContinue) btnContinue.addEventListener('click', () => handleContinue(configuredSeconds));

    // Emergency Modal handlers
    if (btnEmergencyTrigger && emergencyModal) {
      btnEmergencyTrigger.addEventListener('click', () => { emergencyModal.style.display = 'flex'; });
      btnModalClose.addEventListener('click', () => { emergencyModal.style.display = 'none'; });
      btnEmergencyCancel.addEventListener('click', () => { emergencyModal.style.display = 'none'; });
      btnEmergencyOnce.addEventListener('click', () => {
        emergencyModal.style.display = 'none';
        handleContinue(configuredSeconds);
      });
    }

    // Start dwell time monitor for Re-intervention
    startDwellSentinel(data.targetDomain || window.location.hostname, configuredSeconds);
  }

  /**
   * Monitors active dwell time and triggers Re-intervention if user stays too long.
   */
  function startDwellSentinel(domain, durationSeconds = 900) {
    const CHECK_INTERVAL = Math.max(3000, Math.min(15000, Math.floor((durationSeconds || 900) * 1000 / 3)));
    const intervalId = setInterval(() => {
      if (document.visibilityState !== 'visible') return;

      chrome.runtime.sendMessage({ type: 'CHECK_SESSION', domain }, (res) => {
        if (chrome.runtime.lastError) return;
        if (res && res.valid === false) {
          clearInterval(intervalId);
          // Re-evaluate navigation to show overlay again
          chrome.runtime.sendMessage(
            { type: 'CHECK_NAVIGATION', url: window.location.href, domain },
            (navData) => {
              if (navData && navData.shouldIntervene) {
                showOverlay(navData);
              }
            }
          );
        }
      });
    }, CHECK_INTERVAL);
  }

  // Initial check upon page load
  try {
    chrome.runtime.sendMessage(
      {
        type: 'CHECK_NAVIGATION',
        url: window.location.href,
        domain: window.location.hostname
      },
      (response) => {
        if (chrome.runtime.lastError || !response) return;
        if (response.shouldIntervene) {
          showOverlay(response);
        } else if (response.hasActivePass) {
          startDwellSentinel(window.location.hostname);
        }
      }
    );
  } catch (err) {
    console.debug('[wattim] Navigation check bypassed:', err);
  }
})();

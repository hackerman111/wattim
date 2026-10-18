// wattim × One Sec × Go Fucking Work — Content Script (v2.0 Fixed & Merged)
(function() {
  if (window.__wattimGfwInjected) return;
  window.__wattimGfwInjected = true;

  const FALLBACK_SETTINGS = {
  "baseDelay": 10,
  "progressiveStep": 5,
  "backoffMode": "exponential",
  "growthPercent": 25,
  "windowMinutes": 60,
  "reinterventionMinutes": 5,
  "tabBlurResetSec": 30,
  "requireIntention": true,
  "minIntentionLength": 8,
  "requireChallengePhrase": true,
  "challengePhrase": "Я захожу на {domain} строго по делу",
  "canvasStyle": "PULSE",
  "themePalette": "gfw_crimson",
  "pauseMedia": true,
  "hapticFeedback": true,
  "touchHoldMode": false,
  "avgSessionMinutes": 10,
  "domains": [
    "youtube.com",
    "reddit.com",
    "x.com",
    "twitter.com",
    "vk.com",
    "t.me",
    "pikabu.ru",
    "instagram.com",
    "tiktok.com",
    "twitch.tv"
  ],
  "phrases": [
    "Серьёзно? Опять сюда? Твои задачи сами себя не закроют, иди работай.",
    "Ты открыл эту вкладку на чистом рефлексе. Закрой её и займись делом.",
    "Go Fucking Work. Мемы деградируют и без твоего участия.",
    "У тебя что, все дедлайны закрыты и на счету миллион долларов? Нет? Тогда за работу.",
    "Ещё одна минута прокрастинации — и ты снова будешь сидеть ночью в мыле.",
    "Сделай глубокий вдох, осознай масштаб своей лени и закрой эту вкладку."
  ],
  "schedules": [
    {
      "id": "work_focus",
      "name": "Рабочий фокус (Hard Block)",
      "enabled": false,
      "mode": "hard_block",
      "days": [
        1,
        2,
        3,
        4,
        5
      ],
      "start": "09:00",
      "end": "18:00"
    }
  ]
};

  const PALETTES = {
    gfw_crimson: { bg: '#09090b', card: '#18181b', border: '#27272a', accent: '#f43f5e', accent2: '#fb923c', glow: 'rgba(244,63,94,0.28)', ok: '#10b981' },
    nord: { bg: '#2e3440', card: '#3b4252', border: '#4c566a', accent: '#88c0d0', accent2: '#81a1c1', glow: 'rgba(136,192,208,0.25)', ok: '#a3be8c' },
    catppuccin: { bg: '#1e1e2e', card: '#313244', border: '#45475a', accent: '#cba6f7', accent2: '#b4befe', glow: 'rgba(203,166,247,0.25)', ok: '#a6e3a1' },
    dracula: { bg: '#282a36', card: '#44475a', border: '#6272a4', accent: '#bd93f9', accent2: '#ff79c6', glow: 'rgba(189,147,249,0.25)', ok: '#50fa7b' },
    gruvbox: { bg: '#282828', card: '#3c3836', border: '#504945', accent: '#fabd2f', accent2: '#fe8019', glow: 'rgba(250,189,47,0.25)', ok: '#b8bb26' },
    tokyo_night: { bg: '#1a1b26', card: '#24283b', border: '#414868', accent: '#7aa2f7', accent2: '#bb9af7', glow: 'rgba(122,162,247,0.25)', ok: '#9ece6a' },
    cyber_terminal: { bg: '#0a0e14', card: '#131924', border: '#238636', accent: '#00ff66', accent2: '#00cc55', glow: 'rgba(0,255,102,0.25)', ok: '#00ff66' }
  };

  let currentSettings = FALLBACK_SETTINGS;
  let hudInterval = null;
  let hiddenAtTimestamp = null;
  let activePassUntil = 0;
  let activeIntention = '';
  let activeDomainKey = '';
  let lastCheckedUrl = location.href;

  function normalizeHost(hostname) {
    return (hostname || '').toLowerCase().replace(/^(www|m|mobile)\./, '');
  }

  function vibrateDevice(pattern) {
    if (!currentSettings.hapticFeedback) return;
    if (typeof navigator !== 'undefined' && navigator.vibrate) {
      try { navigator.vibrate(pattern); } catch (e) {}
    }
  }

  function pauseAllPageMedia() {
    if (!currentSettings.pauseMedia) return;
    document.querySelectorAll('video, audio').forEach(el => {
      try { if (!el.paused) el.pause(); } catch (e) {}
    });
  }

  function checkCurrentPage(forceReintervene = false) {
    if (!chrome || !chrome.runtime || !chrome.runtime.sendMessage) return;
    chrome.runtime.sendMessage({
      type: 'EVALUATE_SITE',
      hostname: window.location.hostname,
      url: window.location.href,
      forceReintervene
    }, (res) => {
      if (chrome.runtime.lastError || !res) return;
      if (res.settings) currentSettings = res.settings;

      if (res.shouldIntervene) {
        showInterventionOverlay(res, forceReintervene);
      } else if (res.hasActivePass) {
        activeDomainKey = res.domainKey;
        activePassUntil = res.passUntil;
        activeIntention = res.intention || 'Осознанный визит';
        startActiveHudAndReintervention(res.domainKey);
      }
    });
  }

  // 60 FPS Canvas Animator from wattim integrated into our Shadow DOM
  function start60FpsCanvas(canvas, style, pal) {
    const ctx = canvas.getContext('2d');
    let running = true;
    let expand = 0;
    const particles = Array.from({ length: 28 }, (_, i) => ({
      angle: (i / 28) * Math.PI * 2,
      offset: (Math.random() - 0.5) * 22,
      speed: 0.015 + Math.random() * 0.015,
      size: 2 + Math.random() * 2.5
    }));

    function resize() {
      const dpr = window.devicePixelRatio || 1;
      canvas.width = window.innerWidth * dpr;
      canvas.height = window.innerHeight * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    resize();
    window.addEventListener('resize', resize);

    function frame(now) {
      if (!running) return;
      const w = window.innerWidth;
      const h = window.innerHeight;
      ctx.clearRect(0, 0, w, h);

      // Smooth 6-second breathing cycle
      const cycle = (now % 6000) / 6000;
      expand = (1 - Math.cos(cycle * Math.PI * 2)) / 2;

      if (style === 'FILL') {
        const fillH = h * (0.12 + 0.65 * expand);
        const baseY = h - fillH;
        const grad = ctx.createLinearGradient(0, baseY - 40, 0, h);
        grad.addColorStop(0, pal.glow);
        grad.addColorStop(1, 'transparent');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.moveTo(0, h);
        for (let x = 0; x <= w; x += 12) {
          ctx.lineTo(x, baseY + Math.sin(x * 0.008 + now * 0.003) * (10 + 10 * expand));
        }
        ctx.lineTo(w, h);
        ctx.fill();
      } else if (style === 'ZEN_ORBIT') {
        const cx = w / 2, cy = h / 2;
        const r = Math.min(w, h) * 0.22 * (0.8 + 0.45 * expand);
        particles.forEach(p => {
          p.angle += p.speed;
          const px = cx + Math.cos(p.angle) * (r + p.offset * expand);
          const py = cy + Math.sin(p.angle) * (r + p.offset * expand);
          ctx.fillStyle = pal.accent;
          ctx.globalAlpha = 0.35 + 0.4 * expand;
          ctx.beginPath();
          ctx.arc(px, py, p.size, 0, Math.PI * 2);
          ctx.fill();
        });
        ctx.globalAlpha = 1;
      } else {
        // PULSE
        const cx = w / 2, cy = h / 2;
        const r = Math.min(w, h) * (0.14 + 0.20 * expand);
        const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, r * 1.4);
        grad.addColorStop(0, pal.glow);
        grad.addColorStop(1, 'transparent');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(cx, cy, r * 1.4, 0, Math.PI * 2);
        ctx.fill();
      }
      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
    return () => { running = false; window.removeEventListener('resize', resize); };
  }

  function showInterventionOverlay(evalData, isReintervention) {
    if (document.getElementById('onesec-gfw-root')) return;
    clearInterval(hudInterval);
    const existingHud = document.getElementById('onesec-gfw-hud');
    if (existingHud) existingHud.remove();

    const s = evalData.settings || currentSettings;
    const pal = PALETTES[s.themePalette] || PALETTES.gfw_crimson;
    const domainKey = evalData.domainKey || normalizeHost(window.location.hostname);
    const totalWaitSec = evalData.waitSec || s.baseDelay || 10;
    const attemptNumber = evalData.attemptNumber || 1;
    const isHardBlock = evalData.mode === 'hard_block';

    const phrases = (s.phrases && s.phrases.length) ? s.phrases : FALLBACK_SETTINGS.phrases;
    const roastText = phrases[Math.floor(Math.random() * phrases.length)];
    const expectedChallenge = (s.challengePhrase || 'Я захожу на {domain} строго по делу').replace(/\{domain\}/g, domainKey).trim();

    pauseAllPageMedia();
    vibrateDevice([60, 40, 60]);
    const mediaStopper = setInterval(pauseAllPageMedia, 400);

    const hostEl = document.createElement('div');
    hostEl.id = 'onesec-gfw-root';
    hostEl.style.cssText = 'position:fixed !important;inset:0 !important;z-index:2147483647 !important;width:100vw !important;height:100dvh !important;touch-action:manipulation !important;';
    const shadow = hostEl.attachShadow({ mode: 'open' });

    shadow.innerHTML = `
      <style>
        * { box-sizing: border-box; font-family: 'Inter', -apple-system, BlinkMacSystemFont, Roboto, sans-serif; -webkit-tap-highlight-color: transparent; }
        .overlay {
          position: fixed; inset: 0; width: 100vw; height: 100dvh;
          background: ${pal.bg}; color: #fafafa;
          display: flex; flex-direction: column; align-items: center; justify-content: center;
          padding: calc(16px + env(safe-area-inset-top, 0px)) 20px calc(20px + env(safe-area-inset-bottom, 0px));
          text-align: center; user-select: none; overflow-y: auto;
          transition: opacity 0.3s ease;
        }
        #bg-canvas { position: fixed; inset: 0; width: 100vw; height: 100dvh; pointer-events: none; z-index: 0; }
        .card-wrap {
          position: relative; z-index: 2; width: 100%; max-width: 520px;
          background: rgba(24, 24, 27, 0.86); backdrop-filter: blur(20px);
          border: 1px solid ${pal.border}; border-radius: 24px;
          padding: 24px 22px; box-shadow: 0 25px 60px rgba(0,0,0,0.65), 0 0 35px ${pal.glow};
          display: flex; flex-direction: column; align-items: center;
        }
        .badge {
          display: inline-flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 6px;
          padding: 6px 14px; border-radius: 999px;
          background: #09090b; border: 1px solid ${pal.border};
          font-size: 12px; font-family: monospace; color: #d4d4d8; margin-bottom: 14px;
        }
        .orb-wrap {
          position: relative; width: 112px; height: 112px;
          display: flex; align-items: center; justify-content: center; margin-bottom: 8px;
          cursor: pointer; touch-action: none;
        }
        .ring {
          position: absolute; inset: 0; border-radius: 999px;
          border: 2.5px solid ${pal.accent};
          transition: transform 3s ease-in-out, border-color 0.2s;
        }
        .timer-circle {
          width: 82px; height: 82px; border-radius: 999px;
          background: #09090b; border: 1px solid ${pal.border};
          display: flex; flex-direction: column; align-items: center; justify-content: center;
          font-size: 28px; font-weight: 900; font-family: monospace;
        }
        .breath-lbl {
          font-size: 11px; letter-spacing: 0.14em; text-transform: uppercase;
          color: ${pal.accent}; font-weight: 800; margin-bottom: 12px;
        }
        h1 {
          font-size: clamp(19px, 4.2vw, 27px);
          font-weight: 900; line-height: 1.25; margin: 0 0 8px 0; color: #ffffff;
        }
        p.sub {
          font-size: 13px; color: #a1a1aa; margin: 0 0 16px 0; line-height: 1.45;
        }
        .controls { width: 100%; display: none; flex-direction: column; gap: 10px; margin-top: 4px; }
        .gate-box {
          background: #09090b; border: 1px solid ${pal.border}; border-radius: 14px;
          padding: 11px 13px; text-align: left;
        }
        .gate-hdr {
          display: flex; justify-content: space-between; align-items: center;
          font-size: 10px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.06em;
          color: #a1a1aa; margin-bottom: 6px;
        }
        .phrase-target {
          font-family: monospace; font-size: 13px; font-weight: 700; color: #fde68a;
          background: rgba(245, 158, 11, 0.1); border: 1px dashed rgba(245, 158, 11, 0.35);
          padding: 7px 10px; border-radius: 9px; margin-bottom: 8px; user-select: none;
        }
        .input {
          width: 100%; padding: 11px 12px; border-radius: 10px;
          background: #18181b; border: 1px solid #3f3f46; color: #fff; font-size: 16px; outline: none;
        }
        .input:focus { border-color: ${pal.accent}; }
        .dur-row { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; align-items: center; font-size: 12px; color: #a1a1aa; margin: 4px 0; }
        .dur-btn {
          min-height: 36px; padding: 5px 13px; border-radius: 10px; border: 1px solid #3f3f46;
          background: #09090b; color: #e4e4e7; cursor: pointer; font-weight: 700; font-size: 12px;
        }
        .dur-btn.active { background: ${pal.accent}; border-color: ${pal.accent}; color: #09090b; }
        .btn-work {
          width: 100%; min-height: 48px; padding: 13px 18px; border-radius: 14px; border: none;
          background: ${pal.ok}; color: #052e16; font-weight: 800; font-size: 14px;
          cursor: pointer; transition: transform 0.1s;
        }
        .btn-enter {
          width: 100%; min-height: 44px; padding: 11px 16px; border-radius: 14px; border: 1px solid #27272a;
          background: #09090b; color: #d4d4d8; font-weight: 600; font-size: 13px; cursor: pointer;
        }
        .btn-enter:disabled { opacity: 0.35; cursor: not-allowed; }
        .footer-stats {
          margin-top: 14px; font-size: 11px; color: #71717a; font-family: monospace;
        }
      </style>
      <div class="overlay" id="ov-wrap">
        <canvas id="bg-canvas"></canvas>
        <div class="card-wrap">
          <div class="badge">
            <strong style="color:${pal.accent}">${isHardBlock ? '🔒 HARD BLOCK' : (isReintervention ? '⏰ ЛИМИТ ИСТЁК' : '🛑 СТОП-КРАН')}</strong>
            <span>•</span>
            <span>${domainKey}</span>
            <span>•</span>
            <span style="color:#fbbf24">Попытка #${attemptNumber} (${totalWaitSec}с)</span>
          </div>

          ${isHardBlock ? `
            <h1>Сайт заблокирован расписанием «${evalData.scheduleName || 'Фокус'}»</h1>
            <p class="sub">Сейчас действует режим строгой блокировки (Hard Block). Вход отключён до окончания рабочего окна.</p>
            <button class="btn-work" id="btn-quit-hard">💪 Закрыть вкладку и работать (${(evalData.stats && evalData.stats.savedMinutes) || 0} мин сбережено)</button>
          ` : `
            <div class="orb-wrap" id="orb-touch">
              <div class="ring" id="ring"></div>
              <div class="timer-circle" id="timer">${totalWaitSec}</div>
            </div>
            <div class="breath-lbl" id="breath">${s.touchHoldMode ? '👆 Удерживай круг пальцем...' : 'Глубокий вдох...'}</div>
            <h1>${roastText}</h1>
            <p class="sub">
              ${isReintervention
                ? 'Твой лимит времени на сайте истёк! Закроем вкладку или тебя снова засосало в думскроллинг?'
                : 'Пока идёт таймер, спроси себя: ты открыл этот сайт по делу или на чистом рефлексе?'}
            </p>

            <div class="controls" id="unlock-box">
              ${s.requireChallengePhrase ? `
                <div class="gate-box">
                  <div class="gate-hdr">
                    <span style="color:#fcd34d">⌨️ Шаг 1: Напечатай проверочную фразу (без Ctrl+V)</span>
                    <span id="chal-status">Ожидание</span>
                  </div>
                  <div class="phrase-target">${expectedChallenge}</div>
                  <input class="input" id="challenge-input" autocomplete="off" spellcheck="false" placeholder="Повтори фразу символ в символ..." />
                </div>
              ` : ''}

              ${s.requireIntention ? `
                <div class="gate-box">
                  <div class="gate-hdr">
                    <span>🎯 Шаг 2: Конкретная причина входа (мин. 8 симв.)</span>
                    <span id="int-status">0 / 8 симв.</span>
                  </div>
                  <input class="input" id="intention-input" autocomplete="off" placeholder="Зачем ты сюда идёшь? (не менее 8 символов)" />
                </div>
              ` : ''}

              <div class="dur-row">
                <span>Пустить на:</span>
                <button class="dur-btn" data-m="2">2 мин</button>
                <button class="dur-btn active" data-m="${s.reinterventionMinutes || 5}">${s.reinterventionMinutes || 5} мин</button>
                <button class="dur-btn" data-m="15">15 мин</button>
              </div>
              <button class="btn-work" id="btn-quit-2">💪 Закрыть вкладку и пойти работать</button>
              <button class="btn-enter" id="btn-proceed" ${(s.requireIntention || s.requireChallengePhrase) ? 'disabled' : ''}>
                Всё равно пустить на сайт (без перезагрузки) →
              </button>
            </div>

            <button class="btn-work" id="btn-quit-1">💪 Я передумал, закрыть вкладку</button>
          `}

          <div class="footer-stats">
            🌿 Отбито импульсов: <strong>${(evalData.stats && evalData.stats.savedCount) || 0}</strong> · Сбережено: <strong>${(evalData.stats && evalData.stats.savedMinutes) || 0} мин</strong>
          </div>
        </div>
      </div>
    `;

    document.documentElement.appendChild(hostEl);
    const stopCanvas = start60FpsCanvas(shadow.getElementById('bg-canvas'), s.canvasStyle || 'PULSE', pal);

    const quitHandler = () => {
      stopCanvas();
      clearInterval(mediaStopper);
      chrome.runtime.sendMessage({
        action: 'CLOSE_CURRENT_TAB',
        domain: domainKey,
        minutesSaved: s.avgSessionMinutes || 10
      }, () => {
        if (window.history.length > 1) window.history.back();
        else window.location.replace('about:blank');
      });
    };

    if (isHardBlock) {
      shadow.getElementById('btn-quit-hard').addEventListener('click', quitHandler);
      return;
    }

    let chosenMinutes = s.reinterventionMinutes || 5;
    let remaining = totalWaitSec;
    let isHolding = false;
    const timerEl = shadow.getElementById('timer');
    const ringEl = shadow.getElementById('ring');
    const breathEl = shadow.getElementById('breath');
    const orbTouch = shadow.getElementById('orb-touch');
    let inhale = true;

    const setHold = (val) => {
      isHolding = val;
      if (s.touchHoldMode) {
        ringEl.style.borderColor = val ? pal.ok : pal.accent;
      }
    };
    orbTouch.addEventListener('touchstart', (e) => { e.preventDefault(); setHold(true); }, { passive: false });
    orbTouch.addEventListener('touchend', () => setHold(false));
    orbTouch.addEventListener('mousedown', () => setHold(true));
    orbTouch.addEventListener('mouseup', () => setHold(false));

    const animBreath = () => {
      ringEl.style.transform = inhale ? 'scale(1.18)' : 'scale(0.85)';
      breathEl.textContent = inhale ? 'Глубокий вдох...' : 'Медленный выдох...';
      vibrateDevice(20);
      inhale = !inhale;
    };
    if (!s.touchHoldMode) animBreath();

    const cdInterval = setInterval(() => {
      if (s.touchHoldMode && !isHolding) {
        breathEl.textContent = '👆 Удерживай палец на круге...';
        return;
      }
      remaining--;
      if (remaining % 3 === 0) animBreath();
      if (remaining <= 0) {
        clearInterval(cdInterval);
        timerEl.textContent = '0';
        breathEl.textContent = 'Осознанный выбор 🌿';
        vibrateDevice([40, 60, 40]);
        shadow.getElementById('unlock-box').style.display = 'flex';
        shadow.getElementById('btn-quit-1').style.display = 'none';
      } else {
        timerEl.textContent = remaining;
      }
    }, 1000);

    shadow.querySelectorAll('.dur-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        shadow.querySelectorAll('.dur-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        chosenMinutes = parseInt(btn.getAttribute('data-m'), 10);
      });
    });

    const challengeInput = shadow.getElementById('challenge-input');
    const intentionInput = shadow.getElementById('intention-input');
    const proceedBtn = shadow.getElementById('btn-proceed');

    function validateGateInputs() {
      let chalOk = true;
      if (s.requireChallengePhrase && challengeInput) {
        const val = challengeInput.value.trim();
        chalOk = val.toLowerCase() === expectedChallenge.toLowerCase();
        const st = shadow.getElementById('chal-status');
        if (st) {
          st.textContent = chalOk ? '✓ Совпало' : `${val.length} / ${expectedChallenge.length}`;
          st.style.color = chalOk ? '#34d399' : '#fbbf24';
        }
      }
      let intOk = true;
      if (s.requireIntention && intentionInput) {
        const val = intentionInput.value.trim();
        const uniqueChars = new Set(val.toLowerCase().replace(/\s+/g, '')).size;
        intOk = val.length >= 8 && uniqueChars >= 3;
        const st = shadow.getElementById('int-status');
        if (st) {
          st.textContent = intOk ? `✓ ${val.length} симв.` : `${val.length} / 8 симв.`;
          st.style.color = intOk ? '#34d399' : '#fb7185';
        }
      }
      proceedBtn.disabled = !(chalOk && intOk);
    }

    if (challengeInput) {
      challengeInput.addEventListener('paste', (e) => e.preventDefault());
      challengeInput.addEventListener('drop', (e) => e.preventDefault());
      challengeInput.addEventListener('input', validateGateInputs);
    }
    if (intentionInput) {
      intentionInput.addEventListener('input', validateGateInputs);
    }

    shadow.getElementById('btn-quit-1').addEventListener('click', () => {
      clearInterval(cdInterval);
      quitHandler();
    });
    shadow.getElementById('btn-quit-2').addEventListener('click', () => {
      clearInterval(cdInterval);
      quitHandler();
    });

    proceedBtn.addEventListener('click', () => {
      clearInterval(cdInterval);
      clearInterval(mediaStopper);
      stopCanvas();
      const intentionText = intentionInput ? intentionInput.value.trim() : 'Осознанный визит';

      chrome.runtime.sendMessage({
        type: 'GRANT_SITE_PASS',
        domainKey,
        minutes: chosenMinutes,
        intention: intentionText
      }, (res) => {
        hostEl.remove();
        activeDomainKey = domainKey;
        activePassUntil = (res && res.until) ? res.until : (Date.now() + chosenMinutes * 60000);
        activeIntention = intentionText;
        startActiveHudAndReintervention(domainKey);
      });
    });
  }

  function startActiveHudAndReintervention(domainKey) {
    clearInterval(hudInterval);
    let hud = document.getElementById('onesec-gfw-hud');
    if (!hud) {
      hud = document.createElement('div');
      hud.id = 'onesec-gfw-hud';
      hud.style.cssText = 'position:fixed;top:calc(12px + env(safe-area-inset-top, 0px));right:12px;z-index:2147483646;background:rgba(9,9,11,0.92);border:1px solid #3f3f46;color:#fafafa;padding:7px 13px;border-radius:999px;font-family:monospace;font-size:12px;box-shadow:0 8px 20px rgba(0,0,0,0.45);backdrop-filter:blur(8px);touch-action:none;user-select:none;cursor:grab;';
      document.documentElement.appendChild(hud);

      let offsetY = 0;
      hud.addEventListener('touchstart', (e) => {
        offsetY = e.touches[0].clientY - hud.getBoundingClientRect().top;
      }, { passive: true });
      hud.addEventListener('touchmove', (e) => {
        const y = Math.max(8, Math.min(window.innerHeight - 48, e.touches[0].clientY - offsetY));
        hud.style.top = y + 'px';
      }, { passive: true });
    }

    const updateHud = () => {
      const leftMs = activePassUntil - Date.now();
      if (leftMs <= 0) {
        clearInterval(hudInterval);
        checkCurrentPage(true);
        return;
      }
      const totalSec = Math.ceil(leftMs / 1000);
      const m = String(Math.floor(totalSec / 60)).padStart(2, '0');
      const s = String(totalSec % 60).padStart(2, '0');
      hud.innerHTML = `<strong style="color:#fb7185">⏱ ${m}:${s}</strong> • <span style="color:#d4d4d8">${activeIntention}</span>`;
    };
    updateHud();
    hudInterval = setInterval(updateHud, 1000);
  }

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      hiddenAtTimestamp = Date.now();
    } else if (document.visibilityState === 'visible') {
      if (!hiddenAtTimestamp || !currentSettings.tabBlurResetSec) return;
      const awaySec = (Date.now() - hiddenAtTimestamp) / 1000;
      hiddenAtTimestamp = null;
      if (awaySec >= currentSettings.tabBlurResetSec) {
        checkCurrentPage(true);
      }
    }
  });

  setInterval(() => {
    if (location.href !== lastCheckedUrl) {
      lastCheckedUrl = location.href;
      checkCurrentPage(false);
    }
  }, 1200);

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => checkCurrentPage(false));
  } else {
    checkCurrentPage(false);
  }
})();

// ==UserScript==
// @name         wattim × One Sec × Go Fucking Work (Mobile & Desktop)
// @namespace    https://wattim-gfw.local
// @version      2.0.1
// @description  Дыхательный таймер + ввод конкретной проверочной фразы + 8 символов цели для мобильного Chrome, Safari и Firefox
// @match        *://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(function() {
  'use strict';
  const SETTINGS = {
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
  const cleanHost = (location.hostname || '').toLowerCase().replace(/^(www|m|mobile)\./, '');
  const matched = SETTINGS.domains.find(d => cleanHost === d || cleanHost.endsWith('.' + d));
  if (!matched) return;

  const STORAGE_KEY = '__wattim_gfw_pass_' + matched;
  const passUntil = Number(sessionStorage.getItem(STORAGE_KEY) || 0);
  if (passUntil > Date.now()) return;

  function launchMobileIntervention() {
    if (document.getElementById('onesec-userscript-root')) return;
    const phrase = SETTINGS.phrases[Math.floor(Math.random() * SETTINGS.phrases.length)];
    const expectedChallenge = (SETTINGS.challengePhrase || 'Я захожу на {domain} строго по делу').replace(/\{domain\}/g, matched).trim();

    const root = document.createElement('div');
    root.id = 'onesec-userscript-root';
    root.style.cssText = 'position:fixed;inset:0;width:100vw;height:100dvh;z-index:2147483647;background:#09090b;color:#fff;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:20px;text-align:center;font-family:sans-serif;overflow-y:auto;';
    root.innerHTML = `
      <div style="font-size:40px;font-weight:900;color:#fb7185;margin-bottom:10px;font-family:monospace" id="us-timer"></div>
      <h1 style="font-size:21px;font-weight:900;max-width:460px;margin:0 0 14px"></h1>
      <div id="us-gates" style="display:none;width:100%;max-width:400px;text-align:left;margin-bottom:12px">
        <div id="us-challenge-gate">
          <div id="us-target" style="font-size:11px;color:#fcd34d;margin-bottom:4px;font-weight:700"></div>
          <input id="us-chal" autocomplete="off" placeholder="Повтори фразу..." style="width:100%;padding:10px;border-radius:10px;background:#18181b;border:1px solid #3f3f46;color:#fff;font-size:16px;margin-bottom:8px"/>
        </div>
        <input id="us-int" autocomplete="off" placeholder="Зачем ты здесь? (мин. 8 симв.)" style="width:100%;padding:10px;border-radius:10px;background:#18181b;border:1px solid #3f3f46;color:#fff;font-size:16px;margin-bottom:8px"/>
      </div>
      <button id="us-quit" style="width:100%;max-width:400px;min-height:48px;padding:12px 24px;border-radius:12px;border:none;background:#10b981;color:#052e16;font-weight:800;font-size:15px;margin-bottom:10px">💪 Закрыть и пойти работать</button>
      <button id="us-enter" disabled style="display:none;width:100%;max-width:400px;min-height:44px;padding:10px 20px;border-radius:12px;border:1px solid #3f3f46;background:#18181b;color:#a1a1aa;opacity:0.4"></button>
    `;
    root.querySelector('#us-timer').textContent = SETTINGS.baseDelay;
    root.querySelector('h1').textContent = phrase;
    root.querySelector('#us-target').textContent = `Напечатай без ошибок: ${expectedChallenge}`;
    root.querySelector('#us-enter').textContent = `Пустить на ${SETTINGS.reinterventionMinutes} мин →`;
    if (!SETTINGS.requireChallengePhrase) root.querySelector('#us-challenge-gate').remove();
    if (!SETTINGS.requireIntention) root.querySelector('#us-int').remove();
    document.documentElement.appendChild(root);

    const chalIn = document.getElementById('us-chal');
    const intIn = document.getElementById('us-int');
    const enterBtn = document.getElementById('us-enter');

    function checkGates() {
      const cOk = !SETTINGS.requireChallengePhrase || (chalIn && chalIn.value.trim().toLowerCase() === expectedChallenge.toLowerCase());
      const iOk = !SETTINGS.requireIntention || (intIn && intIn.value.trim().length >= 8);
      enterBtn.disabled = !(cOk && iOk);
      enterBtn.style.opacity = enterBtn.disabled ? '0.4' : '1';
    }
    if (chalIn) { chalIn.onpaste = e => e.preventDefault(); chalIn.oninput = checkGates; }
    if (intIn) intIn.oninput = checkGates;
    checkGates();

    let left = SETTINGS.baseDelay;
    const t = setInterval(() => {
      left--;
      const el = document.getElementById('us-timer');
      if (el) el.textContent = Math.max(0, left);
      if (left <= 0) {
        clearInterval(t);
        document.getElementById('us-gates').style.display = 'block';
        enterBtn.style.display = 'inline-block';
      }
    }, 1000);

    document.getElementById('us-quit').onclick = () => location.replace('about:blank');
    enterBtn.onclick = () => {
      if (enterBtn.disabled) return;
      sessionStorage.setItem(STORAGE_KEY, String(Date.now() + SETTINGS.reinterventionMinutes * 60000));
      root.remove();
      setTimeout(launchMobileIntervention, SETTINGS.reinterventionMinutes * 60000);
    };
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', launchMobileIntervention);
  } else {
    launchMobileIntervention();
  }
})();

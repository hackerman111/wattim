/**
 * wattim Browser Extension - Intervention Page Controller
 * Handles breathing lifecycle, animations, timer, tab closing, and pass grants.
 */

import { BreathAnimationController } from './animations.js';
import { THEMES, applyTheme } from '../common/themes.js';
import { getStorage } from '../common/storage.js';

// DOM Elements
const canvas = document.getElementById('breathCanvas');
const targetBadge = document.getElementById('targetBadge');
const phaseLabel = document.getElementById('phaseLabel');
const timerDisplay = document.getElementById('timerDisplay');
const quoteText = document.getElementById('quoteText');
const btnExit = document.getElementById('btnExit');
const btnContinue = document.getElementById('btnContinue');
const backoffBadge = document.getElementById('backoffBadge');
const backoffPercent = document.getElementById('backoffPercent');
const backoffAttempt = document.getElementById('backoffAttempt');
const cardMain = document.getElementById('cardMain');
const cardBlocked = document.getElementById('cardBlocked');
const btnExitBlocked = document.getElementById('btnExitBlocked');
const statMinutes = document.getElementById('statMinutes');
const statImpulses = document.getElementById('statImpulses');
const emergencyModal = document.getElementById('emergencyModal');
const btnEmergencyTrigger = document.getElementById('btnEmergencyTrigger');
const btnModalClose = document.getElementById('btnModalClose');
const btnEmergencyOnce = document.getElementById('btnEmergencyOnce');
const btnEmergencyPauseDomain = document.getElementById('btnEmergencyPauseDomain');
const btnEmergencyCancel = document.getElementById('btnEmergencyCancel');

// Query Parameters
const urlParams = new URLSearchParams(window.location.search);
const targetUrl = urlParams.get('target') || 'https://google.com';
const domain = urlParams.get('domain') || 'target site';
const delaySeconds = Math.max(1, parseInt(urlParams.get('delay'), 10) || 10);
const attempts = parseInt(urlParams.get('attempts'), 10) || 0;
const mode = urlParams.get('mode') || 'normal';
const isReintervention = urlParams.get('reintervention') === 'true';

let animationController = null;
let currentStorage = null;

/**
 * Safely closes the current tab or falls back to window.close.
 */
async function closeCurrentTab() {
  try {
    if (typeof chrome !== 'undefined' && chrome.tabs && chrome.tabs.getCurrent) {
      const tab = await new Promise(res => chrome.tabs.getCurrent(res));
      if (tab && tab.id) {
        chrome.tabs.remove(tab.id);
        return;
      }
    }
  } catch (err) {
    console.warn('[wattim] Unable to close tab via tabs API:', err);
  }
  window.close();
}

/**
 * Logs a prevented impulse and saved minutes to extension storage.
 */
function recordSavedImpulse() {
  if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
    const avgMinutes = (currentStorage && currentStorage.settings && currentStorage.settings.avgSessionMinutes) || 10;
    chrome.runtime.sendMessage({
      type: 'LOG_IMPULSE',
      domain,
      minutesSaved: avgMinutes
    });
  }
}

/**
 * Grants a temporary pass and navigates to the target URL.
 */
async function grantPassAndProceed(durationMinutes = 15) {
  if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
    await new Promise(resolve => {
      chrome.runtime.sendMessage(
        { type: 'GRANT_PASS', domain, durationMinutes },
        resolve
      );
    });
  }
  // Replace current location so user cannot simply go "Back" to breath page
  window.location.replace(targetUrl);
}

/**
 * Initializes the intervention screen.
 */
async function init() {
  currentStorage = await getStorage();
  const themeId = (currentStorage.settings && currentStorage.settings.theme) || 'nord';
  const themeConfig = applyTheme(themeId);

  // Update target badge
  targetBadge.textContent = domain;

  // Update stats in banner
  if (currentStorage.stats) {
    statMinutes.textContent = currentStorage.stats.savedMinutes || 0;
    statImpulses.textContent = currentStorage.stats.savedImpulses || 0;
  }

  // Handle Hard Block Mode
  if (mode === 'hard_block') {
    cardMain.style.display = 'none';
    cardBlocked.style.display = 'flex';
    document.getElementById('terminalPrompt').textContent = '$ wattim --hard-block';
    btnExitBlocked.addEventListener('click', () => {
      recordSavedImpulse();
      closeCurrentTab();
    });
    return;
  }

  // Backoff notification
  if (attempts > 0 && currentStorage.backoff && currentStorage.backoff.enabled) {
    backoffBadge.style.display = 'inline-block';
    backoffPercent.textContent = currentStorage.backoff.growthPercent || 20;
    backoffAttempt.textContent = attempts + 1;
  }

  // Pick random mindful phrase
  const phrases = (currentStorage.settings && currentStorage.settings.phrases) || [];
  if (phrases.length > 0) {
    const randomIndex = Math.floor(Math.random() * phrases.length);
    quoteText.textContent = `«${phrases[randomIndex]}»`;
  }

  if (isReintervention) {
    quoteText.textContent = '«Вы уже провели на сайте достаточно времени. Сделайте паузу.»';
  }

  // Initialize Canvas Animation
  const animStyle = (currentStorage.settings && currentStorage.settings.animation) || 'PULSE';
  animationController = new BreathAnimationController(canvas, {
    durationSeconds: delaySeconds,
    style: animStyle,
    colors: themeConfig.colors
  });

  animationController.onPhaseChange = (state) => {
    phaseLabel.textContent = state.label;
    timerDisplay.textContent = `${state.remainingSeconds}с`;
  };

  animationController.onComplete = () => {
    phaseLabel.textContent = 'Осознанность 🌿';
    timerDisplay.textContent = 'Готово';
    btnContinue.style.display = 'flex';
    btnContinue.focus();
  };

  animationController.start();

  // Button Listeners
  btnExit.addEventListener('click', () => {
    if (animationController) animationController.stop();
    recordSavedImpulse();
    closeCurrentTab();
  });

  btnContinue.addEventListener('click', () => {
    grantPassAndProceed(15);
  });

  // Emergency Modal Listeners
  btnEmergencyTrigger.addEventListener('click', () => {
    emergencyModal.style.display = 'flex';
  });

  btnModalClose.addEventListener('click', () => {
    emergencyModal.style.display = 'none';
  });

  btnEmergencyCancel.addEventListener('click', () => {
    emergencyModal.style.display = 'none';
  });

  btnEmergencyOnce.addEventListener('click', () => {
    grantPassAndProceed(15);
  });

  btnEmergencyPauseDomain.addEventListener('click', () => {
    grantPassAndProceed(15);
  });
}

document.addEventListener('DOMContentLoaded', init);

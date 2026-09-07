/**
 * wattim Browser Extension - Popup Menu Controller
 */

import { getStorage, setStorage, normalizeDomain, DEFAULT_STORAGE } from '../common/storage.js';
import { applyTheme } from '../common/themes.js';

// DOM Elements
const statusDot = document.getElementById('statusDot');
const statusTitle = document.getElementById('statusTitle');
const statusDesc = document.getElementById('statusDesc');
const activeTargetsCount = document.getElementById('activeTargetsCount');
const pauseTimerBox = document.getElementById('pauseTimerBox');
const pauseCountdown = document.getElementById('pauseCountdown');
const btnResume = document.getElementById('btnResume');
const currentDomainEl = document.getElementById('currentDomain');
const btnToggleSite = document.getElementById('btnToggleSite');
const statImpulses = document.getElementById('statImpulses');
const statMinutes = document.getElementById('statMinutes');
const btnOpenOptions = document.getElementById('btnOpenOptions');
const pauseButtons = document.querySelectorAll('.btn-pause');

let currentTabDomain = '';
let currentStorageData = null;
let countdownInterval = null;

/**
 * Formats milliseconds into MM:SS string.
 */
function formatRemainingTime(ms) {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

/**
 * Loads current status from background service worker.
 */
function refreshStatus() {
  if (typeof chrome === 'undefined' || !chrome.runtime || !chrome.runtime.sendMessage) return;

  chrome.runtime.sendMessage({ type: 'GET_STATUS' }, (status) => {
    if (chrome.runtime.lastError || !status) return;

    activeTargetsCount.textContent = status.activeTargetsCount || 0;
    statImpulses.textContent = status.savedImpulses || 0;
    statMinutes.textContent = status.savedMinutes || 0;

    if (countdownInterval) {
      clearInterval(countdownInterval);
      countdownInterval = null;
    }

    if (status.isPaused && status.remainingMs > 0) {
      statusDot.className = 'status-dot dot-paused';
      statusTitle.textContent = 'ЗАЩИТА НА ПАУЗЕ';
      pauseTimerBox.style.display = 'flex';
      statusDesc.style.display = 'none';

      let remaining = status.remainingMs;
      pauseCountdown.textContent = formatRemainingTime(remaining);

      countdownInterval = setInterval(() => {
        remaining -= 1000;
        if (remaining <= 0) {
          clearInterval(countdownInterval);
          refreshStatus();
        } else {
          pauseCountdown.textContent = formatRemainingTime(remaining);
        }
      }, 1000);
    } else {
      statusDot.className = 'status-dot dot-active';
      statusTitle.textContent = 'ЗАЩИТА АКТИВНА';
      pauseTimerBox.style.display = 'none';
      statusDesc.style.display = 'block';
    }
  });
}

/**
 * Inspects currently open tab to provide quick-add or toggle.
 */
async function inspectCurrentTab() {
  if (typeof chrome === 'undefined' || !chrome.tabs || !chrome.tabs.query) {
    currentDomainEl.textContent = 'недоступно';
    btnToggleSite.style.display = 'none';
    return;
  }

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab || !tab.url || tab.url.startsWith('chrome://') || tab.url.startsWith('about:')) {
    currentDomainEl.textContent = 'системная страница';
    btnToggleSite.style.display = 'none';
    return;
  }

  const domain = normalizeDomain(tab.url);
  if (!domain) {
    currentDomainEl.textContent = 'неизвестный адрес';
    btnToggleSite.style.display = 'none';
    return;
  }

  currentTabDomain = domain;
  currentDomainEl.textContent = domain;

  updateCurrentSiteButtonState();
}

/**
 * Updates the quick-protect button text depending on whether domain is protected.
 */
function updateCurrentSiteButtonState() {
  if (!currentStorageData || !currentTabDomain) return;

  const existing = currentStorageData.targets.find(t => t.domain === currentTabDomain);

  if (existing && existing.enabled) {
    btnToggleSite.textContent = '✓ Защищен (нажмите для снятия)';
    btnToggleSite.classList.add('is-protected');
  } else if (existing && !existing.enabled) {
    btnToggleSite.textContent = 'Включить защиту';
    btnToggleSite.classList.remove('is-protected');
  } else {
    btnToggleSite.textContent = `+ Защитить ${currentTabDomain}`;
    btnToggleSite.classList.remove('is-protected');
  }
}

/**
 * Toggles protection for the current tab domain.
 */
async function toggleCurrentTabSite() {
  if (!currentTabDomain || !currentStorageData) return;

  const targets = [...currentStorageData.targets];
  const index = targets.findIndex(t => t.domain === currentTabDomain);

  if (index >= 0) {
    // Toggle existing
    targets[index].enabled = !targets[index].enabled;
  } else {
    // Add new
    targets.push({
      id: 't_' + Date.now(),
      domain: currentTabDomain,
      enabled: true
    });
  }

  await setStorage({ targets });
  currentStorageData.targets = targets;
  updateCurrentSiteButtonState();
  refreshStatus();
}

/**
 * Sets global protection pause.
 */
function setGlobalPause(durationMinutes) {
  if (typeof chrome === 'undefined' || !chrome.runtime || !chrome.runtime.sendMessage) return;

  chrome.runtime.sendMessage({ type: 'SET_GLOBAL_PAUSE', durationMinutes }, () => {
    refreshStatus();
  });
}

/**
 * Initializes the popup.
 */
async function init() {
  currentStorageData = await getStorage();
  const theme = (currentStorageData.settings && currentStorageData.settings.theme) || 'nord';
  applyTheme(theme);

  refreshStatus();
  await inspectCurrentTab();

  // Pause duration buttons
  pauseButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const minutes = parseInt(btn.getAttribute('data-duration'), 10);
      setGlobalPause(minutes);
    });
  });

  // Resume button
  btnResume.addEventListener('click', () => {
    setGlobalPause(0);
  });

  // Toggle current tab site
  btnToggleSite.addEventListener('click', () => {
    toggleCurrentTabSite();
  });

  // Open Options page
  btnOpenOptions.addEventListener('click', () => {
    if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.openOptionsPage) {
      chrome.runtime.openOptionsPage();
    } else {
      window.open('../options/options.html');
    }
  });
}

document.addEventListener('DOMContentLoaded', init);

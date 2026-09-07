/**
 * wattim Browser Extension - Options Dashboard Controller
 * Complete settings dashboard matching Android app architecture.
 */

import { getStorage, setStorage, normalizeDomain } from '../common/storage.js';
import { THEMES, applyTheme } from '../common/themes.js';
import { BreathAnimationController } from '../intervention/animations.js';
import { calculateBackoffDelay } from '../background/rule-engine.js';

let storageData = null;
let previewController = null;

// DOM Elements
const navItems = document.querySelectorAll('.nav-item');
const tabPanes = document.querySelectorAll('.tab-pane');
const toastMessage = document.getElementById('toastMessage');

// Tab 1: Targets
const inputNewDomain = document.getElementById('inputNewDomain');
const btnAddDomain = document.getElementById('btnAddDomain');
const targetsList = document.getElementById('targetsList');
const targetsCountBadge = document.getElementById('targetsCountBadge');
const presetChips = document.querySelectorAll('.chip');

// Tab 2: Breathing
const sliderDelay = document.getElementById('sliderDelay');
const valDelay = document.getElementById('valDelay');
const animRadios = document.querySelectorAll('input[name="animStyle"]');
const sliderReintervention = document.getElementById('sliderReintervention');
const valReintervention = document.getElementById('valReintervention');
const previewCanvas = document.getElementById('previewCanvas');
const previewPhase = document.getElementById('previewPhase');
const inputNewPhrase = document.getElementById('inputNewPhrase');
const btnAddPhrase = document.getElementById('btnAddPhrase');
const phrasesList = document.getElementById('phrasesList');

// Tab 3: Backoff
const checkBackoffEnabled = document.getElementById('checkBackoffEnabled');
const backoffControlsArea = document.getElementById('backoffControlsArea');
const sliderGrowth = document.getElementById('sliderGrowth');
const valGrowth = document.getElementById('valGrowth');
const sliderWindow = document.getElementById('sliderWindow');
const valWindow = document.getElementById('valWindow');
const backoffTableBody = document.getElementById('backoffTableBody');

// Tab 4: Schedules
const schedName = document.getElementById('schedName');
const schedMode = document.getElementById('schedMode');
const schedCustomDelayGroup = document.getElementById('schedCustomDelayGroup');
const schedDelay = document.getElementById('schedDelay');
const schedStart = document.getElementById('schedStart');
const schedEnd = document.getElementById('schedEnd');
const schedDaysSelector = document.getElementById('schedDaysSelector');
const btnSaveSchedule = document.getElementById('btnSaveSchedule');
const schedulesList = document.getElementById('schedulesList');

// Tab 5: Themes
const themesGrid = document.getElementById('themesGrid');

// Tab 6: Stats
const kpiImpulses = document.getElementById('kpiImpulses');
const kpiMinutes = document.getElementById('kpiMinutes');
const kpiHours = document.getElementById('kpiHours');
const sliderAvgSession = document.getElementById('sliderAvgSession');
const valAvgSession = document.getElementById('valAvgSession');
const btnResetStats = document.getElementById('btnResetStats');

/**
 * Displays a non-intrusive toast notification.
 */
function showToast(text = 'Изменения сохранены') {
  toastMessage.textContent = text;
  toastMessage.classList.add('show');
  setTimeout(() => {
    toastMessage.classList.remove('show');
  }, 2200);
}

/**
 * Saves current state to storage and shows toast.
 */
async function saveChanges(toastText) {
  await setStorage(storageData);
  if (toastText) showToast(toastText);
}

/**
 * Tab Navigation.
 */
function initNavigation() {
  navItems.forEach(item => {
    item.addEventListener('click', () => {
      const targetTab = item.getAttribute('data-tab');
      navItems.forEach(i => i.classList.remove('active'));
      tabPanes.forEach(p => p.classList.remove('active'));

      item.classList.add('active');
      const pane = document.getElementById(targetTab);
      if (pane) pane.classList.add('active');
    });
  });
}

/**
 * Tab 1: Targets List Rendering & Handlers.
 */
function renderTargets() {
  targetsList.innerHTML = '';
  const targets = storageData.targets || [];
  targetsCountBadge.textContent = `${targets.length} сайтов`;

  if (targets.length === 0) {
    targetsList.innerHTML = '<div style="color: var(--text-muted); font-size: 0.85rem; padding: 12px;">Список пуст. Добавьте первый сайт выше.</div>';
    return;
  }

  targets.forEach((target, index) => {
    const item = document.createElement('div');
    item.className = `target-item ${target.enabled ? '' : 'disabled'}`;

    const label = document.createElement('span');
    label.className = 'target-domain-label';
    label.textContent = target.domain;

    const actions = document.createElement('div');
    actions.className = 'target-actions';

    // Toggle switch
    const toggleLabel = document.createElement('label');
    toggleLabel.className = 'toggle-switch';
    const toggleInput = document.createElement('input');
    toggleInput.type = 'checkbox';
    toggleInput.checked = target.enabled;
    toggleInput.addEventListener('change', async () => {
      target.enabled = toggleInput.checked;
      item.classList.toggle('disabled', !target.enabled);
      await saveChanges();
    });
    const toggleSlider = document.createElement('span');
    toggleSlider.className = 'toggle-slider';
    toggleLabel.appendChild(toggleInput);
    toggleLabel.appendChild(toggleSlider);

    // Delete button
    const btnDelete = document.createElement('button');
    btnDelete.className = 'btn-icon-delete';
    btnDelete.innerHTML = '🗑️';
    btnDelete.title = 'Удалить сайт';
    btnDelete.addEventListener('click', async () => {
      storageData.targets.splice(index, 1);
      renderTargets();
      await saveChanges('Сайт удален');
    });

    actions.appendChild(toggleLabel);
    actions.appendChild(btnDelete);

    item.appendChild(label);
    item.appendChild(actions);
    targetsList.appendChild(item);
  });
}

function addTargetDomain(rawDomain) {
  const domain = normalizeDomain(rawDomain);
  if (!domain) return;

  const exists = storageData.targets.some(t => t.domain === domain);
  if (exists) {
    showToast('Этот сайт уже добавлен');
    return;
  }

  storageData.targets.unshift({
    id: 't_' + Date.now(),
    domain,
    enabled: true
  });

  inputNewDomain.value = '';
  renderTargets();
  saveChanges(`Добавлен: ${domain}`);
}

/**
 * Tab 2: Breathing Settings & Live Preview.
 */
function initBreathingTab() {
  const settings = storageData.settings;
  sliderDelay.value = settings.baseDelaySeconds || 10;
  valDelay.textContent = sliderDelay.value;

  sliderReintervention.value = settings.reInterventionMinutes || 15;
  valReintervention.textContent = sliderReintervention.value;

  // Selected animation radio
  animRadios.forEach(radio => {
    if (radio.value === settings.animation) radio.checked = true;
    radio.addEventListener('change', () => {
      if (radio.checked) {
        settings.animation = radio.value;
        if (previewController) previewController.setStyle(radio.value);
        saveChanges();
      }
    });
  });

  sliderDelay.addEventListener('input', () => {
    valDelay.textContent = sliderDelay.value;
  });

  sliderDelay.addEventListener('change', () => {
    settings.baseDelaySeconds = parseInt(sliderDelay.value, 10);
    renderBackoffTable();
    saveChanges();
  });

  sliderReintervention.addEventListener('input', () => {
    valReintervention.textContent = sliderReintervention.value;
  });

  sliderReintervention.addEventListener('change', () => {
    settings.reInterventionMinutes = parseInt(sliderReintervention.value, 10);
    saveChanges();
  });

  // Phrases
  renderPhrases();
  btnAddPhrase.addEventListener('click', () => {
    const text = inputNewPhrase.value.trim();
    if (!text) return;
    settings.phrases.push(text);
    inputNewPhrase.value = '';
    renderPhrases();
    saveChanges('Фраза добавлена');
  });

  // Start live canvas preview
  startLivePreview();
}

function renderPhrases() {
  phrasesList.innerHTML = '';
  const phrases = storageData.settings.phrases || [];
  phrases.forEach((phrase, index) => {
    const li = document.createElement('li');
    li.className = 'phrase-item';
    li.innerHTML = `<span>«${phrase}»</span>`;

    const btnDel = document.createElement('button');
    btnDel.className = 'btn-icon-delete';
    btnDel.innerHTML = '🗑️';
    btnDel.title = 'Удалить';
    btnDel.addEventListener('click', () => {
      phrases.splice(index, 1);
      renderPhrases();
      saveChanges();
    });

    li.appendChild(btnDel);
    phrasesList.appendChild(li);
  });
}

function startLivePreview() {
  if (!previewCanvas) return;
  const theme = storageData.settings.theme || 'nord';
  const themeConfig = THEMES[theme] || THEMES.nord;

  if (previewController) {
    previewController.destroy();
  }

  // Set looping preview with 8 seconds loop
  previewController = new BreathAnimationController(previewCanvas, {
    durationSeconds: 8,
    style: storageData.settings.animation || 'PULSE',
    colors: themeConfig.colors
  });

  previewController.onPhaseChange = (state) => {
    previewPhase.textContent = `Фаза: ${state.label}`;
  };

  previewController.onComplete = () => {
    // Loop preview continuously
    previewController.start();
  };

  previewController.start();
}

/**
 * Tab 3: Backoff Table & Handlers.
 */
function initBackoffTab() {
  const backoff = storageData.backoff;
  checkBackoffEnabled.checked = backoff.enabled;
  sliderGrowth.value = backoff.growthPercent || 20;
  valGrowth.textContent = sliderGrowth.value;
  sliderWindow.value = backoff.windowMinutes || 60;
  valWindow.textContent = sliderWindow.value;

  checkBackoffEnabled.addEventListener('change', () => {
    backoff.enabled = checkBackoffEnabled.checked;
    backoffControlsArea.style.opacity = backoff.enabled ? '1' : '0.5';
    renderBackoffTable();
    saveChanges();
  });

  sliderGrowth.addEventListener('input', () => {
    valGrowth.textContent = sliderGrowth.value;
    backoff.growthPercent = parseInt(sliderGrowth.value, 10);
    renderBackoffTable();
  });

  sliderGrowth.addEventListener('change', () => {
    saveChanges();
  });

  sliderWindow.addEventListener('input', () => {
    valWindow.textContent = sliderWindow.value;
    backoff.windowMinutes = parseInt(sliderWindow.value, 10);
  });

  sliderWindow.addEventListener('change', () => {
    saveChanges();
  });

  renderBackoffTable();
}

function renderBackoffTable() {
  backoffTableBody.innerHTML = '';
  const base = storageData.settings.baseDelaySeconds || 10;
  const growth = storageData.backoff.enabled ? (storageData.backoff.growthPercent || 20) : 0;
  const maxDelay = storageData.settings.maxDelaySeconds || 120;

  for (let i = 0; i < 10; i++) {
    const attempt = i + 1;
    const delay = calculateBackoffDelay(base, growth, i, maxDelay);

    let difficulty = 'Легко';
    let diffColor = 'var(--success)';
    if (delay >= 60) {
      difficulty = 'Хардкор';
      diffColor = 'var(--error)';
    } else if (delay >= 25) {
      difficulty = 'Ощутимо';
      diffColor = 'var(--accent)';
    }

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><strong>#${attempt}</strong></td>
      <td style="color: var(--text-muted);">${base} × (1 + ${growth}%)<sup>${i}</sup></td>
      <td><strong style="color: var(--accent); font-size: 1.05rem;">${delay} сек</strong></td>
      <td><span style="color: ${diffColor}; font-weight: 600;">${difficulty}</span></td>
    `;
    backoffTableBody.appendChild(tr);
  }
}

/**
 * Tab 4: Schedules Handlers.
 */
function initSchedulesTab() {
  schedMode.addEventListener('change', () => {
    schedCustomDelayGroup.style.display = schedMode.value === 'custom_intervention' ? 'block' : 'none';
  });

  btnSaveSchedule.addEventListener('click', () => {
    const name = schedName.value.trim() || 'Расписание';
    const mode = schedMode.value;
    const customDelaySeconds = parseInt(schedDelay.value, 10) || 30;
    const start = schedStart.value;
    const end = schedEnd.value;

    const days = [];
    schedDaysSelector.querySelectorAll('input[type="checkbox"]:checked').forEach(cb => {
      days.push(parseInt(cb.value, 10));
    });

    if (days.length === 0) {
      alert('Выберите хотя бы один день недели!');
      return;
    }

    storageData.schedules.push({
      id: 's_' + Date.now(),
      name,
      enabled: true,
      mode,
      customDelaySeconds,
      days,
      start,
      end,
      targetDomain: 'all'
    });

    schedName.value = '';
    renderSchedules();
    saveChanges('Расписание добавлено');
  });

  renderSchedules();
}

function renderSchedules() {
  schedulesList.innerHTML = '';
  const schedules = storageData.schedules || [];

  if (schedules.length === 0) {
    schedulesList.innerHTML = '<div style="color: var(--text-muted); font-size: 0.85rem;">Расписаний пока нет. Создайте правило выше.</div>';
    return;
  }

  const dayNames = ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'];

  schedules.forEach((sched, index) => {
    const card = document.createElement('div');
    card.className = 'schedule-card';

    const info = document.createElement('div');
    info.className = 'schedule-info';

    const titleRow = document.createElement('div');
    titleRow.className = 'schedule-title-row';

    const title = document.createElement('span');
    title.className = 'schedule-name';
    title.textContent = sched.name;

    const badge = document.createElement('span');
    badge.className = 'schedule-badge';
    badge.textContent = sched.mode === 'hard_block' ? '🔒 Hard Block' : `🧘 Пауза ${sched.customDelaySeconds}с`;

    titleRow.appendChild(title);
    titleRow.appendChild(badge);

    const timing = document.createElement('div');
    timing.className = 'schedule-timing';
    const daysStr = sched.days.map(d => dayNames[d]).join(', ');
    timing.textContent = `⏰ ${sched.start} — ${sched.end} (${daysStr})`;

    info.appendChild(titleRow);
    info.appendChild(timing);

    const actions = document.createElement('div');
    actions.className = 'target-actions';

    // Toggle
    const toggleLabel = document.createElement('label');
    toggleLabel.className = 'toggle-switch';
    const toggleInput = document.createElement('input');
    toggleInput.type = 'checkbox';
    toggleInput.checked = sched.enabled;
    toggleInput.addEventListener('change', async () => {
      sched.enabled = toggleInput.checked;
      await saveChanges();
    });
    const toggleSlider = document.createElement('span');
    toggleSlider.className = 'toggle-slider';
    toggleLabel.appendChild(toggleInput);
    toggleLabel.appendChild(toggleSlider);

    // Delete
    const btnDelete = document.createElement('button');
    btnDelete.className = 'btn-icon-delete';
    btnDelete.innerHTML = '🗑️';
    btnDelete.title = 'Удалить расписание';
    btnDelete.addEventListener('click', async () => {
      storageData.schedules.splice(index, 1);
      renderSchedules();
      await saveChanges('Расписание удалено');
    });

    actions.appendChild(toggleLabel);
    actions.appendChild(btnDelete);

    card.appendChild(info);
    card.appendChild(actions);
    schedulesList.appendChild(card);
  });
}

/**
 * Tab 5: Themes Grid Handlers.
 */
function initThemesTab() {
  themesGrid.innerHTML = '';
  const currentTheme = storageData.settings.theme || 'nord';

  Object.values(THEMES).forEach(theme => {
    const card = document.createElement('div');
    card.className = `theme-card ${theme.id === currentTheme ? 'active' : ''}`;

    const header = document.createElement('div');
    header.className = 'theme-header';
    header.innerHTML = `<span>${theme.icon}</span><span>${theme.name}</span>`;

    const palette = document.createElement('div');
    palette.className = 'theme-palette-preview';
    palette.innerHTML = `
      <div class="palette-swatch" style="background: ${theme.colors.bgPrimary}"></div>
      <div class="palette-swatch" style="background: ${theme.colors.bgSurface}"></div>
      <div class="palette-swatch" style="background: ${theme.colors.accent}"></div>
      <div class="palette-swatch" style="background: ${theme.colors.accentSecondary}"></div>
    `;

    const desc = document.createElement('div');
    desc.className = 'theme-desc';
    desc.textContent = theme.description;

    card.appendChild(header);
    card.appendChild(palette);
    card.appendChild(desc);

    card.addEventListener('click', () => {
      document.querySelectorAll('.theme-card').forEach(c => c.classList.remove('active'));
      card.classList.add('active');
      storageData.settings.theme = theme.id;
      applyTheme(theme.id);
      if (previewController) {
        previewController.setColors(theme.colors);
      }
      saveChanges(`Тема изменена: ${theme.name}`);
    });

    themesGrid.appendChild(card);
  });
}

/**
 * Tab 6: Stats Handlers.
 */
function initStatsTab() {
  const stats = storageData.stats || { savedImpulses: 0, savedMinutes: 0 };
  kpiImpulses.textContent = stats.savedImpulses || 0;
  kpiMinutes.textContent = stats.savedMinutes || 0;
  kpiHours.textContent = ((stats.savedMinutes || 0) / 60).toFixed(1);

  sliderAvgSession.value = storageData.settings.avgSessionMinutes || 10;
  valAvgSession.textContent = sliderAvgSession.value;

  sliderAvgSession.addEventListener('input', () => {
    valAvgSession.textContent = sliderAvgSession.value;
  });

  sliderAvgSession.addEventListener('change', () => {
    storageData.settings.avgSessionMinutes = parseInt(sliderAvgSession.value, 10);
    saveChanges();
  });

  btnResetStats.addEventListener('click', async () => {
    if (confirm('Вы уверены, что хотите сбросить всю сохраненную статистику?')) {
      storageData.stats = { savedImpulses: 0, savedMinutes: 0, history: [] };
      kpiImpulses.textContent = '0';
      kpiMinutes.textContent = '0';
      kpiHours.textContent = '0.0';
      await saveChanges('Статистика сброшена');
    }
  });
}

/**
 * Initializes the entire options dashboard.
 */
async function init() {
  storageData = await getStorage();
  applyTheme(storageData.settings.theme || 'nord');

  initNavigation();

  // Targets
  renderTargets();
  btnAddDomain.addEventListener('click', () => {
    addTargetDomain(inputNewDomain.value);
  });
  inputNewDomain.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') addTargetDomain(inputNewDomain.value);
  });
  presetChips.forEach(chip => {
    chip.addEventListener('click', () => {
      addTargetDomain(chip.getAttribute('data-domain'));
    });
  });

  // Breathing
  initBreathingTab();

  // Backoff
  initBackoffTab();

  // Schedules
  initSchedulesTab();

  // Themes
  initThemesTab();

  // Stats
  initStatsTab();
}

document.addEventListener('DOMContentLoaded', init);

// wattim × One Sec × GFW — Popup Controller
function cleanHost(urlStr) {
  try {
    return new URL(urlStr).hostname.toLowerCase().replace(/^(www|m|mobile)\./, '');
  } catch { return ''; }
}

document.addEventListener('DOMContentLoaded', () => {
  let activeHost = '';

  function renderPopup() {
    chrome.storage.local.get(['settings', 'stats', 'globalPauseUntil'], (data) => {
      const s = data.settings || {};
      const st = data.stats || { savedCount: 0, enteredCount: 0, savedMinutes: 0 };
      const pausedUntil = Number(data.globalPauseUntil) || 0;

      document.getElementById('savedCount').textContent = st.savedCount || 0;
      document.getElementById('savedMinutes').textContent = (st.savedMinutes || 0) + 'м';
      document.getElementById('enteredCount').textContent = st.enteredCount || 0;

      const badge = document.getElementById('statusBadge');
      if (pausedUntil > Date.now()) {
        const leftMin = Math.ceil((pausedUntil - Date.now()) / 60000);
        badge.textContent = 'ПАУЗА ' + leftMin + 'М';
        badge.style.color = '#fbbf24';
      } else {
        badge.textContent = 'АКТИВЕН';
        badge.style.color = '#34d399';
      }

      if (activeHost) {
        const isTracked = (s.domains || []).includes(activeHost);
        const btn = document.getElementById('btnToggleDomain');
        btn.textContent = isTracked ? '✓ Под надзором (Снять)' : '+ Под надзор';
        btn.style.borderColor = isTracked ? '#10b981' : '#3f3f46';
      }
    });
  }

  chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
    const tab = tabs && tabs[0];
    activeHost = tab && tab.url ? cleanHost(tab.url) : '';
    document.getElementById('currentHost').textContent = activeHost || 'системная вкладка';
    if (!activeHost) document.getElementById('btnToggleDomain').style.display = 'none';
    renderPopup();
  });

  document.getElementById('btnToggleDomain').addEventListener('click', () => {
    if (!activeHost) return;
    chrome.storage.local.get(['settings'], (data) => {
      const s = data.settings || {};
      const list = s.domains || [];
      if (list.includes(activeHost)) {
        s.domains = list.filter(d => d !== activeHost);
      } else {
        s.domains = [activeHost, ...list];
      }
      chrome.storage.local.set({ settings: s }, renderPopup);
    });
  });

  document.querySelectorAll('.pause-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const mins = parseInt(btn.getAttribute('data-m'), 10);
      chrome.runtime.sendMessage({ type: 'SET_GLOBAL_PAUSE', durationMinutes: mins }, renderPopup);
    });
  });

  document.getElementById('openOptionsBtn').addEventListener('click', () => {
    if (chrome.runtime.openOptionsPage) chrome.runtime.openOptionsPage();
    else window.open('options.html');
  });
});

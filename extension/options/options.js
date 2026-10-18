// wattim × One Sec × GFW — Options Controller
document.addEventListener('DOMContentLoaded', () => {
  chrome.storage.local.get(['settings'], (data) => {
    const s = data.settings || {};
    document.getElementById('requireChallengePhrase').checked = s.requireChallengePhrase !== false;
    document.getElementById('challengePhrase').value = s.challengePhrase || 'Я захожу на {domain} строго по делу';
    document.getElementById('requireIntention').checked = s.requireIntention !== false;
    document.getElementById('baseDelay').value = s.baseDelay || 10;
    document.getElementById('reinterventionMinutes').value = s.reinterventionMinutes || 5;
    document.getElementById('backoffMode').value = s.backoffMode || 'exponential';
    document.getElementById('canvasStyle').value = s.canvasStyle || 'PULSE';
    document.getElementById('domains').value = (s.domains || []).join(', ');
    document.getElementById('phrases').value = (s.phrases || []).join('\n');
  });

  document.getElementById('saveBtn').addEventListener('click', () => {
    chrome.storage.local.get(['settings'], (data) => {
      const s = data.settings || {};
      s.requireChallengePhrase = document.getElementById('requireChallengePhrase').checked;
      s.challengePhrase = document.getElementById('challengePhrase').value.trim() || 'Я захожу на {domain} строго по делу';
      s.requireIntention = document.getElementById('requireIntention').checked;
      s.baseDelay = Math.max(3, parseInt(document.getElementById('baseDelay').value, 10) || 10);
      s.reinterventionMinutes = Math.max(1, parseInt(document.getElementById('reinterventionMinutes').value, 10) || 5);
      s.backoffMode = document.getElementById('backoffMode').value;
      s.canvasStyle = document.getElementById('canvasStyle').value;
      s.domains = document.getElementById('domains').value.split(/[\n,]+/).map(d => d.trim().toLowerCase()).filter(Boolean);
      s.phrases = document.getElementById('phrases').value.split('\n').map(p => p.trim()).filter(Boolean);

      chrome.storage.local.set({ settings: s }, () => {
        const t = document.getElementById('toast');
        t.style.display = 'inline';
        setTimeout(() => { t.style.display = 'none'; }, 2000);
      });
    });
  });
});

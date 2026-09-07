/**
 * wattim Browser Extension - Re-intervention Sentinel
 * Runs unobtrusively on web pages to monitor active dwell time.
 * If user remains stuck on a target website past their granted session pass,
 * signals the background worker to re-trigger a mindful pause.
 */

(function () {
  // Prevent duplicate execution
  if (window.__wattimSentinelActive) return;
  window.__wattimSentinelActive = true;

  const CHECK_INTERVAL_MS = 30 * 1000; // Check every 30 seconds
  let activeSeconds = 0;
  let lastUserActivity = Date.now();

  // Track user presence
  const registerActivity = () => {
    lastUserActivity = Date.now();
  };

  window.addEventListener('mousemove', registerActivity, { passive: true });
  window.addEventListener('keydown', registerActivity, { passive: true });
  window.addEventListener('scroll', registerActivity, { passive: true });
  window.addEventListener('click', registerActivity, { passive: true });

  const intervalId = setInterval(async () => {
    // Only count if page is visible and user was active in the last 60 seconds
    const isVisible = document.visibilityState === 'visible';
    const isRecentlyActive = (Date.now() - lastUserActivity) < 60 * 1000;

    if (!isVisible || !isRecentlyActive) {
      return;
    }

    activeSeconds += CHECK_INTERVAL_MS / 1000;

    try {
      if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
        chrome.runtime.sendMessage(
          {
            type: 'CHECK_SESSION',
            domain: window.location.hostname
          },
          (response) => {
            if (chrome.runtime.lastError) return;

            // If background reports session expired on active page, trigger mindful re-intervention
            if (response && response.valid === false) {
              clearInterval(intervalId);
              chrome.runtime.sendMessage({
                type: 'RE_INTERVENE',
                domain: window.location.hostname,
                targetUrl: window.location.href
              });
            }
          }
        );
      }
    } catch (err) {
      // Extension context invalidated (e.g. extension updated or reloaded)
      clearInterval(intervalId);
    }
  }, CHECK_INTERVAL_MS);
})();

/*
 * Warns players before the server restarts, and reloads the page once it is
 * back.
 *
 * Without this an update just breaks the page under whoever is playing: their
 * clicks stop working and nothing explains why. Here they get a countdown,
 * and the reload happens on its own when the new instance answers.
 *
 * Server side: /instance-status reports when this process started and, if
 * scripts/update.sh has scheduled one, when it intends to restart.
 */
(function () {
  'use strict';

  var POLL_MS_IDLE = 15000;   // nothing happening
  var POLL_MS_SOON = 2000;    // a restart is announced or in progress
  var ENDPOINT = '/instance-status';

  var knownStartedAt = null;  // the instance we loaded against
  var serverWentAway = false; // saw at least one failed poll
  var banner = null;
  var countdownTimer = null;

  function ensureBanner() {
    if (banner) return banner;
    banner = document.createElement('div');
    banner.id = 'updateBanner';
    banner.style.cssText = [
      'position:fixed', 'top:0', 'left:0', 'right:0', 'z-index:2147483647',
      'background:#b8860b', 'color:#fff', 'padding:10px 16px',
      'font:14px/1.4 sans-serif', 'text-align:center',
      'box-shadow:0 2px 6px rgba(0,0,0,.4)'
    ].join(';');
    document.body.appendChild(banner);
    return banner;
  }

  function showBanner(html) {
    ensureBanner().innerHTML = html;
  }

  function hideBanner() {
    if (banner && banner.parentNode) { banner.parentNode.removeChild(banner); banner = null; }
    if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null; }
  }

  function startCountdown(secondsLeft) {
    if (countdownTimer) return;   // already counting
    var remaining = secondsLeft;
    var render = function () {
      if (remaining > 0) {
        showBanner('<b>Update starting in ' + remaining + 's.</b> ' +
                   'The page will reload by itself - nothing is lost.');
        remaining--;
      } else {
        showBanner('<b>Updating...</b> the page will reload as soon as the server is back.');
      }
    };
    render();
    countdownTimer = setInterval(render, 1000);
  }

  function poll() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', ENDPOINT + '?t=' + Date.now(), true);
    xhr.timeout = 5000;

    xhr.onload = function () {
      var next = POLL_MS_IDLE;
      try {
        var s = JSON.parse(xhr.responseText);

        if (knownStartedAt === null) {
          knownStartedAt = s.startedAt;          // first sighting
        } else if (s.startedAt !== knownStartedAt) {
          // A different process is answering: the update landed.
          showBanner('<b>Update finished.</b> Reloading...');
          setTimeout(function () { window.location.reload(); }, 800);
          return;
        }

        if (s.restartAt) {
          var secs = Math.max(0, Math.round((s.restartAt - s.serverNow) / 1000));
          startCountdown(secs);
          next = POLL_MS_SOON;
        } else if (!serverWentAway) {
          hideBanner();
        }

        // The server answered again after being unreachable, but with the
        // same start time - it never actually went down (a blip, or the
        // laptop slept). Clear the warning rather than leaving it stuck.
        if (serverWentAway) { serverWentAway = false; hideBanner(); }
      } catch (e) { /* malformed answer - try again later */ }

      setTimeout(poll, next);
    };

    var failed = function () {
      // Server unreachable: it is probably restarting right now. Keep the
      // banner up and poll faster so the reload happens promptly.
      serverWentAway = true;
      showBanner('<b>Updating...</b> the page will reload as soon as the server is back.');
      setTimeout(poll, POLL_MS_SOON);
    };
    xhr.onerror = failed;
    xhr.ontimeout = failed;

    xhr.send();
  }

  // This file is loaded from <head>, so the body does not exist yet and any
  // look for #map here finds nothing. Wait for the document before deciding.
  function start() {
    if (document.getElementById('map') || document.querySelector('.leaflet-container')) {
      setTimeout(poll, 3000);
    }
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();

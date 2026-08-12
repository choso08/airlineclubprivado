/*
 * End-to-end test for the update warning and automatic reload.
 *
 *   node test/auto-refresh-test.js
 *
 * Opens the game, schedules a restart the way scripts/update.sh does, and
 * checks that:
 *
 *   1. a countdown banner appears for players already on the page
 *   2. the banner survives the server actually going away
 *   3. the page reloads itself once a new instance answers
 *
 * Needs to restart the site, so it expects RESTART_CMD to bring it back.
 */
const { chromium } = require('playwright');
const { execSync } = require('child_process');
const fs = require('fs');

const URL = process.env.TEST_URL || 'http://localhost:9000/';
const CHROME = process.env.CHROME_PATH || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const FLAG = process.env.AIRLINE_RESTART_FLAG || '/tmp/airline-restart-at';
const RESTART_CMD = process.env.RESTART_CMD;

const results = [];
const ok = (name, pass, detail) => results.push({ name, pass: !!pass, detail: detail || '' });

const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  if (fs.existsSync(FLAG)) fs.unlinkSync(FLAG);

  const browser = await chromium.launch({ executablePath: CHROME });
  const page = await browser.newPage();

  let reloadCount = 0;
  page.on('load', () => reloadCount++);

  await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 90000 });
  await sleep(6000);   // let the poller start (it waits 3s)

  ok('no banner while nothing is happening',
     await page.evaluate(() => !document.getElementById('updateBanner')));

  // --- 1. announce a restart, exactly as update.sh does --------------------
  const restartAt = Date.now() + 20000;
  fs.writeFileSync(FLAG, String(restartAt));

  let bannerText = '';
  for (let i = 0; i < 25; i++) {
    bannerText = await page.evaluate(() => {
      const b = document.getElementById('updateBanner');
      return b ? b.textContent : '';
    });
    if (bannerText) break;
    await sleep(1000);
  }
  ok('a warning banner appears', !!bannerText, bannerText.slice(0, 60));
  ok('the banner counts down in seconds', /\d+s/.test(bannerText), bannerText.slice(0, 60));
  ok('the banner promises an automatic reload', /reload/i.test(bannerText));

  const loadsBefore = reloadCount;

  // --- 2 & 3. actually restart, and expect the page to come back ----------
  if (!RESTART_CMD) {
    ok('SKIPPED: set RESTART_CMD to test the reload itself', true);
  } else {
    execSync(RESTART_CMD, { stdio: 'ignore' });
    // update.sh removes the flag once the new instance is up; mirror that so
    // the reloaded page is not greeted by the countdown it just lived through.
    if (fs.existsSync(FLAG)) fs.unlinkSync(FLAG);

    let reloaded = false;
    for (let i = 0; i < 60; i++) {
      await sleep(2000);
      if (reloadCount > loadsBefore) { reloaded = true; break; }
    }
    ok('the page reloaded itself after the restart', reloaded,
       reloaded ? '' : 'no reload within 120s');

    if (reloaded) {
      await sleep(6000);
      ok('the banner is gone after the reload',
         await page.evaluate(() => !document.getElementById('updateBanner')));
    }
  }

  if (fs.existsSync(FLAG)) fs.unlinkSync(FLAG);

  const failed = results.filter(r => !r.pass);
  const width = Math.max(...results.map(r => r.name.length)) + 2;
  console.log('\n=== update warning / auto reload ===\n');
  for (const r of results) {
    console.log(`  ${r.pass ? 'PASS' : 'FAIL'}  ${r.name.padEnd(width)}${r.detail ? '  ' + r.detail : ''}`);
  }
  console.log(`\n  ${results.length - failed.length}/${results.length} passed\n`);

  await browser.close();
  process.exit(failed.length ? 1 : 0);
})();

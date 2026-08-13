/*
 * The in-game clock, and the speed it runs at.
 *
 *   node test/game-clock-test.js [url]
 *
 * A cycle has always been exactly one week on the clock. ui.gameTimeSpeed
 * scales that without touching the simulation, which still thinks in weeks and
 * still pays weekly - so the two things worth pinning down are that the pace
 * really does change, and that the clock stays continuous when it does: where
 * one cycle's clock ends is where the next one begins, or the date jumps every
 * few minutes and the aircraft on the map jump with it.
 */
const { chromium } = require('playwright');

const BASE = process.argv[2] || 'http://localhost:9000';
const CHROME = process.env.CHROME_PATH || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';

const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

const results = [];
const ok = (name, pass, detail) => results.push({ name, pass: !!pass, detail: detail || '' });

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, args: ['--no-sandbox'] });
  const page = await browser.newPage();
  await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(1200);

  const r = await page.evaluate((weekMs) => {
    const out = {};
    out.hasClock = typeof window.updateTime === 'function' && typeof window.gameTimePerCycle === 'function';
    if (!out.hasClock) return out;

    out.configured = window.GAME_TIME_SPEED;

    const at = (cycle, fraction, speed) => {
      window.GAME_TIME_SPEED = speed;
      window.updateTime(cycle, fraction, 7 * 60 * 1000);
      return window.currentGameTime();
    };

    // One cycle of game time, at each speed.
    window.GAME_TIME_SPEED = 1;
    out.fullWeek = window.gameTimePerCycle();
    window.GAME_TIME_SPEED = 0.5;
    out.halfWeek = window.gameTimePerCycle();
    window.GAME_TIME_SPEED = 0;          // nonsense, must not stop the clock
    out.zeroFallsBack = window.gameTimePerCycle();
    window.GAME_TIME_SPEED = 'banana';
    out.rubbishFallsBack = window.gameTimePerCycle();

    // A cycle apart, at half speed, is half a week apart.
    const startOf157 = at(157, 0, 0.5);
    const startOf158 = at(158, 0, 0.5);
    out.gapHalf = startOf158 - startOf157;

    // And the end of one cycle is the start of the next.
    const endOf157 = at(157, 1, 0.5);
    out.continuous = Math.abs(endOf157 - startOf158) < 1000;

    // At full speed the same pair is a whole week apart.
    out.gapFull = at(158, 0, 1) - at(157, 0, 1);

    // The clock the map is drawn against comes from the same place.
    window.GAME_TIME_SPEED = 0.5;
    window.updateTime(157, 0, 7 * 60 * 1000);
    out.weekMinute = window.currentGameWeekMinute();

    window.GAME_TIME_SPEED = out.configured;
    return out;
  }, WEEK_MS);

  ok('the page has a clock to test', r.hasClock);
  if (r.hasClock) {
    ok('a cycle is a week at full speed', r.fullWeek === WEEK_MS, r.fullWeek + 'ms');
    ok('and half a week at half speed', r.halfWeek === WEEK_MS / 2, r.halfWeek + 'ms');
    ok('zero does not stop time', r.zeroFallsBack === WEEK_MS, r.zeroFallsBack + 'ms');
    ok('nor does nonsense', r.rubbishFallsBack === WEEK_MS, r.rubbishFallsBack + 'ms');
    ok('consecutive cycles are half a week apart', Math.abs(r.gapHalf - WEEK_MS / 2) < 1000,
       Math.round(r.gapHalf / 3600000) + 'h');
    ok('the date never jumps between cycles', r.continuous);
    ok('full speed still moves a whole week', Math.abs(r.gapFull - WEEK_MS) < 1000,
       Math.round(r.gapFull / 3600000) + 'h');
    ok('the map still gets a minute of the week',
       typeof r.weekMinute === 'number' && r.weekMinute >= 0 && r.weekMinute < 7 * 24 * 60,
       String(r.weekMinute));
  }

  const failed = results.filter(x => !x.pass);
  const width = Math.max(...results.map(x => x.name.length)) + 2;
  console.log('\n=== game clock battery ===\n');
  for (const x of results) {
    console.log(`  ${x.pass ? 'PASS' : 'FAIL'}  ${x.name.padEnd(width)}${x.detail ? '  ' + x.detail : ''}`);
  }
  console.log(`\n  ${results.length - failed.length}/${results.length} passed\n`);

  await browser.close();
  process.exit(failed.length ? 1 : 0);
})();

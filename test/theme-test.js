/*
 * Checks that the map's light/dark and the game's light/dark are one setting.
 *
 *   node test/theme-test.js [url]
 *
 * Upstream kept them apart: a Color Theme under Settings, and a separate
 * switch on the map. Choosing dark gave you a dark game and a bright white
 * map. These tests pin down that they now move together, in both directions,
 * and that pinning map.theme still overrides the pair.
 *
 * The tiles are what is asserted on, not a class name - the tile URL is the
 * only thing that decides what the player actually sees.
 */
const { chromium } = require('playwright');

const BASE = process.argv[2] || 'http://localhost:9000';
const CHROME = process.env.CHROME_PATH || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';

const results = [];
const ok = (name, pass, detail) => results.push({ name, pass: !!pass, detail: detail || '' });

const isDark = url => /dark_all/.test(url || '');
const isLight = url => /light_all/.test(url || '');

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME });

  // Read the map's tile source and the game's theme together, so they can
  // never be reported out of step with each other.
  const state = page => page.evaluate(() => ({
    tiles: window.map && window.map._tileLayer && window.map._tileLayer._url,
    game: document.documentElement.getAttribute('data-theme'),
    stored: window.localStorage.getItem('theme'),
    mapStyles: window.currentStyles
  }));

  // ------------------------------------------------- a game set to dark
  {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await page.addInitScript(() => window.localStorage.setItem('theme', 'dark'));
    await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 90000 });
    await page.waitForTimeout(5000);

    const start = await state(page);
    ok('a dark game opens a dark map', isDark(start.tiles), start.tiles);
    ok('the two agree at load', start.mapStyles === start.game, start.mapStyles + ' / ' + start.game);

    // ---------------------------------------- Settings moves the map with it
    await page.evaluate(() => {
      document.getElementById('switchDark').checked = false;
      document.getElementById('switchLight').checked = true;
      switchTheme();
    });
    await page.waitForTimeout(1500);
    const toLight = await state(page);
    ok('switching the game to light lightens the map', isLight(toLight.tiles), toLight.tiles);
    ok('the game is light too', toLight.game === 'light', toLight.game);

    await page.evaluate(() => {
      document.getElementById('switchDark').checked = true;
      document.getElementById('switchLight').checked = false;
      switchTheme();
    });
    await page.waitForTimeout(1500);
    const backToDark = await state(page);
    ok('switching back darkens the map again', isDark(backToDark.tiles), backToDark.tiles);

    // --------------------------------- and the switch on the map moves both
    await page.evaluate(() => toggleMapLight());
    await page.waitForTimeout(1500);
    const viaMap = await state(page);
    ok('the map switch lightens the map', isLight(viaMap.tiles), viaMap.tiles);
    ok('the map switch lightens the game as well', viaMap.game === 'light', viaMap.game);
    ok('and Settings shows it', await page.evaluate(() => document.getElementById('switchLight').checked));

    await ctx.close();
  }

  // ---------------------------------------------- a game set to light
  {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await page.addInitScript(() => window.localStorage.setItem('theme', 'light'));
    await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 90000 });
    await page.waitForTimeout(5000);
    const start = await state(page);
    ok('a light game opens a light map', isLight(start.tiles), start.tiles);
    await ctx.close();
  }

  // ------------------------------------- a stale map cookie does not win
  {
    const ctx = await browser.newContext();
    await ctx.addCookies([{ name: 'currentMapStyles', value: 'light', url: BASE }]);
    const page = await ctx.newPage();
    await page.addInitScript(() => window.localStorage.setItem('theme', 'dark'));
    await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 90000 });
    await page.waitForTimeout(5000);
    const start = await state(page);
    // Whatever the map was last left on, the game's theme is the setting now -
    // otherwise one old cookie keeps a player's map out of step for ever.
    ok('an old map cookie loses to the game theme', isDark(start.tiles), start.tiles);
    await ctx.close();
  }

  // ------------------------------------------------- map.theme pins it
  {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await page.addInitScript(() => {
      window.localStorage.setItem('theme', 'light');
      // What the server writes into the page when map.theme is set to "dark".
      // Not writable, so the page's own assignment of "follow" cannot take it
      // back - this instance is configured to follow, and the point here is to
      // exercise the pinned path without restarting the server.
      Object.defineProperty(window, 'OSM_DEFAULT_THEME', { value: 'dark', writable: false });
    });
    await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 90000 });
    await page.waitForTimeout(5000);

    const pinned = await state(page);
    ok('a pinned map ignores a light game', isDark(pinned.tiles), pinned.tiles);

    await page.evaluate(() => {
      document.getElementById('switchDark').checked = true;
      document.getElementById('switchLight').checked = false;
      switchTheme();
    });
    await page.waitForTimeout(1500);
    const afterSwitch = await state(page);
    ok('a pinned map stays put when the game changes', isDark(afterSwitch.tiles), afterSwitch.tiles);

    // The switch on the map is the one thing that still moves a pinned map,
    // and it moves only the map.
    await page.evaluate(() => toggleMapLight());
    await page.waitForTimeout(1500);
    const afterMapSwitch = await state(page);
    ok('the map switch still works on a pinned map', isLight(afterMapSwitch.tiles), afterMapSwitch.tiles);
    ok('and leaves the game alone', afterMapSwitch.game === 'dark', afterMapSwitch.game);

    await ctx.close();
  }

  const failed = results.filter(r => !r.pass);
  const width = Math.max(...results.map(r => r.name.length)) + 2;
  console.log('\n=== theme battery ===\n');
  for (const r of results) {
    console.log(`  ${r.pass ? 'PASS' : 'FAIL'}  ${r.name.padEnd(width)}${r.detail ? '  ' + r.detail : ''}`);
  }
  console.log(`\n  ${results.length - failed.length}/${results.length} passed\n`);

  await browser.close();
  process.exit(failed.length ? 1 : 0);
})();

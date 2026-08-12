/*
 * Checks the pieces this instance deliberately changed, end to end against a
 * running server.
 *
 *   node test/features-test.js
 *
 * Covers:
 *   - signup and login work with reCAPTCHA disabled
 *   - the Patreon-gated features are open (account.allFeaturesUnlocked)
 *   - the in-game chat websocket connects and speaks
 *   - the Patreon and Discord buttons are gone from the page
 */
const { chromium } = require('playwright');

const BASE = process.env.TEST_URL || 'http://localhost:9000';
const CHROME = process.env.CHROME_PATH || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
// Usernames may be alphanumeric, but AIRLINE names are letters and spaces
// only - digits there are rejected with a 400 that looks like a server fault.
const SUFFIX = Math.floor(Date.now() / 1000 % 100000).toString()
  .split('').map(d => 'abcdefghij'[+d]).join('');
const USER = 'testfeat' + SUFFIX;
const PASS = '1234';

const results = [];
const ok = (name, pass, detail) => results.push({ name, pass: !!pass, detail: detail || '' });

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME });
  const ctx = await browser.newContext();
  const page = await ctx.newPage();

  // ------------------------------------------------ signup without reCAPTCHA
  const signup = await page.request.post(BASE + '/signup', {
    form: {
      username: USER, email: USER + '@example.com',
      'password.main': PASS, 'password.confirm': PASS,
      recaptchaToken: '', airlineName: 'Test Air ' + SUFFIX
    },
    maxRedirects: 0
  }).catch(e => ({ status: () => 'ERR ' + e.message }));
  ok('signup succeeds with reCAPTCHA off', signup.status() === 303 || signup.status() === 200,
     'status ' + signup.status());

  // ---------------------------------------------------------------- login
  const login = await page.request.post(BASE + '/login', {
    headers: { Authorization: 'Basic ' + Buffer.from(USER + ':' + PASS).toString('base64') }
  });
  ok('login with a 4 character password', login.status() === 200, 'status ' + login.status());

  let user = {};
  try { user = await login.json(); } catch (e) {}
  ok('login returns the user record', !!user.userName, user.userName || '');

  // -------------------------------------------- Patreon features unlocked
  ok('server reports premium despite level 0 (allFeaturesUnlocked)',
     user.isPremium === true, 'level=' + user.level + ' isPremium=' + user.isPremium);

  // ------------------------------------------------------ page is de-branded
  const home = await (await page.request.get(BASE + '/')).text();
  ok('the Patreon button is gone', home.indexOf('bePatron') === -1);
  ok('the Patreon image is gone', home.indexOf('become_a_patron_button') === -1);
  ok('the Discord button is gone', home.indexOf('discord.gg') === -1);
  ok('no FullStory session recording', home.indexOf('_fs_org') === -1);
  ok('no Google Maps script when using OpenStreetMap',
     home.indexOf('maps.googleapis.com') === -1);
  ok('Leaflet is served locally, not from a CDN',
     home.indexOf('leaflet.js') !== -1 && home.indexOf('unpkg.com/leaflet') === -1);

  // ------------------------------------------------------------------- chat
  // The client picks ws:// or wss:// from the page protocol, so this is the
  // same path a player takes; behind `tailscale serve` it becomes wss on 443.
  // The chat socket requires a logged-in session and answers Forbidden without
  // one, so establish the session in this browser context first - the cookie
  // jar is shared with page.request.
  await page.goto(BASE + '/', { waitUntil: 'domcontentloaded', timeout: 90000 });
  const sessionLogin = await page.request.post(BASE + '/login', {
    headers: { Authorization: 'Basic ' + Buffer.from(USER + ':' + PASS).toString('base64') }
  });
  ok('login establishes a browser session', sessionLogin.status() === 200,
     'status ' + sessionLogin.status());
  const cookies = await ctx.cookies();
  ok('a session cookie is set', cookies.some(c => /session|PLAY/i.test(c.name)),
     cookies.map(c => c.name).join(','));

  const chat = await page.evaluate((base) => new Promise(resolve => {
    try {
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
      const port = location.port || (location.protocol === 'https:' ? 443 : 80);
      const uri = proto + '//' + location.hostname + ':' + port + '/chat';
      const ws = new WebSocket(uri);
      const done = r => resolve(Object.assign({ uri }, r));
      const timer = setTimeout(() => done({ state: 'timeout' }), 12000);
      ws.onopen = () => { clearTimeout(timer); done({ state: 'open' }); ws.close(); };
      ws.onerror = () => { clearTimeout(timer); done({ state: 'error' }); };
      ws.onclose = e => { clearTimeout(timer); done({ state: 'closed', code: e.code }); };
    } catch (e) { resolve({ state: 'threw', message: e.message }); }
  }), BASE);
  ok('the chat websocket connects', chat.state === 'open',
     chat.state + (chat.code ? ' code ' + chat.code : '') + ' @ ' + (chat.uri || ''));

  // The live-updates socket the game uses for cycle progress.
  const live = await page.evaluate(() => new Promise(resolve => {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const port = location.port || (location.protocol === 'https:' ? 443 : 80);
    const ws = new WebSocket(proto + '//' + location.hostname + ':' + port + '/wsWithActor');
    const timer = setTimeout(() => resolve({ state: 'timeout' }), 12000);
    ws.onopen = () => { clearTimeout(timer); resolve({ state: 'open' }); ws.close(); };
    ws.onerror = () => { clearTimeout(timer); resolve({ state: 'error' }); };
    ws.onclose = e => { clearTimeout(timer); resolve({ state: 'closed', code: e.code }); };
  }));
  // Without a chosen airline this one may legitimately refuse; opening or a
  // clean close both prove the endpoint is reachable and speaking WebSocket.
  ok('the live-update websocket endpoint responds',
     live.state === 'open' || live.state === 'closed', live.state);

  const failed = results.filter(r => !r.pass);
  const width = Math.max(...results.map(r => r.name.length)) + 2;
  console.log('\n=== instance feature battery ===\n');
  for (const r of results) {
    console.log(`  ${r.pass ? 'PASS' : 'FAIL'}  ${r.name.padEnd(width)}${r.detail ? '  ' + r.detail : ''}`);
  }
  console.log(`\n  ${results.length - failed.length}/${results.length} passed\n`);

  await browser.close();
  process.exit(failed.length ? 1 : 0);
})();

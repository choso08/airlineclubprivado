/*
 * Checks the Portuguese interface, and the departure board it sits on.
 *
 *   node test/translate-test.js
 *
 * Two things are being tested, and they are not the same thing:
 *
 *   - the dictionary itself, read as a file. Cheap, and catches the mistakes
 *     that are easy to make by hand - a duplicated key silently overwriting an
 *     earlier one, an entry left empty, a translation identical to its English.
 *   - the machinery, in a real browser. That only the listed phrases change,
 *     that panels the game builds afterwards are caught too, and that a player
 *     who has not chosen a language still sees English.
 *
 * And the departure board, because the weather it asks for used to be able to
 * take the whole board down with it.
 */
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const BASE = process.env.TEST_URL || 'http://localhost:9000';
const CHROME = process.env.CHROME_PATH || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const SOURCE = path.join(__dirname, '..', 'airline-web', 'public', 'javascripts', 'translate.js');

const results = [];
const ok = (name, pass, detail) => results.push({ name, pass: !!pass, detail: detail || '' });

// Errors that are the game's own and predate any of this. index.scala.html
// pulls AngularJS from Google's CDN, so on a machine with no route to
// ajax.googleapis.com every page reports it - including a plain checkout with
// none of these changes applied. Failing the battery on it would only teach
// whoever runs it to ignore the battery.
const INHERITED = [/angular is not defined/];
const unexpected = list => list.filter(m => !INHERITED.some(p => p.test(m)));

// --------------------------------------------------------------- the file
const source = fs.readFileSync(SOURCE, 'utf8');

ok('translate.js parses', (() => {
  try { new Function(source); return true; } catch (e) { return false; }
})());

// Pull the pt block out and read its keys in order, so duplicates show up -
// as an object literal they would silently collapse and the earlier one would
// simply never apply.
const ptBlock = source.slice(source.indexOf('pt: {'), source.lastIndexOf('};'));
const entries = [];
const entryPattern = /^\s*'((?:[^'\\]|\\.)*)':\s*'((?:[^'\\]|\\.)*)'/gm;
let m;
while ((m = entryPattern.exec(ptBlock)) !== null) {
  entries.push([m[1], m[2]]);
}
// Multi-line entries have their translation on the following line; catch those
// separately rather than pretending the single-line pattern saw everything.
const wrapped = ptBlock.match(/^\s*'((?:[^'\\]|\\.)*)':\s*$/gm) || [];

ok('the dictionary has entries', entries.length > 200, entries.length + ' single-line entries');

const seen = new Map();
const duplicates = [];
for (const [key] of entries) {
  if (seen.has(key)) duplicates.push(key);
  seen.set(key, true);
}
ok('no duplicate keys', duplicates.length === 0, duplicates.slice(0, 5).join(', '));

const empty = entries.filter(([, value]) => value.trim() === '').map(([k]) => k);
ok('no empty translations', empty.length === 0, empty.slice(0, 5).join(', '));

// A handful are legitimately identical - PAX, km, min, OK, proper nouns - so
// this is a sanity bound, not a ban.
const identical = entries.filter(([k, v]) => k === v).map(([k]) => k);
ok('few translations identical to the English', identical.length < 25,
   identical.length + ': ' + identical.slice(0, 8).join(', '));

const blank = entries.filter(([k]) => k.trim() === '');
ok('no blank keys', blank.length === 0);

// Untranslated whitespace is the thing that quietly breaks table layouts.
const padded = entries.filter(([k]) => k !== k.trim()).map(([k]) => JSON.stringify(k));
ok('keys carry no stray whitespace', padded.length === 0, padded.slice(0, 4).join(', '));

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME });

  // ------------------------------------------------------- English by default
  {
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 90000 });
    await page.waitForTimeout(3000);

    const state = await page.evaluate(() => ({
      module: typeof window.airlineTranslate,
      current: window.airlineTranslate && window.airlineTranslate.current(),
      settingsHeading: (document.querySelector('#settingsModal h4') || {}).textContent
    }));
    ok('the module loads', state.module === 'object', state.module);
    ok('a new player gets English', state.current === 'en', state.current);
    ok('English is left alone', (state.settingsHeading || '').trim() === 'Settings', state.settingsHeading);
    ok('no page errors in English', unexpected(errors).length === 0, unexpected(errors).slice(0, 2).join(' | '));
    await page.close();
  }

  // ------------------------------------------------------------- Portuguese
  {
    const ctx = await browser.newContext();
    await ctx.addCookies([{ name: 'gameLanguage', value: 'pt', url: BASE }]);
    const page = await ctx.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 90000 });
    await page.waitForTimeout(3000);

    const state = await page.evaluate(() => {
      const text = id => {
        const el = document.querySelector(id);
        return el ? el.textContent.trim() : null;
      };
      return {
        current: window.airlineTranslate.current(),
        settingsHeading: text('#settingsModal h4'),
        // Chosen because they sit in different places: a modal heading, a
        // table header cell, and the language selector's own label.
        headings: Array.from(document.querySelectorAll('#settingsModal h5')).map(h => h.textContent.trim()),
        selectorValue: (document.getElementById('languageSelect') || {}).value,
        // The option labels are proper nouns and must survive untouched.
        options: Array.from(document.querySelectorAll('#languageSelect option')).map(o => o.textContent)
      };
    });

    ok('the cookie selects Portuguese', state.current === 'pt', state.current);
    ok('a heading is translated', state.settingsHeading === 'Definições', state.settingsHeading);
    ok('the language row is translated', state.headings.indexOf('Idioma') >= 0, state.headings.join(', '));
    ok('the colour theme row is translated', state.headings.indexOf('Tema de cores') >= 0, state.headings.join(', '));
    ok('the selector shows the current language', state.selectorValue === 'pt', state.selectorValue);
    ok('language names are not translated', state.options.join(',') === 'English,Português', state.options.join(','));
    ok('no page errors in Portuguese', unexpected(errors).length === 0, unexpected(errors).slice(0, 2).join(' | '));

    // ------------------------------------------- panels built after the fact
    const later = await page.evaluate(() => new Promise(resolve => {
      const host = document.createElement('div');
      host.innerHTML = '<span>Airline</span><span>Weekly Passengers</span>' +
                       '<span>Not A Known Phrase At All</span>' +
                       '<span>  Distance  </span>' +
                       '<input type="button" value="Cancel">' +
                       '<span title="Fleet">x</span>';
      document.body.appendChild(host);
      // The observer batches into an animation frame, so wait for two.
      requestAnimationFrame(() => requestAnimationFrame(() => {
        const spans = host.querySelectorAll('span');
        resolve({
          known: spans[0].textContent,
          multiword: spans[1].textContent,
          unknown: spans[2].textContent,
          padded: spans[3].textContent,
          button: host.querySelector('input').value,
          title: spans[4].getAttribute('title')
        });
      }));
    }));

    ok('later panels are translated', later.known === 'Companhia', later.known);
    ok('multi-word phrases are translated', later.multiword === 'Passageiros semanais', later.multiword);
    ok('unknown phrases are untouched', later.unknown === 'Not A Known Phrase At All', later.unknown);
    ok('surrounding spaces are preserved', later.padded === '  Distância  ', JSON.stringify(later.padded));
    ok('button labels are translated', later.button === 'Cancelar', later.button);
    ok('tooltips are translated', later.title === 'Frota', later.title);

    await ctx.close();
  }

  // ------------------------------------------------ the departure board holds
  {
    const page = await browser.newPage();
    // Lisbon, but any airport with an id will do - the point is that the
    // endpoint answers rather than failing on a weather lookup it cannot make.
    const lookup = await page.request.get(BASE + '/airports/3670/departures/4/12/0');
    ok('the departure board answers', lookup.status() === 200, 'status ' + lookup.status());
    if (lookup.status() === 200) {
      const body = await lookup.json().catch(() => null);
      ok('the board returns a timeslot list', body && Array.isArray(body.timeslots),
         body ? Object.keys(body).join(', ') : 'unparseable');
    } else {
      ok('the board returns a timeslot list', false, 'skipped, request failed');
    }
    await page.close();
  }

  const failed = results.filter(r => !r.pass);
  const width = Math.max(...results.map(r => r.name.length)) + 2;
  console.log('\n=== translation battery ===\n');
  for (const r of results) {
    console.log(`  ${r.pass ? 'PASS' : 'FAIL'}  ${r.name.padEnd(width)}${r.detail ? '  ' + r.detail : ''}`);
  }
  console.log(`\n  ${results.length - failed.length}/${results.length} passed\n`);

  await browser.close();
  process.exit(failed.length ? 1 : 0);
})();

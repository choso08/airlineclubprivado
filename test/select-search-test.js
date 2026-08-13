/*
 * The filter box above the long dropdowns.
 *
 *   node test/select-search-test.js [url]
 *
 * The aircraft list on a route is eighty-odd entries and the browser's own
 * type-ahead only matches from the start of a name, so "737" finds nothing.
 * This checks the box that fixes that, against a dropdown built the way the
 * game builds its own.
 *
 * Two of these are here because they went wrong in front of a player: the box
 * stretched to the width of the whole dialog instead of the dropdown's, and
 * the selected aircraft stayed in the list however you filtered - so typing
 * "dhc" left a Cessna sitting at the top and the box read as broken.
 */
const { chromium } = require('playwright');

const BASE = process.argv[2] || 'http://localhost:9000';
const CHROME = process.env.CHROME_PATH || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';

const results = [];
const ok = (name, pass, detail) => results.push({ name, pass: !!pass, detail: detail || '' });

// Real models, in the order the game offers them for a short domestic leg.
const MODELS = [
  'Cessna 421 (53)', 'Britten-Norman BN-2 Islander (51)', 'Pilatus PC-12 (54)',
  'Beechcraft Super King Air 200 (54)', 'Cessna Caravan (53)',
  'Britten-Norman MKIII Trislander (52)', 'Let L-410UVP-E20 (51)',
  'Bombardier DHC-6-400 (50)', 'Let L-410NG (51)', 'Yakovlev Yak-40 (48)',
  'Bombardier DHC-8-200 (45)', 'Bombardier DHC-8-100 (45)', 'ATR 42-600 (43)',
  'Embraer EMB 120 Brasília (42)'
];

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, args: ['--no-sandbox'] });
  const page = await browser.newPage();
  await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 60000 });

  const r = await page.evaluate(async (models) => {
    const out = {};

    // A dropdown of the same shape as the route planner's, in a cell far wider
    // than the dropdown itself - which is what the real dialog does, and what
    // made the box stretch across the panel.
    const cell = document.createElement('div');
    cell.style.width = '620px';
    const select = document.createElement('select');
    select.id = 'testModelSelect';
    select.className = 'select-css';
    select.style.width = '240px';
    models.forEach((m, i) => {
      const option = document.createElement('option');
      option.value = String(i);
      option.textContent = m;
      select.appendChild(option);
    });
    cell.appendChild(select);
    document.body.appendChild(cell);

    window.attachSelectSearch('testModelSelect', 'search aircraft');
    const box = cell.querySelector('input.select-search');
    out.boxExists = !!box;
    if (!box) return out;

    const visible = () => Array.from(select.options)
      .filter(o => !o.hidden && o.style.display !== 'none')
      .map(o => o.textContent);
    const all = () => Array.from(select.options).map(o => o.textContent);

    out.widthMatches = Math.abs(box.getBoundingClientRect().width - select.getBoundingClientRect().width) <= 2;
    out.boxWidth = Math.round(box.getBoundingClientRect().width);
    out.selectWidth = Math.round(select.getBoundingClientRect().width);

    const type = (text) => {
      box.value = text;
      box.dispatchEvent(new Event('input', { bubbles: true }));
    };

    // Filtering, with something else selected.
    select.value = '4';                                  // Cessna Caravan
    type('dhc');
    out.filtered = visible();
    out.selectionKept = select.value === '4';
    out.orderPreserved = JSON.stringify(all()) === JSON.stringify(models);

    // Anywhere in the name, not just the start - the whole point.
    type('737');
    out.middleOfName = visible().length === 0;           // no 737 in this list
    out.emptyMarked = box.classList.contains('select-search-empty');

    type('421');
    out.byNumber = visible();

    // Accents, which nobody types.
    type('brasilia');
    out.accentsIgnored = visible().length === 1;

    type('');
    out.restored = visible().length === models.length;
    out.emptyCleared = !box.classList.contains('select-search-empty');

    // The game rebuilds these lists whenever the route changes.
    type('dhc');
    select.innerHTML = '';
    models.slice(0, 5).forEach((m, i) => {
      const option = document.createElement('option');
      option.value = String(i);
      option.textContent = m;
      select.appendChild(option);
    });
    await new Promise(res => setTimeout(res, 120));
    out.rebuildClearsFilter = box.value === '';
    out.rebuildShowsAll = visible().length === 5;
    // Five options is not worth a filter box.
    out.hiddenWhenShort = box.style.display === 'none';

    return out;
  }, MODELS);

  ok('the box appears above the dropdown', r.boxExists);
  ok('it is the width of the dropdown, not of the dialog', r.widthMatches,
     r.boxWidth + 'px vs ' + r.selectWidth + 'px');
  ok('typing narrows the list', r.filtered && r.filtered.length === 3,
     (r.filtered || []).join(', '));
  ok('only matches are left - not the selected one as well',
     r.filtered && r.filtered.every(name => /dhc/i.test(name)),
     (r.filtered || []).join(', '));
  ok('the selection survives being filtered away', r.selectionKept);
  ok('the order never changes', r.orderPreserved);
  ok('a number in the middle of a name matches', r.byNumber && r.byNumber.length === 1,
     (r.byNumber || []).join(', '));
  ok('accents are ignored', r.accentsIgnored);
  ok('nothing matching is marked rather than left blank', r.emptyMarked);
  ok('clearing the box brings everything back', r.restored);
  ok('and clears the nothing-matched mark', r.emptyCleared);
  ok('a rebuilt list drops the old filter', r.rebuildClearsFilter);
  ok('and shows everything in the new one', r.rebuildShowsAll);
  ok('a short list gets no box at all', r.hiddenWhenShort);

  const failed = results.filter(x => !x.pass);
  const width = Math.max(...results.map(x => x.name.length)) + 2;
  console.log('\n=== dropdown filter battery ===\n');
  for (const x of results) {
    console.log(`  ${x.pass ? 'PASS' : 'FAIL'}  ${x.name.padEnd(width)}${x.detail ? '  ' + x.detail : ''}`);
  }
  console.log(`\n  ${results.length - failed.length}/${results.length} passed\n`);

  await browser.close();
  process.exit(failed.length ? 1 : 0);
})();

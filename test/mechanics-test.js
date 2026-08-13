/*
 * Checks the game's own rules against the live world.
 *
 *   node test/mechanics-test.js                 check the world as it stands
 *   node test/mechanics-test.js --watch-cycle   also wait for a cycle and
 *                                               check that it did something
 *
 * The other batteries here drive a browser: they prove the pages work. None of
 * them can tell you the GAME is working - that aircraft are not flying more
 * hours than exist in a week, that nobody is selling seats that are not on the
 * aeroplane, that the money adds up.
 *
 * So this reads the world directly and checks the rules the simulation is
 * supposed to keep. Every rule below is taken from the code, with the source
 * named, rather than from what seemed reasonable - a test that asserts an
 * invented rule is worse than no test, because it gets switched off.
 *
 * It runs against whatever database the environment points at, so it works on
 * a real game with players in it. Nothing here writes.
 */
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const REPO = path.join(__dirname, '..');
const BASE = process.env.TEST_URL || 'http://localhost:9000';
const WATCH = process.argv.includes('--watch-cycle');

// ------------------------------------------------------------ database ---

// Same precedence as scripts/common.sh: what is already set wins over .env.
function loadEnv() {
  const env = {};
  for (const file of ['.env', 'game-settings.env']) {
    const full = path.join(REPO, file);
    if (!fs.existsSync(full)) continue;
    for (const line of fs.readFileSync(full, 'utf8').split('\n')) {
      const m = /^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$/.exec(line);
      if (!m) continue;
      const value = m[2].trim().replace(/^["']|["']$/g, '');
      if (env[m[1]] === undefined) env[m[1]] = value;
    }
  }
  return {
    schema: process.env.AIRLINE_DB_SCHEMA || env.AIRLINE_DB_SCHEMA || 'airline_v2_1',
    user: process.env.AIRLINE_DB_USER || env.AIRLINE_DB_USER || 'sa',
    password: process.env.AIRLINE_DB_PASSWORD !== undefined
      ? process.env.AIRLINE_DB_PASSWORD : (env.AIRLINE_DB_PASSWORD || '')
  };
}

const db = loadEnv();

function query(sql) {
  const args = ['--skip-column-names', '-B', '-u', db.user];
  if (db.password) args.push('-p' + db.password);
  args.push(db.schema, '-e', sql);
  const out = execFileSync('mariadb', args, { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
  return out.split('\n').filter(Boolean).map(row => row.split('\t'));
}

// ---------------------------------------------------------------- rules ---

/*
 * Each rule is a query that returns the rows BREAKING it. An empty result is a
 * pass, which keeps every rule readable as a single statement of what must
 * never happen.
 *
 * `source` says where the rule comes from, so anyone can check the test rather
 * than trust it.
 */
const RULES = [
  {
    name: 'no aircraft flies more hours than exist in a week',
    source: 'Airplane.MAX_FLIGHT_MINUTES = 24 * 60 * 4',
    sql: `SELECT la.airplane, SUM(la.flight_minutes)
          FROM link_assignment la
          GROUP BY la.airplane
          HAVING SUM(la.flight_minutes) > 5760`,
    describe: r => `airplane ${r[0]} is flying ${r[1]} minutes a week`
  },
  {
    name: 'aircraft only fly for the airline that owns them',
    source: 'a route belongs to one airline, and so does each aeroplane on it',
    sql: `SELECT la.airplane, p.owner, l.airline
          FROM link_assignment la
            JOIN airplane p ON p.id = la.airplane
            JOIN link l ON l.id = la.link
          WHERE p.owner <> l.airline`,
    describe: r => `airplane ${r[0]} is owned by ${r[1]} but flies for ${r[2]}`
  },
  {
    name: 'no sold aircraft is still assigned to a route',
    source: 'Airplane.isReady = !isSold && currentCycle >= constructedCycle',
    sql: `SELECT la.airplane, la.link
          FROM link_assignment la JOIN airplane p ON p.id = la.airplane
          WHERE p.is_sold <> 0`,
    describe: r => `sold airplane ${r[0]} still assigned to route ${r[1]}`
  },
  {
    name: 'every assignment points at a route and an aircraft that exist',
    source: 'referential integrity - the schema has no foreign keys to enforce it',
    sql: `SELECT la.link, la.airplane
          FROM link_assignment la
            LEFT JOIN link l ON l.id = la.link
            LEFT JOIN airplane p ON p.id = la.airplane
          WHERE l.id IS NULL OR p.id IS NULL`,
    describe: r => `assignment route ${r[0]} / airplane ${r[1]} points at nothing`
  },
  {
    name: 'flight numbers are unique within an airline',
    source: 'LinkApplication.getNextAvailableFlightNumber picks the lowest free one',
    sql: `SELECT airline, flight_number, COUNT(*)
          FROM link
          WHERE transport_type = 0 AND flight_number > 0
          GROUP BY airline, flight_number
          HAVING COUNT(*) > 1`,
    describe: r => `airline ${r[0]} has ${r[2]} routes numbered ${r[1]}`
  },
  {
    name: 'every route joins two airports that exist, for an airline that exists',
    source: 'referential integrity',
    sql: `SELECT l.id
          FROM link l
            LEFT JOIN airport af ON af.id = l.from_airport
            LEFT JOIN airport at2 ON at2.id = l.to_airport
            LEFT JOIN airline a ON a.id = l.airline
          WHERE af.id IS NULL OR at2.id IS NULL
             OR (l.transport_type = 0 AND a.id IS NULL)`,
    describe: r => `route ${r[0]} points at an airport or airline that is gone`
  },
  {
    name: 'no route goes from an airport to itself',
    source: 'a flight needs two ends',
    sql: `SELECT id, from_airport FROM link WHERE from_airport = to_airport`,
    describe: r => `route ${r[0]} starts and ends at airport ${r[1]}`
  },
  {
    name: 'no more seats were sold than the aircraft had',
    source: 'link_consumption records what actually flew',
    sql: `SELECT link, sold_seats_economy, capacity_economy,
                 sold_seats_business, capacity_business,
                 sold_seats_first, capacity_first
          FROM link_consumption
          WHERE sold_seats_economy > capacity_economy
             OR sold_seats_business > capacity_business
             OR sold_seats_first > capacity_first`,
    describe: r => `route ${r[0]} sold ${r[1]}/${r[3]}/${r[5]} seats but had ${r[2]}/${r[4]}/${r[6]}`
  },
  {
    name: 'no negative passenger counts',
    source: 'a flight cannot carry fewer than nobody',
    sql: `SELECT link FROM link_consumption
          WHERE sold_seats_economy < 0 OR sold_seats_business < 0 OR sold_seats_first < 0`,
    describe: r => `route ${r[0]} carried a negative number of passengers`
  },
  {
    name: 'the money on every flight adds up',
    source: 'LinkSimulation: profit = revenue - fuel - maintenance - crew - airport fees'
          + ' - inflight - delay compensation - depreciation - lounge',
    sql: `SELECT link, profit,
                 revenue - fuel_cost - maintenance_cost - crew_cost - airport_fees
                         - inflight_cost - delay_compensation - depreciation - lounge_cost
          FROM link_consumption
          WHERE profit <> revenue - fuel_cost - maintenance_cost - crew_cost - airport_fees
                                  - inflight_cost - delay_compensation - depreciation - lounge_cost`,
    describe: r => `route ${r[0]} records a profit of ${r[1]} where the parts make ${r[2]}`
  },
  {
    name: 'every flight record belongs to a route that exists',
    source: 'referential integrity',
    sql: `SELECT lc.link FROM link_consumption lc
            LEFT JOIN link l ON l.id = lc.link
          WHERE l.id IS NULL`,
    describe: r => `a flight record refers to route ${r[0]}, which is gone`
  },
  {
    name: 'loyalty stays between nothing and full',
    source: 'AirlineAppeal.MAX_LOYALTY = 100',
    sql: `SELECT airline, airport, loyalty FROM airline_appeal
          WHERE loyalty < 0 OR loyalty > 100`,
    describe: r => `airline ${r[0]} has loyalty ${r[2]} at airport ${r[1]}`
  },
  {
    name: 'every base belongs to an airline and an airport that exist',
    source: 'referential integrity',
    sql: `SELECT b.airline, b.airport
          FROM airline_base b
            LEFT JOIN airline a ON a.id = b.airline
            LEFT JOIN airport ap ON ap.id = b.airport
          WHERE a.id IS NULL OR ap.id IS NULL`,
    describe: r => `a base of airline ${r[0]} at airport ${r[1]} points at nothing`
  },
  {
    name: 'an airline has at most one headquarters',
    source: 'airline_base.headquarter marks the one base that is the HQ',
    sql: `SELECT airline, COUNT(*) FROM airline_base
          WHERE headquarter = 1 GROUP BY airline HAVING COUNT(*) > 1`,
    describe: r => `airline ${r[0]} has ${r[1]} headquarters`
  },
  {
    name: 'every airline has exactly one balance',
    source: 'airline_info carries the balance; without a row the airline has no money at all',
    sql: `SELECT a.id, COUNT(i.airline)
          FROM airline a LEFT JOIN airline_info i ON i.airline = a.id
          GROUP BY a.id HAVING COUNT(i.airline) <> 1`,
    describe: r => `airline ${r[0]} has ${r[1]} balance rows`
  },
  {
    name: 'every player account has an airline',
    source: 'user_airline links the two; a player without one cannot enter the game',
    sql: `SELECT ua.airline FROM user_airline ua
            LEFT JOIN airline a ON a.id = ua.airline
          WHERE a.id IS NULL`,
    describe: r => `an account points at airline ${r[0]}, which does not exist`
  },
  {
    name: 'the world clock is running',
    source: 'the cycle table holds the current week',
    sql: `SELECT cycle FROM cycle WHERE cycle < 1`,
    describe: r => `the clock reads ${r[0]}`
  }
];

// Things worth knowing that are not faults. Kept apart on purpose: a battery
// that cries wolf is a battery people stop reading.
const NOTES = [
  {
    name: 'routes flying nothing',
    sql: `SELECT COUNT(*) FROM link l
          WHERE l.transport_type = 0
            AND NOT EXISTS (
              SELECT 1 FROM link_assignment la JOIN airplane p ON p.id = la.airplane
              WHERE la.link = l.id AND p.is_sold = 0
                AND p.constructed_cycle <= (SELECT cycle FROM cycle LIMIT 1))`,
    describe: n => `${n} route(s) have no delivered aircraft, so they fly nothing`
  },
  {
    name: 'stored frequency out of date',
    sql: `SELECT COUNT(*) FROM link l
          WHERE l.transport_type = 0 AND l.frequency <> COALESCE((
            SELECT SUM(la.frequency) FROM link_assignment la JOIN airplane p ON p.id = la.airplane
            WHERE la.link = l.id AND p.is_sold = 0
              AND p.constructed_cycle <= (SELECT cycle FROM cycle LIMIT 1)), 0)`,
    describe: n => `${n} route(s) store a frequency the game recomputes on load (harmless)`
  }
];

// ---------------------------------------------------------------- runner ---

const results = [];
const ok = (name, pass, detail) => results.push({ name, pass: !!pass, detail: detail || '' });
const notes = [];

function runRules() {
  for (const rule of RULES) {
    let rows;
    try {
      rows = query(rule.sql);
    } catch (e) {
      const message = String(e.stderr || e.message).split('\n').filter(Boolean).pop();
      ok(rule.name, false, 'could not check: ' + message);
      continue;
    }
    const detail = rows.length
      ? rows.slice(0, 3).map(rule.describe).join('; ') + (rows.length > 3 ? ` (+${rows.length - 3} more)` : '')
      : '';
    ok(rule.name, rows.length === 0, detail);
  }

  for (const note of NOTES) {
    try {
      const n = Number(query(note.sql)[0][0]);
      if (n > 0) notes.push(note.describe(n));
    } catch (e) { /* a note is never worth failing over */ }
  }
}

async function runEndpoints() {
  const check = async (name, url, test) => {
    try {
      const res = await fetch(BASE + url, { signal: AbortSignal.timeout(20000) });
      const body = res.headers.get('content-type', '').includes('json')
        ? await res.json().catch(() => null) : null;
      ok(name, res.status === 200 && (!test || test(body)), 'status ' + res.status);
    } catch (e) {
      ok(name, false, e.message);
    }
  };

  await check('the site answers', '/');
  await check('it reports its version', '/version', b => b && typeof b.version === 'string');

  // The departure board, because a failed weather lookup used to answer this
  // with an error and leave every board in the game blank.
  let airport;
  try { airport = query(`SELECT id FROM airport WHERE iata = 'LIS' LIMIT 1`)[0][0]; } catch (e) { /* */ }
  if (airport) {
    await check('a departure board answers', `/airports/${airport}/departures/3/12/0`,
      b => b && Array.isArray(b.timeslots));
  }
}

async function watchCycle() {
  const cycleNow = () => Number(query('SELECT cycle FROM cycle LIMIT 1')[0][0]);
  const start = cycleNow();
  const before = query(`SELECT COUNT(*), COALESCE(SUM(sold_seats_economy + sold_seats_business
                        + sold_seats_first), 0) FROM link_consumption`)[0];

  console.log(`\n  waiting for the clock to pass week ${start}...`);
  const deadline = Date.now() + 45 * 60 * 1000;
  while (cycleNow() === start) {
    if (Date.now() > deadline) {
      ok('the clock advances', false, `still on week ${start} after 45 minutes`);
      return;
    }
    await new Promise(r => setTimeout(r, 15000));
  }
  const after = query(`SELECT COUNT(*), COALESCE(SUM(sold_seats_economy + sold_seats_business
                       + sold_seats_first), 0) FROM link_consumption`)[0];

  ok('the clock advances', cycleNow() > start, `week ${start} -> ${cycleNow()}`);
  // A cycle that runs but moves nobody means the simulation is running and
  // doing nothing, which looks healthy from the outside and is not.
  ok('the week carried passengers', Number(after[1]) > 0,
     `${before[1]} -> ${after[1]} seats sold across ${after[0]} routes`);

  console.log('  re-checking every rule against the new week...\n');
  runRules();
}

(async () => {
  try {
    query('SELECT 1');
  } catch (e) {
    console.error('\nCannot reach the database. Set AIRLINE_DB_USER / AIRLINE_DB_PASSWORD /');
    console.error('AIRLINE_DB_SCHEMA, or run this from a checkout with a .env file.\n');
    process.exit(2);
  }

  const [links, airlines, cycle] = query(
    `SELECT (SELECT COUNT(*) FROM link WHERE transport_type = 0),
            (SELECT COUNT(*) FROM airline),
            (SELECT cycle FROM cycle LIMIT 1)`)[0];

  console.log(`\n=== game mechanics battery ===`);
  console.log(`\n  week ${cycle}, ${airlines} airlines, ${links} flight routes\n`);

  runRules();
  await runEndpoints();
  if (WATCH) await watchCycle();

  const failed = results.filter(r => !r.pass);
  const width = Math.max(...results.map(r => r.name.length)) + 2;
  for (const r of results) {
    console.log(`  ${r.pass ? 'PASS' : 'FAIL'}  ${r.name.padEnd(width)}${r.detail ? '  ' + r.detail : ''}`);
  }
  if (notes.length) {
    console.log('\n  worth knowing, not faults:');
    for (const n of notes) console.log('    - ' + n);
  }
  console.log(`\n  ${results.length - failed.length}/${results.length} passed\n`);

  process.exit(failed.length ? 1 : 0);
})();

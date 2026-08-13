/*
 * Checks the aircraft moving on the map: that they leave when the game says
 * they leave, and that they are somewhere sensible while they are in the air.
 *
 *   node test/flight-animation-test.js [url]
 *
 * The important part is the first one. The browser works out each departure
 * time itself, and it has to reach the same answer as the server does in
 * com.patson.model.Scheduling - otherwise the departure board and the map show
 * two different flights and there is no way to tell which is lying.
 *
 * The times asserted below were taken from a running server's
 * /airports/<id>/departures, not from reading the Scala. Reimplementing a rule
 * and then testing it against your own reimplementation proves nothing.
 */
const { chromium } = require('playwright');

const BASE = process.argv[2] || 'http://localhost:9000';
const CHROME = process.env.CHROME_PATH || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';

const results = [];
const ok = (name, pass, detail) => results.push({ name, pass: !!pass, detail: detail || '' });

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME });
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 90000 });
  await page.waitForTimeout(4000);

  const out = await page.evaluate(() => {
    const r = {};
    const asText = minute => {
      const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
      const d = Math.floor(minute / 1440), h = Math.floor((minute % 1440) / 60), m = minute % 60;
      return days[d] + ' ' + String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0');
    };

    r.hasSchedule = typeof flightDepartureMinutes === 'function';
    r.hasProgress = typeof flightProgress === 'function';
    r.hasPopup = typeof showFlightPopup === 'function';
    r.hasClock = typeof currentGameWeekMinute === 'function';
    if (!r.hasSchedule) return r;

    // Lisbon (a major airport), 1868 km, airline 8, fourteen flights a week.
    // The running server put these at 00:20 and 12:20 every day.
    r.major = flightDepartureMinutes({
      frequency: 14, distance: 1868, airlineId: 8, fromAirportSize: 8
    }).sort((a, b) => a - b).map(asText);

    // The same route from a small airport is squeezed into 06:00-22:55.
    r.smallHours = flightDepartureMinutes({
      frequency: 20, distance: 1868, airlineId: 8, fromAirportSize: 2
    }).map(m => Math.floor((m % 1440) / 60));

    r.count = flightDepartureMinutes({
      frequency: 7, distance: 500, airlineId: 3, fromAirportSize: 9
    }).length;

    const weekly = flightDepartureMinutes({
      frequency: 9, distance: 777, airlineId: 5, fromAirportSize: 9
    });
    r.unique = new Set(weekly).size === weekly.length;
    r.spread = Math.max(...weekly) - Math.min(...weekly);

    r.none = flightDepartureMinutes({ frequency: 0, distance: 100, airlineId: 1, fromAirportSize: 9 });

    // ------------------------------------------------------------- in flight
    // Departs Monday 10:00 (minute 2040), three hours in the air.
    const dep = 1 * 1440 + 10 * 60, dur = 180;
    r.atDeparture = flightProgress(dep, dur, dep);
    r.halfway = flightProgress(dep, dur, dep + 90);
    r.justLanded = flightProgress(dep, dur, dep + 181);
    r.onTheWayBack = flightProgress(dep, dur, dep + dur + 60 + 90);
    r.longAfter = flightProgress(dep, dur, dep + 700);

    // A Saturday night departure lands on Sunday morning: the week has to wrap
    // or the aircraft vanishes at midnight.
    const late = 6 * 1440 + 23 * 60;   // Sat 23:00
    r.acrossMidnight = flightProgress(late, dur, 30);   // Sun 00:30

    return r;
  });

  ok('the schedule function is there', out.hasSchedule);
  ok('the progress function is there', out.hasProgress);
  ok('the click-through card is there', out.hasPopup);
  ok('the game clock is readable', out.hasClock);

  if (out.hasSchedule) {
    const expected = ['Sun 00:20', 'Sun 12:20', 'Mon 00:20', 'Mon 12:20', 'Tue 00:20', 'Tue 12:20',
                      'Wed 00:20', 'Wed 12:20', 'Thu 00:20', 'Thu 12:20', 'Fri 00:20', 'Fri 12:20',
                      'Sat 00:20', 'Sat 12:20'];
    ok('departures match what the server schedules',
       JSON.stringify(out.major) === JSON.stringify(expected), out.major.slice(0, 3).join(', '));

    const outOfHours = out.smallHours.filter(h => h < 6 || h > 22);
    ok('a small airport only dispatches between 06:00 and 23:00',
       outOfHours.length === 0, outOfHours.join(', '));

    ok('one departure per weekly flight', out.count === 7, String(out.count));
    ok('no two flights leave at the same moment', out.unique);
    ok('flights are spread across the week', out.spread > 4 * 1440, out.spread + ' minutes apart');
    ok('a route with no flights schedules nothing', out.none.length === 0);
  }

  if (out.hasProgress) {
    ok('at its departure time it is at the gate',
       out.atDeparture && out.atDeparture.outbound && out.atDeparture.fraction === 0);
    ok('halfway through it is halfway there',
       out.halfway && Math.abs(out.halfway.fraction - 0.5) < 0.01, out.halfway && out.halfway.fraction);
    ok('after landing it is off the map', out.justLanded === null);
    ok('later it flies back the other way',
       out.onTheWayBack && out.onTheWayBack.outbound === false,
       JSON.stringify(out.onTheWayBack));
    ok('once the return leg is over it is gone again', out.longAfter === null);
    ok('a flight over midnight on Saturday keeps going',
       out.acrossMidnight && out.acrossMidnight.outbound === true,
       JSON.stringify(out.acrossMidnight));
  }

  const inherited = [/angular is not defined/];
  const unexpected = errors.filter(m => !inherited.some(p => p.test(m)));
  ok('no page errors', unexpected.length === 0, unexpected.slice(0, 2).join(' | '));

  const failed = results.filter(r => !r.pass);
  const width = Math.max(...results.map(r => r.name.length)) + 2;
  console.log('\n=== flight animation battery ===\n');
  for (const r of results) {
    console.log(`  ${r.pass ? 'PASS' : 'FAIL'}  ${r.name.padEnd(width)}${r.detail ? '  ' + r.detail : ''}`);
  }
  console.log(`\n  ${results.length - failed.length}/${results.length} passed\n`);

  await browser.close();
  process.exit(failed.length ? 1 : 0);
})();

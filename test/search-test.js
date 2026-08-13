/*
 * The search boxes, against a running game.
 *
 *   node test/search-test.js [url]
 *
 * These are the searches people actually type, and every one of them is here
 * because it failed at some point: accents nobody writes at a keyboard, a word
 * from the middle of a long airport name, two fingers landing in the wrong
 * order, a filter that quietly matched everything because it matched nothing.
 *
 * It needs a game running with a generated world - nothing else. No browser,
 * no Elasticsearch. Airline and alliance names belong to whoever is playing,
 * so those are only checked for the shape of the answer, not its content.
 */
const BASE = process.argv[2] || 'http://localhost:9000';

const results = [];
const ok = (name, pass, detail) => results.push({ name, pass: !!pass, detail: detail || '' });

async function search(type, input) {
  const url = `${BASE}/search-${type}?input=${encodeURIComponent(input)}`;
  const response = await fetch(url);
  if (!response.ok) {
    return { status: response.status, entries: [], message: 'HTTP ' + response.status };
  }
  const body = await response.json();
  return { status: response.status, entries: body.entries || [], message: body.message || '' };
}

const codes = (result) => result.entries.map(e => e.airportIata);
const has = (result, iata) => codes(result).includes(iata);
const first = (result) => codes(result)[0];

(async () => {
  // The first search of all builds the index; time nothing until it has.
  await search('airport', 'lisbon');

  // ---- the plain cases -------------------------------------------------
  const lis = await search('airport', 'lis');
  ok('a three letter code finds its airport', first(lis) === 'LIS', codes(lis).slice(0, 4).join(', '));
  ok('and the near misses come after it', has(lis, 'LSY') || lis.entries.length > 1,
     lis.entries.length + ' results');

  const lisbon = await search('airport', 'lisbon');
  ok('a city by name', has(lisbon, 'LIS'), codes(lisbon).slice(0, 3).join(', '));

  const jfk = await search('airport', 'jfk');
  ok('another code, to show the first was not luck', first(jfk) === 'JFK', first(jfk));

  const kennedy = await search('airport', 'kennedy');
  ok('a word from the middle of a long name', has(kennedy, 'JFK'), codes(kennedy).slice(0, 3).join(', '));

  const heathrow = await search('airport', 'heathrow');
  ok('the name rather than the city', has(heathrow, 'LHR'), codes(heathrow).slice(0, 3).join(', '));

  // ---- accents ---------------------------------------------------------
  const plain = await search('airport', 'sao tome');
  ok('an accented place, typed without the accents', has(plain, 'TMS'), codes(plain).slice(0, 3).join(', '));

  const accented = await search('airport', 'são tomé');
  ok('and typed with them', has(accented, 'TMS'), codes(accented).slice(0, 3).join(', '));

  const zurich = await search('airport', 'zurich');
  ok('an umlaut nobody types either', has(zurich, 'ZRH'), codes(zurich).slice(0, 3).join(', '));

  // ---- typing badly ----------------------------------------------------
  const swapped = await search('airport', 'frankfrut');
  ok('two fingers in the wrong order', has(swapped, 'FRA'), codes(swapped).slice(0, 3).join(', '));

  const missing = await search('airport', 'amsterdm');
  ok('a letter left out', has(missing, 'AMS'), codes(missing).slice(0, 3).join(', '));

  const extra = await search('airport', 'barcellona');
  ok('a letter typed twice', has(extra, 'BCN'), codes(extra).slice(0, 3).join(', '));

  // ---- the four letter code --------------------------------------------
  const icao = await search('airport', 'eddf');
  ok('the four letter code works too', has(icao, 'FRA'), codes(icao).slice(0, 3).join(', '));

  // ---- more than one word ----------------------------------------------
  const twoWords = await search('airport', 'new york');
  ok('two words, both of which must match',
     has(twoWords, 'JFK') || has(twoWords, 'LGA') || has(twoWords, 'EWR'),
     codes(twoWords).slice(0, 4).join(', '));

  const contradiction = await search('airport', 'lisbon tokyo');
  ok('a second word narrows rather than widens', contradiction.entries.length === 0,
     contradiction.entries.length + ' results');

  // ---- saying no -------------------------------------------------------
  const nonsense = await search('airport', 'qqqqzzzz');
  ok('nothing is found for nothing', nonsense.entries.length === 0 && nonsense.message === 'No match',
     nonsense.message);

  const short = await search('airport', 'li');
  ok('two letters is too few for an airport', /at least 3/.test(short.message), short.message);

  const empty = await search('airport', '');
  ok('an empty box is not an error', empty.status === 200, 'HTTP ' + empty.status);

  // ---- countries, zones ------------------------------------------------
  const country = await search('country', 'portu');
  ok('a country by name', country.entries.some(e => e.countryCode === 'PT'),
     country.entries.map(e => e.countryCode).slice(0, 3).join(', '));

  const countryCode = await search('country', 'pt');
  ok('a country by its code', countryCode.entries.some(e => e.countryCode === 'PT'),
     countryCode.entries.map(e => e.countryCode).slice(0, 3).join(', '));

  const countryTypo = await search('country', 'portgual');
  ok('a country typed badly', countryTypo.entries.some(e => e.countryCode === 'PT'),
     countryTypo.entries.map(e => e.countryCode).slice(0, 3).join(', '));

  // Zones had no answer at all without Elasticsearch - not even a wrong one.
  const zone = await search('zone', 'eu');
  ok('a zone by its code', zone.entries.some(e => e.zone === 'EU'),
     zone.entries.map(e => e.zone).join(', '));

  const zoneName = await search('zone', 'africa');
  ok('a zone by name', zoneName.entries.some(e => e.zone === 'AF'),
     zoneName.entries.map(e => e.zone).join(', '));

  // ---- airlines and alliances ------------------------------------------
  // Whose airlines these are depends on who is playing, so what is checked
  // here is that the endpoint answers in the right shape and that a filter
  // which matches nothing says so - a filter that silently matches
  // everything is how "search by airline" ended up listing the whole world.
  const airline = await search('airline', 'qqqqzzzz');
  ok('an airline that does not exist', airline.entries.length === 0 && airline.message === 'No match',
     airline.message);

  const alliance = await search('alliance', 'qqqqzzzz');
  ok('an alliance that does not exist', alliance.entries.length === 0 && alliance.message === 'No match',
     alliance.message);

  const airlineShort = await search('airline', 'a');
  ok('one letter is too few for an airline', /at least/.test(airlineShort.message), airlineShort.message);

  // ---- how it behaves --------------------------------------------------
  const upper = await search('airport', 'LISBON');
  ok('capitals make no difference', first(upper) === first(lisbon), first(upper) + ' vs ' + first(lisbon));

  const started = Date.now();
  for (let i = 0; i < 10; i++) {
    await search('airport', 'lisbo'.slice(0, 3 + (i % 3)));
  }
  const each = (Date.now() - started) / 10;
  ok('a keystroke is answered quickly', each < 250, Math.round(each) + 'ms each');

  const ordered = await search('airport', 'paris');
  const scores = ordered.entries.map(e => e.score);
  ok('results come back best first',
     scores.every((score, i) => i === 0 || scores[i - 1] >= score), scores.slice(0, 4).join(' '));

  const capped = await search('airport', 'international');
  ok('a very common word does not return the world', capped.entries.length <= 20,
     capped.entries.length + ' results');

  const failed = results.filter(r => !r.pass);
  const width = Math.max(...results.map(r => r.name.length)) + 2;
  console.log('\n=== search battery ===\n');
  for (const r of results) {
    console.log(`  ${r.pass ? 'PASS' : 'FAIL'}  ${r.name.padEnd(width)}${r.detail ? '  ' + r.detail : ''}`);
  }
  console.log(`\n  ${results.length - failed.length}/${results.length} passed\n`);
  process.exit(failed.length ? 1 : 0);
})();

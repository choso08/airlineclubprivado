/*
 * Test battery for the OpenStreetMap shim (public/javascripts/libs/google-maps-shim.js).
 *
 * Drives a real browser against a running instance and exercises every part of
 * the Google Maps API the game calls. Written because the shim reimplements
 * someone else's API from the outside: the only way to know it behaves is to
 * call it the way the game does.
 *
 *   node test/osm-shim-test.js [url]
 *
 * Defaults to http://localhost:9000. Requires the site to be running and
 * playwright to be installed. Exits non-zero if anything fails, so it can gate
 * a deploy.
 *
 * Map tiles are NOT required - the tests check API behaviour and the objects
 * Leaflet builds, not pixels, so this passes on a machine with no access to
 * the tile server.
 */
const { chromium } = require('playwright');

const URL = process.argv[2] || 'http://localhost:9000/';
const CHROME = process.env.CHROME_PATH || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME });
  const page = await browser.newPage();

  const pageErrors = [];
  page.on('pageerror', e => pageErrors.push(e.message));

  await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 90000 });
  await page.waitForTimeout(5000);

  const results = await page.evaluate(() => {
    const out = [];
    const ok   = (name, cond, detail) => out.push({ name, pass: !!cond, detail: detail || '' });
    const near = (a, b, tol) => Math.abs(a - b) <= (tol == null ? 0.001 : tol);

    function group(name, fn) {
      try { fn(); }
      catch (e) { out.push({ name: name + ' (threw)', pass: false, detail: e.message }); }
    }

    // ---------------------------------------------------------- environment
    group('environment', () => {
      ok('google.maps exists', typeof google !== 'undefined' && !!google.maps);
      ok('Leaflet loaded', typeof L !== 'undefined');
      ok('jQuery loaded (game depends on it)', typeof jQuery !== 'undefined');
      ok('main map is a shim Map', typeof map !== 'undefined' && map && map._shimType === 'Map');
      ok('a Leaflet container was created', document.querySelectorAll('.leaflet-container').length >= 1);
      ok('a tile layer was attached', document.querySelectorAll('.leaflet-tile-pane').length >= 1);
    });

    // --------------------------------------------------------------- LatLng
    group('LatLng', () => {
      const p = new google.maps.LatLng(38.0789, -7.9324);
      ok('lat() returns latitude', near(p.lat(), 38.0789));
      ok('lng() returns longitude', near(p.lng(), -7.9324));
      ok('accepts a {lat,lng} literal', near(new google.maps.LatLng({ lat: 1, lng: 2 }).lat(), 1));
      ok('accepts another LatLng', near(new google.maps.LatLng(p).lng(), -7.9324));
      ok('equals() matches', p.equals({ lat: 38.0789, lng: -7.9324 }));
      ok('equals() rejects a different point', !p.equals({ lat: 0, lng: 0 }));
      const pt = new google.maps.Point(3, 4);
      ok('Point keeps x/y', pt.x === 3 && pt.y === 4);
    });

    // ------------------------------------------------------------------ Map
    group('Map', () => {
      map.setCenter(new google.maps.LatLng(38.0789, -7.9324));
      const c = map.getCenter();
      ok('setCenter/getCenter round-trip', near(c.lat(), 38.0789, 0.5) && near(c.lng(), -7.9324, 0.5));
      map.setZoom(6);
      ok('setZoom/getZoom round-trip', map.getZoom() === 6);
      const b = map.getBounds();
      ok('getBounds returns bounds', !!b && typeof b.contains === 'function');
      ok('bounds contain the centre', b.contains({ lat: 38.0789, lng: -7.9324 }));
      ok('bounds expose corners', !!b.getNorthEast().lat() && !!b.getSouthWest().lat());
      ok('setOptions applies zoom', (map.setOptions({ zoom: 5 }), map.getZoom() === 5));
    });

    // --------------------------------------------------------------- Marker
    group('Marker', () => {
      const m = new google.maps.Marker({
        position: { lat: 38.0789, lng: -7.9324 }, title: 'Beja'
      });
      ok('constructs without an icon (default pin)', !!m && !!m._leaflet);
      m.setMap(map);
      ok('setMap attaches it', m.getMap() === map);
      ok('setPosition moves it', (m.setPosition({ lat: 40, lng: -8 }), near(m.getPosition().lat(), 40)));
      ok('setTitle does not throw', (m.setTitle('BYJ'), true));
      ok('setOpacity does not throw', (m.setOpacity(0.4), true));
      ok('setZIndex does not throw', (m.setZIndex(7), true));
      m.setVisible(false);
      ok('setVisible(false) records state', m.getVisible() === false);
      m.setVisible(true);
      ok('setVisible(true) restores it', m.getVisible() === true);
      ok('setIcon with a url', (m.setIcon({ url: '/assets/images/icons/airport.png' }), true));
      ok('setIcon with a bare string', (m.setIcon('/assets/images/icons/airport.png'), true));
      ok('setIcon with a vector symbol is survivable',
         (m.setIcon({ path: google.maps.SymbolPath.CIRCLE, scale: 4 }), true));
      ok('setOptions applies several at once',
         (m.setOptions({ title: 'x', opacity: 1, zIndex: 2, visible: true }), true));
      ok('game code can hang its own fields on it',
         (m.airportId = 3097, m.airportId === 3097));

      // Google's MVCObject copies every option onto the instance, and the game
      // depends on it: airport markers carry their airport record in through
      // the constructor. Missing this left all ~3500 markers with
      // marker.airport undefined, and the zoom handler threw on first scroll.
      const carried = new google.maps.Marker({
        position: { lat: 1, lng: 1 },
        airport: { id: 3097, size: 5, name: 'Beja' },
        originalIcon: '/x.png',
        championIcon: '/y.png'
      });
      ok('custom constructor options survive (marker.airport)',
         carried.airport && carried.airport.size === 5);
      ok('several custom options survive at once',
         carried.originalIcon === '/x.png' && carried.championIcon === '/y.png');
      ok('custom options never clobber the methods',
         typeof carried.setMap === 'function' && typeof carried.setVisible === 'function');

      // This is what actually broke: zoom handlers read marker.airport.size.
      ok('the game\'s isShowMarker test can run against it',
         (function () {
           const zoom = 6;
           return (carried.isBase) || ((zoom >= 4) && (zoom + carried.airport.size / 2 >= 7.5));
         })() === true);
      m.setMap(null);
      ok('setMap(null) detaches it', m.getMap() === null);
    });

    // ------------------------------------------------------------- Polyline
    group('Polyline', () => {
      const line = new google.maps.Polyline({
        path: [{ lat: 38.07, lng: -7.93 }, { lat: 51.47, lng: -0.45 }],
        strokeColor: '#ff0000', strokeWeight: 2, strokeOpacity: 0.8, map: map
      });
      ok('constructs with a path', !!line._leaflet);
      ok('getPath().getLength()', line.getPath().getLength() === 2);
      const first = line.getPath().getAt(0);
      ok('getPath().getAt() returns a LatLng', near(first.lat(), 38.07));
      line.setPath([{ lat: 0, lng: 0 }, { lat: 1, lng: 1 }, { lat: 2, lng: 2 }]);
      ok('setPath replaces the path', line.getPath().getLength() === 3);
      ok('setOptions restyles', (line.setOptions({ strokeColor: '#00ff00', strokeWeight: 4 }), true));
      ok('custom constructor options survive on a Polyline',
         new google.maps.Polyline({ path: [{lat:0,lng:0},{lat:1,lng:1}], linkId: 42 }).linkId === 42);
      ok('setVisible does not throw', (line.setVisible(false), line.setVisible(true), true));
      ok('accepts direction arrows without dying',
         (new google.maps.Polyline({
            path: [{ lat: 0, lng: 0 }, { lat: 1, lng: 1 }],
            icons: [{ icon: { path: google.maps.SymbolPath.FORWARD_CLOSED_ARROW } }],
            map: map }), true));
      line.setMap(null);
      ok('setMap(null) detaches it', line.getMap() === null);
    });

    // --------------------------------------------------------------- Circle
    group('Circle', () => {
      const c = new google.maps.Circle({
        center: { lat: 38.07, lng: -7.93 }, radius: 50000,
        fillColor: '#0000ff', fillOpacity: 0.2, map: map
      });
      ok('constructs', !!c._leaflet);
      ok('setRadius', (c.setRadius(80000), c._leaflet.getRadius() === 80000));
      ok('setCenter', (c.setCenter({ lat: 40, lng: -8 }), near(c._leaflet.getLatLng().lat, 40)));
      ok('setOptions', (c.setOptions({ radius: 1000, fillColor: '#fff' }), true));
      c.setMap(null);
      ok('setMap(null) detaches it', c.getMap() === null);
    });

    // ----------------------------------------------------------- InfoWindow
    group('InfoWindow', () => {
      const anchor = new google.maps.Marker({ position: { lat: 38, lng: -7 }, map: map });
      const iw = new google.maps.InfoWindow({ content: '<b>Beja</b>' });
      ok('constructs', !!iw);
      ok('getContent', iw.getContent() === '<b>Beja</b>');
      iw.open(map, anchor);
      ok('open() shows a popup', document.querySelectorAll('.leaflet-popup').length >= 1);
      iw.setContent('<i>changed</i>');
      ok('setContent while open', document.body.innerHTML.indexOf('changed') !== -1);
      iw.close();
      ok('close() removes it', document.querySelectorAll('.leaflet-popup').length === 0);
      iw.setPosition({ lat: 10, lng: 10 });
      iw.open(map);
      ok('open() at an explicit position', document.querySelectorAll('.leaflet-popup').length >= 1);
      iw.close();
      anchor.setMap(null);
    });

    // ---------------------------------------------------------------- events
    group('events', () => {
      const m = new google.maps.Marker({ position: { lat: 1, lng: 1 }, map: map });
      let clicks = 0, overs = 0, onces = 0;

      google.maps.event.addListener(m, 'click', () => clicks++);
      google.maps.event.trigger(m, 'click');
      google.maps.event.trigger(m, 'click');
      ok('addListener fires every time', clicks === 2, 'got ' + clicks);

      google.maps.event.addListenerOnce(m, 'click', () => onces++);
      google.maps.event.trigger(m, 'click');
      google.maps.event.trigger(m, 'click');
      ok('addListenerOnce fires exactly once', onces === 1, 'got ' + onces);

      const h = google.maps.event.addListener(m, 'mouseover', () => overs++);
      google.maps.event.trigger(m, 'mouseover');
      google.maps.event.removeListener(h);
      google.maps.event.trigger(m, 'mouseover');
      ok('removeListener stops delivery', overs === 1, 'got ' + overs);

      let after = 0;
      google.maps.event.addListener(m, 'click', () => after++);
      google.maps.event.clearInstanceListeners(m);
      google.maps.event.trigger(m, 'click');
      ok('clearInstanceListeners silences it', after === 0, 'got ' + after);

      ok('unknown events are ignored, not fatal',
         (google.maps.event.addListener(m, 'maptypeid_changed', () => {}), true));
      ok('listening on the map works',
         (google.maps.event.addListener(map, 'idle', () => {}), true));
      ok('bounds_changed binds', (google.maps.event.addListener(map, 'bounds_changed', () => {}), true));
      ok('zoom_changed binds', (google.maps.event.addListener(map, 'zoom_changed', () => {}), true));
      ok("trigger('resize') resizes rather than throwing",
         (google.maps.event.trigger(map, 'resize'), true));
      m.setMap(null);
    });

    // -------------------------------------------------------------- geometry
    group('geometry.spherical', () => {
      const interp = google.maps.geometry.spherical.interpolate;

      const mid = interp({ lat: 0, lng: 0 }, { lat: 0, lng: 90 }, 0.5);
      ok('equator midpoint is (0, 45)', near(mid.lat(), 0, 0.01) && near(mid.lng(), 45, 0.01),
         mid.lat() + ',' + mid.lng());

      const start = interp({ lat: 10, lng: 20 }, { lat: 50, lng: 60 }, 0);
      ok('fraction 0 returns the start', near(start.lat(), 10, 0.01) && near(start.lng(), 20, 0.01));

      const end = interp({ lat: 10, lng: 20 }, { lat: 50, lng: 60 }, 1);
      ok('fraction 1 returns the end', near(end.lat(), 50, 0.01) && near(end.lng(), 60, 0.01));

      const same = interp({ lat: 5, lng: 5 }, { lat: 5, lng: 5 }, 0.5);
      ok('identical points do not divide by zero', same && near(same.lat(), 5, 0.01));

      // A great circle bulges polewards of the straight line on a flat map:
      // Lisbon to New York should pass north of their latitude average.
      const atlantic = interp({ lat: 38.77, lng: -9.13 }, { lat: 40.64, lng: -73.78 }, 0.5);
      ok('great circle bows polewards, not straight', atlantic.lat() > 40.9,
         'midpoint lat ' + atlantic.lat().toFixed(2));

      const d = google.maps.geometry.spherical.computeDistanceBetween(
        { lat: 38.0789, lng: -7.9324 }, { lat: 51.47, lng: -0.45 });
      ok('Beja to London is about 1600km', d > 1_500_000 && d < 1_700_000,
         Math.round(d / 1000) + ' km');
    });

    // ------------------------------------------------------------- constants
    group('constants', () => {
      ok('ControlPosition.LEFT/RIGHT/TOP defined',
         google.maps.ControlPosition.LEFT !== undefined &&
         google.maps.ControlPosition.RIGHT !== undefined &&
         google.maps.ControlPosition.TOP !== undefined);
      ok('SymbolPath.FORWARD_CLOSED_ARROW defined',
         google.maps.SymbolPath.FORWARD_CLOSED_ARROW !== undefined);
      ok('map.controls buckets exist for the three positions used',
         map.controls && map.controls[google.maps.ControlPosition.LEFT] !== undefined);
    });

    // ------------------------------------------------------------- the corner
    // Leaflet parks its zoom buttons at the top left by default, which is
    // where the game's own top bar is - money, airline name, reputation. They
    // showed through it. Google, which this page was built around, puts them
    // at the bottom right.
    group('zoom buttons', () => {
      const container = (map._leaflet && map._leaflet.getContainer()) || document.body;
      ok('the zoom buttons exist',
         !!container.querySelector('.leaflet-control-zoom'));
      ok('they are not under the top bar',
         !container.querySelector('.leaflet-top.leaflet-left .leaflet-control-zoom'));
      ok('they are in the bottom right, where Google puts them',
         !!container.querySelector('.leaflet-bottom.leaflet-right .leaflet-control-zoom'));
    });

    // --------------------------------------------------------------- heatmap
    group('heatmap', () => {
      const h = new google.maps.visualization.HeatmapLayer({ data: [] });
      ok('HeatmapLayer constructs', !!h);
      ok('its methods are safe no-ops',
         (h.setMap(map), h.setData([]), h.setOptions({}), true));
    });

    return out;
  });

  const failed = results.filter(r => !r.pass);
  const width = Math.max(...results.map(r => r.name.length)) + 2;

  console.log('\n=== OpenStreetMap shim test battery ===\n');
  for (const r of results) {
    console.log(`  ${r.pass ? 'PASS' : 'FAIL'}  ${r.name.padEnd(width)}${r.detail ? '  ' + r.detail : ''}`);
  }
  console.log(`\n  ${results.length - failed.length}/${results.length} passed\n`);

  // Tile and CDN fetch failures are environmental, not shim bugs.
  const realErrors = pageErrors.filter(e => !/tile|Failed to fetch|net::|ERR_/i.test(e));
  if (realErrors.length) {
    console.log('=== uncaught page errors ===');
    realErrors.slice(0, 15).forEach(e => console.log('  ' + e));
    console.log('');
  }

  await browser.close();
  process.exit(failed.length ? 1 : 0);
})();

/*
 * OpenStreetMap drop-in for the Google Maps JavaScript API.
 *
 * The game calls google.maps.* in about 10,500 lines across a dozen files.
 * Rather than rewrite those, this implements the slice of the Google API they
 * actually use on top of Leaflet, so the game code carries on unchanged and
 * no API key, billing account or Google project is needed.
 *
 * Only what the game calls is implemented - this is not a general Google Maps
 * emulator. The full inventory it was built against:
 *
 *   Map  Marker  Polyline  Circle  InfoWindow  LatLng  Point
 *   event.{addListener,addListenerOnce,clearListeners,clearInstanceListeners,trigger}
 *   geometry.spherical.interpolate
 *   ControlPosition.{LEFT,RIGHT,TOP}   SymbolPath.FORWARD_*
 *   visualization.HeatmapLayer
 *
 * Events used by the game, and what they map to in Leaflet:
 *
 *   click / mouseover / mouseout   direct equivalents
 *   idle                           moveend + zoomend
 *   zoom_changed                   zoomend
 *   bounds_changed                 moveend
 *   closeclick                     popupclose
 *   maptypeid_changed              no equivalent, ignored
 */
(function (global) {
  'use strict';

  if (typeof L === 'undefined') {
    console.error('[osm-shim] Leaflet is not loaded - the map cannot start.');
    return;
  }

  // ---------------------------------------------------------------- helpers

  // Google accepts LatLng objects, {lat,lng} literals and {lat(),lng()}.
  // Leaflet wants [lat, lng]. Normalise everything here, once.
  function toLatLngArray(v) {
    if (v == null) return null;
    if (Array.isArray(v)) return v;
    if (typeof v.lat === 'function') return [v.lat(), v.lng()];
    if (typeof v.lat === 'number') return [v.lat, v.lng];
    return null;
  }

  function toLatLngObj(latlng) {
    return new LatLng(latlng.lat, latlng.lng);
  }

  // Google's MVCObject copies EVERY key of the options object onto the
  // instance, so callers routinely smuggle their own data in through the
  // constructor and read it back as a property. The game relies on this
  // heavily:
  //
  //     new google.maps.Marker({ position: p, airport: airportInfo,
  //                              originalIcon: icon })
  //     ... later ...  marker.airport.size
  //
  // Without this, every one of the ~3500 airport markers came back with
  // marker.airport undefined and the zoom handler threw on the first wheel
  // scroll. Prototype methods are left alone - they are already defined, so
  // an option cannot overwrite setMap and friends.
  function applyCustomOptions(target, opts) {
    if (!opts) return;
    for (var key in opts) {
      if (!Object.prototype.hasOwnProperty.call(opts, key)) continue;
      if (target[key] === undefined) target[key] = opts[key];
    }
  }

  // Google's colours are plain CSS, so they pass straight through; only the
  // option NAMES differ.
  function pathStyle(opts) {
    opts = opts || {};
    var style = {};
    if (opts.strokeColor !== undefined) style.color = opts.strokeColor;
    if (opts.strokeWeight !== undefined) style.weight = opts.strokeWeight;
    if (opts.strokeOpacity !== undefined) style.opacity = opts.strokeOpacity;
    if (opts.fillColor !== undefined) style.fillColor = opts.fillColor;
    if (opts.fillOpacity !== undefined) style.fillOpacity = opts.fillOpacity;
    if (opts.zIndex !== undefined) style.zIndexOffset = opts.zIndex;
    if (opts.clickable === false) style.interactive = false;
    return style;
  }

  // ------------------------------------------------------------ event plumbing
  //
  // Google hands back a listener handle you can later remove. Leaflet removes
  // by (event, function), so remember the pieces needed to undo it.

  var EVENT_MAP = {
    click: 'click',
    dblclick: 'dblclick',
    mouseover: 'mouseover',
    mouseout: 'mouseout',
    mousemove: 'mousemove',
    zoom_changed: 'zoomend',
    bounds_changed: 'moveend',
    center_changed: 'moveend',
    dragend: 'dragend',
    closeclick: 'popupclose',
    // 'idle' has no single Leaflet event; handled specially below.
    maptypeid_changed: null
  };

  function bindListener(instance, eventName, handler, once) {
    var target = instance && instance._leaflet ? instance._leaflet : instance;
    if (!target || typeof target.on !== 'function') {
      return { remove: function () {} };
    }

    // Google passes the mouse event with a .latLng; Leaflet uses .latlng.
    function wrapped(e) {
      if (e && e.latlng && !e.latLng) e.latLng = toLatLngObj(e.latlng);
      return handler.call(instance, e);
    }

    if (eventName === 'idle') {
      // Google fires 'idle' once the map has settled. Approximate it by
      // debouncing the two Leaflet events that end a movement, so callers
      // that recompute the viewport do it once rather than per frame.
      var timer = null;
      var settle = function (e) {
        if (timer) clearTimeout(timer);
        timer = setTimeout(function () {
          wrapped(e);
          if (once) {
            target.off('moveend', settle);
            target.off('zoomend', settle);
          }
        }, 150);
      };
      target.on('moveend', settle);
      target.on('zoomend', settle);
      return {
        remove: function () {
          if (timer) clearTimeout(timer);
          target.off('moveend', settle);
          target.off('zoomend', settle);
        }
      };
    }

    var leafletEvent = EVENT_MAP[eventName];
    if (leafletEvent === null) return { remove: function () {} };   // knowingly ignored
    if (leafletEvent === undefined) {
      console.warn('[osm-shim] unmapped event "' + eventName + '" - ignoring');
      return { remove: function () {} };
    }

    if (once) target.once(leafletEvent, wrapped);
    else target.on(leafletEvent, wrapped);

    return {
      remove: function () { target.off(leafletEvent, wrapped); },
      _target: target,
      _event: leafletEvent,
      _fn: wrapped
    };
  }

  // ------------------------------------------------------------------ LatLng

  function LatLng(lat, lng) {
    if (!(this instanceof LatLng)) return new LatLng(lat, lng);
    if (typeof lat === 'object' && lat !== null) {
      var a = toLatLngArray(lat);
      this._lat = a[0]; this._lng = a[1];
    } else {
      this._lat = lat; this._lng = lng;
    }
  }
  LatLng.prototype.lat = function () { return this._lat; };
  LatLng.prototype.lng = function () { return this._lng; };
  LatLng.prototype.equals = function (o) {
    if (!o) return false;
    var a = toLatLngArray(o);
    return a && a[0] === this._lat && a[1] === this._lng;
  };
  LatLng.prototype.toString = function () { return '(' + this._lat + ', ' + this._lng + ')'; };

  function Point(x, y) {
    if (!(this instanceof Point)) return new Point(x, y);
    this.x = x; this.y = y;
  }

  // ------------------------------------------------------------- LatLngBounds

  function LatLngBounds(leafletBounds) {
    this._b = leafletBounds;
  }
  LatLngBounds.prototype.contains = function (ll) {
    var a = toLatLngArray(ll);
    return a ? this._b.contains(L.latLng(a[0], a[1])) : false;
  };
  LatLngBounds.prototype.getNorthEast = function () {
    var ne = this._b.getNorthEast(); return new LatLng(ne.lat, ne.lng);
  };
  LatLngBounds.prototype.getSouthWest = function () {
    var sw = this._b.getSouthWest(); return new LatLng(sw.lat, sw.lng);
  };
  LatLngBounds.prototype.getCenter = function () {
    var c = this._b.getCenter(); return new LatLng(c.lat, c.lng);
  };
  LatLngBounds.prototype.extend = function (ll) {
    var a = toLatLngArray(ll);
    if (a) this._b.extend(L.latLng(a[0], a[1]));
    return this;
  };

  // ------------------------------------------------------- control containers
  //
  // Google's map.controls[position] is an MVCArray whose push() physically
  // moves your element into the map's own overlay. Reproduce that with one
  // absolutely positioned container per position, layered above the map.

  var CONTROL_PLACEMENT = {
    0:  'top:10px; left:50%; transform:translateX(-50%);',        // TOP_CENTER
    1:  'top:10px; left:10px;',                                    // TOP_LEFT
    2:  'top:10px; right:10px;',                                   // TOP_RIGHT
    3:  'top:50%; left:10px; transform:translateY(-50%);',         // LEFT_CENTER
    4:  'bottom:24px; left:10px;',                                 // LEFT_BOTTOM
    5:  'top:50%; right:10px; transform:translateY(-50%);',        // RIGHT_CENTER
    6:  'bottom:24px; right:10px;',                                // RIGHT_BOTTOM
    7:  'bottom:24px; left:50%; transform:translateX(-50%);',      // BOTTOM_CENTER
    8:  'top:60px; left:10px;',                                    // LEFT_TOP
    9:  'top:60px; right:10px;'                                    // RIGHT_TOP
  };

  function buildControlContainers(mapElement) {
    var buckets = {};

    Object.keys(CONTROL_PLACEMENT).forEach(function (pos) {
      var box = document.createElement('div');
      box.className = 'osm-shim-controls osm-shim-controls-' + pos;
      box.setAttribute('style',
        'position:absolute; z-index:800; display:flex; flex-direction:column; ' +
        'gap:4px; align-items:flex-start; pointer-events:none; ' +
        CONTROL_PLACEMENT[pos]);
      mapElement.appendChild(box);

      buckets[pos] = {
        _box: box,
        push: function (el) {
          if (!el) return this.getLength();
          // Buttons must remain clickable even though the container is
          // click-through, so the map underneath still pans.
          el.style.pointerEvents = 'auto';
          box.appendChild(el);
          return this.getLength();
        },
        insertAt: function (index, el) {
          if (!el) return;
          el.style.pointerEvents = 'auto';
          box.insertBefore(el, box.children[index] || null);
        },
        clear: function () { while (box.firstChild) box.removeChild(box.firstChild); },
        removeAt: function (i) {
          var child = box.children[i];
          if (child) box.removeChild(child);
        },
        getAt: function (i) { return box.children[i]; },
        getLength: function () { return box.children.length; },
        forEach: function (fn) { Array.prototype.slice.call(box.children).forEach(fn); }
      };
    });

    return buckets;
  }

  // --------------------------------------------------------------------- Map

  // Google styled its map from JavaScript, so the game flips between light and
  // dark by handing setOptions a style array. With tiles the look comes from
  // the server instead, so the two themes are two tile sources and switching
  // means swapping the layer.
  //
  // CARTO's basemaps are used because they offer a matched light/dark pair -
  // the same cartography in two palettes - and need no API key. Plain
  // OpenStreetMap has no dark equivalent, so mixing them would make the two
  // themes look like different maps.
  var TILE_URL_LIGHT = (global.OSM_TILE_URL_LIGHT ||
      'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png');
  var TILE_URL_DARK = (global.OSM_TILE_URL_DARK ||
      'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png');

  // An explicit OSM_TILE_URL overrides both, for anyone pointing at their own
  // tile server - they then get the same tiles in both themes.
  var TILE_URL_OVERRIDE = global.OSM_TILE_URL || null;

  var TILE_ATTRIBUTION = (global.OSM_TILE_ATTRIBUTION ||
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, &copy; <a href="https://carto.com/attributions">CARTO</a>');

  // map-style.js works the theme out and leaves it in a global. If the map is
  // built before that runs, fall back to the same two sources it uses: a
  // pinned map.theme, then the game's own colour theme.
  function currentTheme() {
    if (global.currentStyles === 'light') return 'light';
    if (global.currentStyles === 'dark') return 'dark';
    if (global.OSM_DEFAULT_THEME === 'light' || global.OSM_DEFAULT_THEME === 'dark') {
      return global.OSM_DEFAULT_THEME;
    }
    try {
      return (global.localStorage.getItem('theme') === 'light') ? 'light' : 'dark';
    } catch (e) {
      return 'dark';
    }
  }

  function tileUrlFor(theme) {
    if (TILE_URL_OVERRIDE) return TILE_URL_OVERRIDE;
    return theme === 'light' ? TILE_URL_LIGHT : TILE_URL_DARK;
  }

  function Map(element, opts) {
    opts = opts || {};
    var centre = toLatLngArray(opts.center) || [20, 0];

    this._leaflet = L.map(element, {
      center: centre,
      zoom: opts.zoom != null ? opts.zoom : 3,
      minZoom: opts.minZoom != null ? opts.minZoom : 2,
      maxZoom: opts.maxZoom != null ? opts.maxZoom : 18,
      // Leaflet puts its zoom buttons at the top left, where the game's own
      // top bar is - so they showed through it, half hidden behind the money
      // and the airline name. Google, which this page was built around, puts
      // them at the bottom right, so that is where they go; the corner is
      // added below rather than here.
      zoomControl: false,
      attributionControl: true,
      // The game draws thousands of markers; canvas keeps that usable.
      preferCanvas: true,
      worldCopyJump: true
    });

    if (!opts.disableDefaultUI) {
      L.control.zoom({ position: 'bottomright' }).addTo(this._leaflet);
    }

    this._theme = currentTheme();
    if (darkFilterWanted(this._theme)) {
      this._leaflet.getContainer().classList.add('osm-dark-filter');
    }
    this._tileLayer = L.tileLayer(tileUrlFor(this._theme), {
      attribution: TILE_ATTRIBUTION,
      maxZoom: 19,
      crossOrigin: true
    }).addTo(this._leaflet);

    // Google lets you park your own DOM inside the map with
    //   map.controls[position].push(element)
    // and the game builds its whole button bar that way. Leaving these as
    // plain arrays silently swallowed every button: the elements stayed
    // wherever they were created, unpositioned, showing as ghostly circles
    // floating over the map. Each position gets a real container instead.
    this.controls = buildControlContainers(this._leaflet.getContainer());
    this._shimType = 'Map';

    // Leaflet measures its container once and then assumes that size. Google's
    // map re-measured itself, so the game never had to say anything.
    //
    // This matters immediately: the game ships two stylesheets, and #map is
    // width:50% in the classic one but width:100% in the modern one. Switching
    // theme resizes the container, Leaflet carries on drawing at the old width,
    // and half the screen goes blank. Same on any window resize or when a
    // hidden map is revealed.
    var self = this;
    setTimeout(function () { self._leaflet.invalidateSize(); }, 0);

    if (typeof ResizeObserver !== 'undefined') {
      var pending = null;
      var observer = new ResizeObserver(function () {
        // Coalesce: a theme switch fires several times as styles apply, and
        // invalidateSize forces a full redraw of every tile and marker.
        if (pending) clearTimeout(pending);
        pending = setTimeout(function () { self._leaflet.invalidateSize(); }, 100);
      });
      observer.observe(element);
      this._resizeObserver = observer;
    } else {
      // Older browsers: at least follow the window.
      window.addEventListener('resize', function () { self._leaflet.invalidateSize(); });
    }
  }

  // Google applies these immediately - code routinely does setCenter() and
  // then reads getCenter()/getBounds() on the next line. Leaflet's panTo and
  // setZoom animate, so those reads would see the OLD viewport until the
  // animation finished. Animation is disabled here to match Google.
  Map.prototype.setCenter = function (ll) {
    var a = toLatLngArray(ll);
    if (a) this._leaflet.setView(a, this._leaflet.getZoom(), { animate: false });
  };
  Map.prototype.getCenter = function () {
    var c = this._leaflet.getCenter(); return new LatLng(c.lat, c.lng);
  };
  Map.prototype.setZoom = function (z) { this._leaflet.setZoom(z, { animate: false }); };
  Map.prototype.getZoom = function () { return this._leaflet.getZoom(); };
  Map.prototype.getBounds = function () { return new LatLngBounds(this._leaflet.getBounds()); };
  Map.prototype.fitBounds = function (b) {
    this._leaflet.fitBounds(b && b._b ? b._b : b);
  };
  // panTo is the one that is *meant* to glide, so keep the animation here.
  Map.prototype.panTo = function (ll) {
    var a = toLatLngArray(ll);
    if (a) this._leaflet.panTo(a);
  };
  Map.prototype.setOptions = function (o) {
    if (!o) return;
    if (o.center) this.setCenter(o.center);
    if (o.zoom != null) this.setZoom(o.zoom);
    // toggleMapLight() flips the game's theme and then calls
    // setOptions({styles: ...}). The array itself is Google-specific and means
    // nothing here, but its arrival is the signal to re-read the theme.
    if (o.styles !== undefined) this.applyTheme(currentTheme());
  };

  /*
   * Some tile sources have no dark twin. Wikimedia's international style is the
   * one that matters here: it is the only free source that labels the world in
   * English rather than in each country's own language, which on a map you are
   * planning routes across is the difference between reading it and not - and
   * it comes in one palette only.
   *
   * So when light and dark would draw the same tiles, the dark theme is made in
   * the browser instead: the tile images are inverted and their hue rotated
   * back, which turns a light map into a credible dark one. Only the tile layer
   * is touched. Routes, aircraft and airport markers live in their own panes
   * and keep their real colours, which is the whole reason this is done with a
   * filter on one pane rather than on the map as a whole.
   */
  function darkFilterWanted(theme) {
    if (theme !== 'dark') return false;
    var mode = global.OSM_DARK_FILTER || 'auto';
    if (mode === 'on') return true;
    if (mode === 'off') return false;
    return tileUrlFor('dark') === tileUrlFor('light');   // auto
  }

  /** Swap the tile layer when the light/dark setting changes. */
  Map.prototype.applyTheme = function (theme) {
    if (theme === this._theme) return;
    this._theme = theme;
    if (this._tileLayer) this._leaflet.removeLayer(this._tileLayer);
    this._tileLayer = L.tileLayer(tileUrlFor(theme), {
      attribution: TILE_ATTRIBUTION,
      maxZoom: 19,
      crossOrigin: true
    }).addTo(this._leaflet);
    // Keep it under the markers and routes.
    if (this._tileLayer.bringToBack) this._tileLayer.bringToBack();

    var container = this._leaflet.getContainer();
    if (container && container.classList) {
      container.classList.toggle('osm-dark-filter', darkFilterWanted(theme));
    }
  };
  Map.prototype.addListener = function (ev, fn) { return bindListener(this, ev, fn, false); };
  Map.prototype.setMapTypeId = function () { /* no map types here */ };
  Map.prototype.getDiv = function () { return this._leaflet.getContainer(); };
  // Some callers reach for this after showing a previously hidden container.
  Map.prototype.invalidateSize = function () { this._leaflet.invalidateSize(); };

  // ------------------------------------------------------------------ Marker

  function buildIcon(icon, title) {
    if (!icon) return undefined;

    if (typeof icon === 'string') {
      return L.icon({ iconUrl: icon, iconSize: [24, 24], iconAnchor: [12, 12] });
    }
    if (icon.path !== undefined) {
      // A vector symbol (SymbolPath). Approximate with a small circle marker
      // image is overkill; let the caller fall back to the default pin.
      return undefined;
    }
    if (icon.url) {
      var size = icon.scaledSize || icon.size;
      var w = size ? (size.width || size.x || 24) : 24;
      var h = size ? (size.height || size.y || 24) : 24;
      var anchor = icon.anchor ? [icon.anchor.x, icon.anchor.y] : [w / 2, h / 2];
      return L.icon({
        iconUrl: icon.url,
        iconSize: [w, h],
        iconAnchor: anchor,
        title: title
      });
    }
    return undefined;
  }

  function Marker(opts) {
    opts = opts || {};
    var pos = toLatLngArray(opts.position) || [0, 0];
    var icon = buildIcon(opts.icon, opts.title);

    var markerOpts = {
      title: opts.title || '',
      opacity: opts.opacity != null ? opts.opacity : 1,
      zIndexOffset: opts.zIndex || 0,
      interactive: opts.clickable !== false,
      keyboard: false
    };
    // Only set `icon` when we actually built one. Leaflet copies every key it
    // is given over its defaults, so passing `icon: undefined` replaces the
    // default pin with undefined and _initIcon then dies on
    // "Cannot read properties of undefined (reading 'createIcon')".
    if (icon) markerOpts.icon = icon;

    this._leaflet = L.marker(pos, markerOpts);

    this._visible = opts.visible !== false;
    this._map = null;
    this._shimType = 'Marker';
    applyCustomOptions(this, opts);
    // The game hangs its own fields on markers (airport ids and so on); those
    // ride along on this object untouched, which is why we wrap rather than
    // return the Leaflet marker directly.
    if (opts.map) this.setMap(opts.map);
  }

  Marker.prototype.setMap = function (map) {
    if (map && map._leaflet) {
      if (this._visible) this._leaflet.addTo(map._leaflet);
      this._map = map;
    } else {
      if (this._map && this._map._leaflet) this._map._leaflet.removeLayer(this._leaflet);
      this._map = null;
    }
  };
  Marker.prototype.getMap = function () { return this._map; };
  Marker.prototype.setPosition = function (ll) {
    var a = toLatLngArray(ll); if (a) this._leaflet.setLatLng(a);
  };
  Marker.prototype.getPosition = function () {
    var p = this._leaflet.getLatLng(); return new LatLng(p.lat, p.lng);
  };
  Marker.prototype.setIcon = function (icon) {
    var built = buildIcon(icon, this._leaflet.options.title);
    if (built) this._leaflet.setIcon(built);
  };
  Marker.prototype.setTitle = function (t) {
    this._leaflet.options.title = t;
    var el = this._leaflet.getElement();
    if (el) el.title = t;
  };
  Marker.prototype.setOpacity = function (o) { this._leaflet.setOpacity(o); };
  Marker.prototype.setZIndex = function (z) { this._leaflet.setZIndexOffset(z); };
  Marker.prototype.setVisible = function (v) {
    this._visible = v;
    if (!this._map) return;
    if (v) this._leaflet.addTo(this._map._leaflet);
    else this._map._leaflet.removeLayer(this._leaflet);
  };
  Marker.prototype.getVisible = function () { return this._visible; };
  Marker.prototype.setOptions = function (o) {
    if (!o) return;
    if (o.position) this.setPosition(o.position);
    if (o.icon) this.setIcon(o.icon);
    if (o.title != null) this.setTitle(o.title);
    if (o.opacity != null) this.setOpacity(o.opacity);
    if (o.zIndex != null) this.setZIndex(o.zIndex);
    if (o.visible != null) this.setVisible(o.visible);
    if (o.map !== undefined) this.setMap(o.map);
  };
  Marker.prototype.addListener = function (ev, fn) { return bindListener(this, ev, fn, false); };

  // ---------------------------------------------------------------- Polyline

  function Polyline(opts) {
    opts = opts || {};
    var path = (opts.path || []).map(toLatLngArray).filter(Boolean);

    this._leaflet = L.polyline(path, pathStyle(opts));
    this._map = null;
    this._shimType = 'Polyline';
    applyCustomOptions(this, opts);
    // Google draws direction arrows via opts.icons. Leaflet has no equivalent
    // without another plugin, so routes render as plain lines. Cosmetic only.
    if (opts.map) this.setMap(opts.map);
  }

  Polyline.prototype.setMap = function (map) {
    if (map && map._leaflet) { this._leaflet.addTo(map._leaflet); this._map = map; }
    else {
      if (this._map && this._map._leaflet) this._map._leaflet.removeLayer(this._leaflet);
      this._map = null;
    }
  };
  Polyline.prototype.getMap = function () { return this._map; };
  Polyline.prototype.setPath = function (path) {
    this._leaflet.setLatLngs((path || []).map(toLatLngArray).filter(Boolean));
  };
  Polyline.prototype.getPath = function () {
    var pts = this._leaflet.getLatLngs();
    // Google returns an MVCArray; the game only ever calls getAt/getLength.
    return {
      getAt: function (i) { return new LatLng(pts[i].lat, pts[i].lng); },
      getLength: function () { return pts.length; },
      length: pts.length
    };
  };
  Polyline.prototype.setOptions = function (o) {
    if (!o) return;
    this._leaflet.setStyle(pathStyle(o));
    if (o.path) this.setPath(o.path);
    if (o.map !== undefined) this.setMap(o.map);
    if (o.zIndex !== undefined && this._leaflet.bringToFront && o.zIndex > 100) {
      this._leaflet.bringToFront();
    }
  };
  Polyline.prototype.setVisible = function (v) {
    var el = this._leaflet.getElement && this._leaflet.getElement();
    if (el) el.style.display = v ? '' : 'none';
    else this._leaflet.setStyle({ opacity: v ? 1 : 0 });
  };
  Polyline.prototype.addListener = function (ev, fn) { return bindListener(this, ev, fn, false); };

  // ------------------------------------------------------------------ Circle

  function Circle(opts) {
    opts = opts || {};
    var centre = toLatLngArray(opts.center) || [0, 0];
    this._leaflet = L.circle(centre, Object.assign({ radius: opts.radius || 0 }, pathStyle(opts)));
    this._map = null;
    this._shimType = 'Circle';
    applyCustomOptions(this, opts);
    if (opts.map) this.setMap(opts.map);
  }
  Circle.prototype.setMap = Polyline.prototype.setMap;
  Circle.prototype.getMap = function () { return this._map; };
  Circle.prototype.setCenter = function (ll) {
    var a = toLatLngArray(ll); if (a) this._leaflet.setLatLng(a);
  };
  Circle.prototype.setRadius = function (r) { this._leaflet.setRadius(r); };
  Circle.prototype.setOptions = function (o) {
    if (!o) return;
    this._leaflet.setStyle(pathStyle(o));
    if (o.center) this.setCenter(o.center);
    if (o.radius != null) this.setRadius(o.radius);
    if (o.map !== undefined) this.setMap(o.map);
  };
  Circle.prototype.addListener = function (ev, fn) { return bindListener(this, ev, fn, false); };

  // -------------------------------------------------------------- InfoWindow

  function InfoWindow(opts) {
    opts = opts || {};
    this._content = opts.content || '';
    this._position = toLatLngArray(opts.position);
    this._popup = L.popup({
      maxWidth: (opts.maxWidth || 500),
      autoPan: false,
      closeButton: true
    });
    this._map = null;
    this._shimType = 'InfoWindow';
    applyCustomOptions(this, opts);
    // Leaflet fires popupclose on the MAP, but the game listens on the
    // InfoWindow, so relay it across when the popup is opened.
    this._leaflet = this._popup;
  }

  InfoWindow.prototype.setContent = function (c) {
    this._content = c;
    if (this._popup.isOpen()) this._popup.setContent(c);
  };
  InfoWindow.prototype.getContent = function () { return this._content; };
  InfoWindow.prototype.setPosition = function (ll) {
    this._position = toLatLngArray(ll);
    if (this._position) this._popup.setLatLng(this._position);
  };
  InfoWindow.prototype.open = function (map, anchor) {
    var target = map && map._leaflet ? map._leaflet : (map && map.getMap ? null : null);
    if (!target && anchor && anchor._map) target = anchor._map._leaflet;
    if (!target) return;

    var at = this._position;
    if (!at && anchor && anchor._leaflet && anchor._leaflet.getLatLng) {
      var p = anchor._leaflet.getLatLng();
      at = [p.lat, p.lng];
    }
    if (!at) return;

    this._popup.setLatLng(at).setContent(this._content).openOn(target);
    this._map = map;

    // Relay the close so google.maps.event.addListener(infoWindow,'closeclick')
    // behaves the way the game expects.
    var self = this;
    target.once('popupclose', function (e) {
      if (e.popup === self._popup) self._popup.fire('popupclose');
    });
  };
  InfoWindow.prototype.close = function () {
    if (this._map && this._map._leaflet) this._map._leaflet.closePopup(this._popup);
    else if (this._popup.remove) this._popup.remove();

    // Leaflet fades a popup out over ~200ms and only then detaches it, while
    // Google's close() is immediate. Code that closes one popup and opens
    // another straight away would otherwise briefly see both. Detach now;
    // Leaflet's own delayed removal is guarded against a missing parent.
    var el = this._popup.getElement && this._popup.getElement();
    if (el && el.parentNode) el.parentNode.removeChild(el);
  };
  InfoWindow.prototype.addListener = function (ev, fn) { return bindListener(this, ev, fn, false); };

  // ------------------------------------------------------------- HeatmapLayer
  //
  // The game replaced Google's heatmap with deck.gl, which binds to Google via
  // deck.GoogleMapsOverlay. That overlay cannot attach to Leaflet, so the
  // heatmap screen is inert here rather than broken - every other screen works.

  function HeatmapLayer(opts) {
    this._shimType = 'HeatmapLayer';
    this._opts = opts || {};
    console.info('[osm-shim] heatmap is not available on the OpenStreetMap map');
  }
  HeatmapLayer.prototype.setMap = function () {};
  HeatmapLayer.prototype.setData = function () {};
  HeatmapLayer.prototype.setOptions = function () {};

  // ----------------------------------------------------------------- exports

  var googleMaps = {
    Map: Map,
    Marker: Marker,
    Polyline: Polyline,
    Circle: Circle,
    InfoWindow: InfoWindow,
    LatLng: LatLng,
    LatLngBounds: LatLngBounds,
    Point: Point,

    // These MUST match the keys of CONTROL_PLACEMENT above. They did not:
    // RIGHT_BOTTOM resolved to 2 (top-right) and BOTTOM_* to 3, which has no
    // container at all - so map.controls[BOTTOM_LEFT].push threw "Cannot read
    // properties of undefined", aborting initMap half way and leaving the top
    // status bar empty.
    ControlPosition: {
      TOP: 0, TOP_CENTER: 0,
      TOP_LEFT: 1,
      TOP_RIGHT: 2,
      LEFT: 3, LEFT_CENTER: 3,
      LEFT_BOTTOM: 4, BOTTOM_LEFT: 4,
      RIGHT: 5, RIGHT_CENTER: 5,
      RIGHT_BOTTOM: 6, BOTTOM_RIGHT: 6,
      BOTTOM: 7, BOTTOM_CENTER: 7,
      LEFT_TOP: 8,
      RIGHT_TOP: 9
    },

    SymbolPath: {
      FORWARD_CLOSED_ARROW: 'forward_closed_arrow',
      FORWARD_OPEN_ARROW: 'forward_open_arrow',
      BACKWARD_CLOSED_ARROW: 'backward_closed_arrow',
      BACKWARD_OPEN_ARROW: 'backward_open_arrow',
      CIRCLE: 'circle'
    },

    event: {
      addListener: function (i, e, f) { return bindListener(i, e, f, false); },
      addListenerOnce: function (i, e, f) { return bindListener(i, e, f, true); },
      removeListener: function (h) { if (h && h.remove) h.remove(); },
      clearListeners: function (i, e) {
        var t = i && i._leaflet ? i._leaflet : i;
        if (t && t.off) { if (e && EVENT_MAP[e]) t.off(EVENT_MAP[e]); else t.off(); }
      },
      clearInstanceListeners: function (i) {
        var t = i && i._leaflet ? i._leaflet : i;
        if (t && t.off) t.off();
      },
      trigger: function (i, e) {
        var t = i && i._leaflet ? i._leaflet : i;
        var mapped = EVENT_MAP[e];
        if (t && t.fire && mapped) t.fire(mapped);
        // 'resize' is the game telling the map its container changed size.
        if (e === 'resize' && t && t.invalidateSize) t.invalidateSize();
      }
    },

    geometry: {
      spherical: {
        // Great-circle interpolation, same contract as Google's: fraction 0..1
        // between two points. Used to draw curved flight paths.
        interpolate: function (from, to, fraction) {
          var a = toLatLngArray(from), b = toLatLngArray(to);
          if (!a || !b) return null;
          var toRad = Math.PI / 180, toDeg = 180 / Math.PI;
          var lat1 = a[0] * toRad, lon1 = a[1] * toRad;
          var lat2 = b[0] * toRad, lon2 = b[1] * toRad;

          var dLat = lat2 - lat1, dLon = lon2 - lon1;
          var sinHalfLat = Math.sin(dLat / 2), sinHalfLon = Math.sin(dLon / 2);
          var h = sinHalfLat * sinHalfLat +
                  Math.cos(lat1) * Math.cos(lat2) * sinHalfLon * sinHalfLon;
          var d = 2 * Math.asin(Math.min(1, Math.sqrt(h)));
          if (d === 0) return new LatLng(a[0], a[1]);

          var A = Math.sin((1 - fraction) * d) / Math.sin(d);
          var B = Math.sin(fraction * d) / Math.sin(d);
          var x = A * Math.cos(lat1) * Math.cos(lon1) + B * Math.cos(lat2) * Math.cos(lon2);
          var y = A * Math.cos(lat1) * Math.sin(lon1) + B * Math.cos(lat2) * Math.sin(lon2);
          var z = A * Math.sin(lat1) + B * Math.sin(lat2);

          return new LatLng(
            Math.atan2(z, Math.sqrt(x * x + y * y)) * toDeg,
            Math.atan2(y, x) * toDeg
          );
        },

        computeDistanceBetween: function (from, to) {
          var a = toLatLngArray(from), b = toLatLngArray(to);
          if (!a || !b) return 0;
          return L.latLng(a[0], a[1]).distanceTo(L.latLng(b[0], b[1]));
        }
      }
    },

    visualization: { HeatmapLayer: HeatmapLayer },

    MapTypeId: { ROADMAP: 'roadmap', SATELLITE: 'satellite', HYBRID: 'hybrid', TERRAIN: 'terrain' }
  };

  global.google = global.google || {};
  global.google.maps = googleMaps;

  // The game's index page passes callback=initMap to Google's loader. Nothing
  // calls it for us, so do it once the document is ready.
  function fireInitMap() {
    if (typeof global.initMap === 'function') {
      try { global.initMap(); }
      catch (err) { console.error('[osm-shim] initMap threw:', err); }
    }
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', fireInitMap);
  } else {
    setTimeout(fireInitMap, 0);
  }

  console.info('[osm-shim] OpenStreetMap map active - no Google API key in use');
})(window);

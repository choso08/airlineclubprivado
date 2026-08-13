/*
 * The card that opens when you click an aircraft on the map.
 *
 * The dots were unclickable, so a flight in the air was the one thing on the
 * screen you could not ask anything about - you could see that something was
 * flying between two cities and nothing else.
 *
 * It is anchored to a corner of the map rather than to the aircraft itself.
 * The aircraft is moving, and a card that chases it is unreadable; worse, the
 * two map providers give pixel positions differently, so pinning it to the
 * aircraft would work under one and not the other.
 *
 * Figures come from what the game already sends for the route. Weekly totals
 * are divided by the number of flights to give the figure for this one, which
 * is what someone clicking a single aeroplane is asking about.
 */
(function (global) {
  'use strict';

  var refreshTimer = null;
  var openLink = null;
  var openMarker = null;

  function money(value) {
    if (value === undefined || value === null || isNaN(value)) return '-';
    var rounded = Math.round(value);
    var sign = rounded < 0 ? '-$' : '$';
    return sign + Math.abs(rounded).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  function number(value) {
    if (value === undefined || value === null || isNaN(value)) return '-';
    return Math.round(value).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  function hoursAndMinutes(minutes) {
    var m = Math.max(0, Math.round(minutes));
    var h = Math.floor(m / 60);
    return h > 0 ? h + 'h ' + (m % 60) + 'm' : m + 'm';
  }

  /** Sunday 00:00 plus n minutes, as the game would show it. */
  function weekTime(minute) {
    var days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    var wrapped = ((minute % (7 * 24 * 60)) + 7 * 24 * 60) % (7 * 24 * 60);
    var day = Math.floor(wrapped / (24 * 60));
    var hour = Math.floor((wrapped % (24 * 60)) / 60);
    var min = wrapped % 60;
    return days[day] + ' ' + (hour < 10 ? '0' : '') + hour + ':' + (min < 10 ? '0' : '') + min;
  }

  function panel() {
    var existing = document.getElementById('flightPopup');
    if (existing) return existing;

    var el = document.createElement('div');
    el.id = 'flightPopup';
    el.className = 'flight-popup';
    el.style.display = 'none';
    el.innerHTML =
      '<span class="flight-popup-close" title="close">&times;</span>' +
      '<div class="flight-popup-title"></div>' +
      '<img class="flight-popup-image" alt="">' +
      '<div class="flight-popup-body"></div>';
    document.body.appendChild(el);
    el.querySelector('.flight-popup-close').addEventListener('click', close);
    return el;
  }

  function close() {
    var el = document.getElementById('flightPopup');
    if (el) el.style.display = 'none';
    if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null; }
    openLink = null;
    openMarker = null;
  }

  function row(label, value) {
    return '<div class="flight-popup-row"><span>' + label + '</span><span>' + value + '</span></div>';
  }

  function render() {
    if (!openLink || !openMarker) return;
    var el = panel();
    var link = openLink;

    var info = openMarker.flightInfo;
    if (!info) {
      // It landed while the card was open. Say so rather than freezing on the
      // last position, which reads as the card having stopped working.
      el.querySelector('.flight-popup-body').innerHTML =
        row('Status', 'On the ground') +
        row('Next departure', weekTime(openMarker.departureMinute));
      return;
    }

    var progress = info.progress;
    var flownMinutes = progress.fraction * link.duration;
    var leftMinutes = link.duration - flownMinutes;

    var origin = progress.outbound ? link.fromAirportCity : link.toAirportCity;
    var destination = progress.outbound ? link.toAirportCity : link.fromAirportCity;
    var originCode = progress.outbound ? link.fromAirportCode : link.toAirportCode;
    var destinationCode = progress.outbound ? link.toAirportCode : link.fromAirportCode;

    // Weekly figures divided by the number of flights. The frequency counts
    // one direction, and the return leg is the same aeroplane on the same
    // route, so this is the money for this leg.
    var frequency = link.frequency || 1;
    var passengers = link.passengers !== undefined
      ? (link.passengers.economy || 0) + (link.passengers.business || 0) + (link.passengers.first || 0)
      : undefined;
    var seats = link.capacity
      ? (link.capacity.economy || 0) + (link.capacity.business || 0) + (link.capacity.first || 0)
      : undefined;
    var costs = (link.revenue !== undefined && link.profit !== undefined)
      ? link.revenue - link.profit : undefined;

    el.querySelector('.flight-popup-title').innerHTML =
      (link.flightCode || 'Flight') + ' &nbsp;' + originCode + ' &rarr; ' + destinationCode;

    var image = el.querySelector('.flight-popup-image');
    if (link.modelName) {
      image.src = 'assets/images/airplanes/' + link.modelName.replace(/\s+/g, '-').toLowerCase() + '.png';
      image.style.display = '';
      image.onerror = function () { image.style.display = 'none'; };
    } else {
      image.style.display = 'none';
    }

    el.querySelector('.flight-popup-body').innerHTML =
      row('Route', origin + ' to ' + destination) +
      row('Aircraft', link.modelName || '-') +
      row('Departed', weekTime(openMarker.departureMinute + (progress.outbound ? 0 : link.duration + Math.min(60, link.duration * 0.2)))) +
      row('Lands', weekTime(openMarker.departureMinute + (progress.outbound ? link.duration : 2 * link.duration + Math.min(60, link.duration * 0.2)))) +
      row('Flown', Math.round(progress.fraction * 100) + '% &middot; ' + hoursAndMinutes(leftMinutes) + ' to go') +
      (passengers !== undefined
        ? row('Passengers', number(passengers / frequency) + (seats ? ' of ' + number(seats / frequency) + ' seats' : ''))
        : '') +
      (link.revenue !== undefined ? row('Earns', money(link.revenue / frequency)) : '') +
      (costs !== undefined ? row('Costs', money(costs / frequency)) : '') +
      (link.profit !== undefined ? row('Profit', money(link.profit / frequency)) : '') +
      row('This week', number(frequency) + ' flight(s), ' + hoursAndMinutes(link.duration) + ' each way');
  }

  /** Open the card for an aircraft on the map. */
  function show(link, marker) {
    openLink = link;
    openMarker = marker;

    var el = panel();
    el.style.display = 'block';
    render();

    if (refreshTimer) clearInterval(refreshTimer);
    // The aeroplane keeps moving while the card is open, so the progress and
    // the time remaining have to keep up with it.
    refreshTimer = setInterval(render, 1000);

    if (global.airlineTranslate) global.airlineTranslate.apply(el);
  }

  global.showFlightPopup = show;
  global.closeFlightPopup = close;

})(window);

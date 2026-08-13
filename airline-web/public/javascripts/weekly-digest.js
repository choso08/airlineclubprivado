/*
 * What happened in the week that just finished.
 *
 * A cycle is the whole game - passengers fly, money moves, somebody's new
 * route turns out to be a mistake - and none of it was visible anywhere. The
 * game shows you your own numbers and a ranking table, so unless you go
 * looking, a week passing looks exactly like a week not passing.
 *
 * It is about everyone rather than about you, because five people sharing a
 * world mostly want to know what the other four did, and the rest of the
 * interface answers that one airline at a time through the rivals panel.
 *
 * The panel is built here rather than in the page template so that it needs
 * nothing but the one link that opens it.
 */
(function (global) {
  'use strict';

  function money(value) {
    if (value === undefined || value === null || isNaN(value)) return '-';
    var rounded = Math.round(value);
    var sign = rounded < 0 ? '-$' : '$';
    return sign + Math.abs(rounded).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  function row(cells) {
    var html = '<div class="table-row">';
    for (var i = 0; i < cells.length; i++) {
      html += '<div class="cell"' + (cells[i].right ? " align='right'" : '') +
        (cells[i].width ? ' style="width: ' + cells[i].width + '"' : '') + '>' +
        cells[i].text + '</div>';
    }
    return html + '</div>';
  }

  function escape(text) {
    return $('<div>').text(text === undefined || text === null ? '' : text).html();
  }

  function section(title, headerCells, rows, emptyText) {
    var html = '<h4 style="margin-top: 12px;">' + title + '</h4>';
    if (!rows.length) {
      return html + '<div class="remarks">' + emptyText + '</div>';
    }
    html += '<div class="table data" style="width: 100%;"><div class="table-header">';
    for (var i = 0; i < headerCells.length; i++) {
      html += '<div class="cell"' + (headerCells[i].right ? " align='right'" : '') +
        (headerCells[i].width ? ' style="width: ' + headerCells[i].width + '"' : '') + '>' +
        headerCells[i].text + '</div>';
    }
    html += '</div>' + rows.join('') + '</div>';
    return html;
  }

  function panel() {
    var existing = document.getElementById('weeklyDigestPanel');
    if (existing) {
      return $(existing);
    }
    var $panel = $(
      '<div id="weeklyDigestPanel" class="section" style="display: none; position: fixed; ' +
      'top: 60px; right: 20px; width: 460px; max-height: 75%; overflow-y: auto; z-index: 60;">' +
      '<span class="button" style="float: right;" onclick="closeWeeklyDigest()">&#10006;</span>' +
      '<h3 class="digestTitle">Last week</h3>' +
      '<div class="digestBody"></div>' +
      '</div>');
    $('body').append($panel);
    return $panel;
  }

  function render(digest) {
    var $panel = panel();
    $panel.find('.digestTitle').text('Week ' + digest.cycle);

    var airlineRows = (digest.airlines || []).map(function (entry) {
      return row([
        { text: escape(entry.airlineName), width: '55%' },
        { text: money(entry.profit), right: true, width: '45%' }
      ]);
    });

    var routeRows = (digest.routes || []).map(function (entry) {
      return row([
        { text: escape(entry.from + ' - ' + entry.to), width: '30%' },
        { text: escape(entry.airlineName), width: '40%' },
        { text: money(entry.profit), right: true, width: '30%' }
      ]);
    });

    var allianceRows = (digest.alliances || []).map(function (entry) {
      return row([
        { text: escape(entry.airlineName + ' ' + entry.event + ' ' + entry.allianceName) }
      ]);
    });

    var html =
      section('Airlines by profit',
        [{ text: 'Airline', width: '55%' }, { text: 'Profit', right: true, width: '45%' }],
        airlineRows, 'No figures for this week yet') +
      section('Most profitable routes',
        [{ text: 'Route', width: '30%' }, { text: 'Airline', width: '40%' }, { text: 'Profit', right: true, width: '30%' }],
        routeRows, 'Nothing flew') +
      section('Alliances',
        [{ text: 'Event' }],
        allianceRows, 'Nothing happened');

    $panel.find('.digestBody').html(html);
    $panel.show();
  }

  global.showWeeklyDigest = function () {
    var $panel = panel();
    if ($panel.is(':visible')) {
      $panel.hide();
      return;
    }
    $.ajax({
      type: 'GET',
      url: 'weekly-digest',
      contentType: 'application/json; charset=utf-8',
      dataType: 'json',
      success: render,
      error: function (jqXHR) {
        console.log('Could not load the weekly digest: ' + JSON.stringify(jqXHR));
      }
    });
  };

  global.closeWeeklyDigest = function () {
    $('#weeklyDigestPanel').hide();
  };
})(window);

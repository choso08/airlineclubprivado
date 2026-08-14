/*
 * The board of firsts.
 *
 * A public server has thousands of players and a world ranking that means
 * something to be near the top of. Five friends have neither: there is nothing
 * to be first at, and nothing to point to afterwards. This is the smallest
 * thing that fixes it - a short list of milestones, each claimed once, kept
 * for good, with the name of whoever got there first.
 *
 * The ones nobody has taken are the point of looking, so they are shown too,
 * greyed out, in order of how hard they are.
 *
 * Built here rather than in the page template so that it needs nothing but the
 * one link that opens it.
 */
(function (global) {
  'use strict';

  function panel() {
    var existing = document.getElementById('achievementsPanel');
    if (existing) {
      return $(existing);
    }
    var $panel = $(
      '<div id="achievementsPanel" class="section" style="display: none; position: fixed; ' +
      'top: 60px; right: 20px; width: 460px; max-height: 75%; overflow-y: auto; z-index: 60;">' +
      '<span class="button" style="float: right;" onclick="closeAchievements()">&#10006;</span>' +
      '<h3>Firsts</h3>' +
      '<div class="achievementsBody"></div>' +
      '</div>');
    $('body').append($panel);
    return $panel;
  }

  function weeksAgoText(entry) {
    if (entry.weeksAgo === 0) return 'this week';
    if (entry.weeksAgo === 1) return '1 week ago';
    return entry.weeksAgo + ' weeks ago';
  }

  function render(result) {
    var $panel = panel();
    var entries = result.achievements || [];

    var taken = 0;
    var html = '<div class="table data" style="width: 100%;">';
    $.each(entries, function (index, entry) {
      if (entry.claimed) taken++;
      var dim = entry.claimed ? '' : ' style="opacity: 0.45;"';
      var who = entry.claimed
        ? '<span class="label">' + entry.airlineName + '</span> &middot; ' + weeksAgoText(entry)
        : '<span class="label">Nobody yet</span>';
      html +=
        '<div class="table-row"' + dim + '>' +
        '<div class="cell" style="width: 55%;">' +
        '<h5>' + entry.title + '</h5>' +
        '<span style="font-size: 0.85em;">' + entry.description + '</span>' +
        '</div>' +
        '<div class="cell" style="width: 45%;">' + who + '</div>' +
        '</div>';
    });
    html += '</div>';

    if (entries.length > 0) {
      html = '<h5 style="margin-bottom: 8px;">' + taken + ' of ' + entries.length + ' claimed</h5>' + html;
    }

    $panel.find('.achievementsBody').html(html);
    if (global.airlineTranslate && global.airlineTranslate.apply) {
      global.airlineTranslate.apply($panel.get(0));
    }
    $panel.show();
  }

  global.showAchievements = function () {
    var $panel = panel();
    if ($panel.is(':visible')) {
      $panel.hide();
      return;
    }
    $.ajax({
      type: 'GET',
      url: 'achievements',
      contentType: 'application/json; charset=utf-8',
      dataType: 'json',
      success: render,
      error: function (jqXHR) {
        console.log('Could not load the achievements: ' + JSON.stringify(jqXHR));
      }
    });
  };

  global.closeAchievements = function () {
    $('#achievementsPanel').hide();
  };
})(window);

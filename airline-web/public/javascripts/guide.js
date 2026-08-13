/*
 * The beginner's guide, in one panel you can read end to end.
 *
 * The game's own tutorials are pop-ups that appear when you happen to trigger
 * them, which teaches in the order things happen rather than in the order
 * somebody needs to learn them - and there is no way to go back and read the
 * one you dismissed. This is the whole thing, on demand, from the top.
 *
 * The text lives in public/html/guide.<language>.html so it can be rewritten
 * without touching any code. A language with no file of its own falls back to
 * Portuguese, which is the one that exists.
 */
(function (global) {
  'use strict';

  var FALLBACK = 'pt';
  var loaded = null;

  function language() {
    try {
      var chosen = $.cookie ? $.cookie('gameLanguage') : null;
      return chosen || (window.GAME_LANGUAGE || FALLBACK);
    } catch (e) {
      return FALLBACK;
    }
  }

  function panel() {
    var existing = document.getElementById('guidePanel');
    if (existing) {
      return $(existing);
    }
    var $panel = $(
      '<div id="guidePanel" class="section" style="display: none; position: fixed; ' +
      'top: 50px; left: 50%; transform: translateX(-50%); width: 640px; max-width: 92vw; ' +
      'max-height: 82%; overflow-y: auto; z-index: 60; padding: 16px 20px;">' +
      '<span class="button" style="float: right;" onclick="closeGuide()">&#10006;</span>' +
      '<div class="guideContent"></div>' +
      '</div>');
    $('body').append($panel);
    return $panel;
  }

  global.showGuide = function () {
    var $panel = panel();
    if ($panel.is(':visible')) {
      $panel.hide();
      return;
    }
    $panel.show();

    if (loaded) {
      return;   // the text does not change between openings
    }
    var file = 'assets/html/guide.' + language() + '.html';
    $panel.find('.guideContent').load(file, function (response, status) {
      if (status === 'error' && language() !== FALLBACK) {
        $panel.find('.guideContent').load('assets/html/guide.' + FALLBACK + '.html');
      }
      loaded = true;
    });
  };

  global.closeGuide = function () {
    $('#guidePanel').hide();
  };
})(window);

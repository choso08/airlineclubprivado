/*
 * A filter box for the long dropdowns.
 *
 * The aircraft model list on a route is the worst of them: every model that
 * can fly the route, ordered by frequency, which on a medium-haul leg is
 * eighty-odd entries. Picking a specific aircraft out of that means scrolling
 * a list you cannot search - the browser's own type-ahead only matches from
 * the start of the name, so typing "737" finds nothing at all.
 *
 * This puts a text box above such a dropdown and narrows it as you type,
 * matching anywhere in the name. Two details matter more than they look:
 *
 *   - options are hidden rather than removed. Removing one makes the browser
 *     silently move the selection to whatever is left, which on the route
 *     planner would quietly change the aircraft the player is costing out.
 *   - the game rebuilds these lists whenever the route changes, so the filter
 *     has to notice and start again rather than hold a stale set of options.
 */
(function (global) {
  'use strict';

  // Below this a filter box is clutter rather than help.
  var MIN_OPTIONS = 8;

  /** Fold accents and case, so "cessna" finds "Cessna" and "citacao" would
   *  find "Citação". Players type without accents far more often than with. */
  function normalise(text) {
    var s = (text || '').toLowerCase();
    if (s.normalize) s = s.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    return s;
  }

  function State(select) {
    this.select = select;
    this.box = null;
  }

  /**
   * Match the dropdown's width.
   *
   * The box used to be 100% wide, which is 100% of whatever cell the dropdown
   * sits in - and in the route planner that is the width of the whole dialog.
   * The result was a grey bar stretching across the panel with a narrow
   * dropdown underneath it, which reads as two unrelated controls and made a
   * mess of the layout around them.
   *
   * Measured rather than guessed, and measured again on every rebuild: the
   * dropdown is only as wide as the longest aircraft name in it, and it is
   * zero while the dialog is closed.
   */
  State.prototype.matchWidth = function () {
    var width = this.select.getBoundingClientRect().width;
    if (width > 0) {
      this.box.style.width = Math.round(width) + 'px';
    }
  };

  /**
   * Hide what does not match.
   *
   * Hidden rather than removed. Removing an option makes the browser silently
   * move the selection to whatever is left - on the route planner that
   * quietly changes the aircraft being costed out - so the selected one used
   * to be kept in the list whatever was typed. Which is worse: you filter for
   * "dhc" and something called Cessna is still sitting there, and the box
   * looks broken. Hiding keeps the selection intact without showing it.
   */
  State.prototype.render = function () {
    var term = normalise(this.box.value).trim();
    var shown = 0;

    for (var i = 0; i < this.select.options.length; i++) {
      var option = this.select.options[i];
      var match = !term || normalise(option.textContent).indexOf(term) !== -1;
      option.hidden = !match;
      // Belt and braces: not every browser has always honoured the attribute
      // on an option, and all of them honour this.
      option.style.display = match ? '' : 'none';
      if (match) {
        shown++;
      }
    }

    // Say so rather than leaving an apparently broken box.
    this.box.classList.toggle('select-search-empty', term !== '' && shown === 0);
  };

  State.prototype.refresh = function () {
    // A rebuild means a different route or a different airline; the old filter
    // is about the old list.
    this.box.value = '';
    this.box.classList.remove('select-search-empty');
    this.box.style.display = this.select.options.length >= MIN_OPTIONS ? '' : 'none';
    this.matchWidth();
  };

  /**
   * Give a dropdown a filter box. Safe to call more than once on the same
   * element - the second call does nothing.
   */
  function attach(selectId, placeholder) {
    var select = document.getElementById(selectId);
    if (!select || select.dataset.searchable === 'yes') return;
    select.dataset.searchable = 'yes';

    var state = new State(select);

    var box = document.createElement('input');
    box.type = 'text';
    box.className = 'select-search';
    box.placeholder = placeholder || 'filter';
    box.autocomplete = 'off';
    // Otherwise a stray Enter submits whatever form the dialog sits in.
    box.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') e.preventDefault();
    });
    box.addEventListener('input', function () { state.render(); });
    state.box = box;

    select.parentNode.insertBefore(box, select);

    state.refresh();

    if (typeof MutationObserver !== 'undefined') {
      new MutationObserver(function () { state.refresh(); })
        .observe(select, { childList: true });
    }
  }

  global.attachSelectSearch = attach;

  document.addEventListener('DOMContentLoaded', function () {
    // The aircraft list on a route - the long one - and the departure airport
    // list, which grows with every base an airline builds.
    attach('planLinkModelSelect', 'search aircraft');
    attach('planLinkFromAirportSelect', 'search airport');
  });

})(window);

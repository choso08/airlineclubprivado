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
 *   - the currently selected option is never filtered away. Removing it would
 *     make the browser silently move the selection to whatever is left, which
 *     on the route planner would quietly change the aircraft the player is
 *     costing out.
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
    this.options = [];       // every option, as the game last built them
    this.suppress = false;   // our own rebuilds must not look like the game's
    this.box = null;
  }

  State.prototype.capture = function () {
    this.options = Array.prototype.slice.call(this.select.options);
  };

  State.prototype.render = function () {
    var term = normalise(this.box.value).trim();
    var selected = this.select.value;

    this.suppress = true;
    while (this.select.firstChild) this.select.removeChild(this.select.firstChild);

    var shown = 0;
    for (var i = 0; i < this.options.length; i++) {
      var option = this.options[i];
      // Keep the selection whatever the filter says - see the note above.
      var keep = !term || option.value === selected ||
                 normalise(option.textContent).indexOf(term) !== -1;
      if (keep) {
        this.select.appendChild(option);
        if (normalise(option.textContent).indexOf(term) !== -1 || !term) shown++;
      }
    }
    this.select.value = selected;
    this.suppress = false;

    // Say so rather than leaving an apparently broken box.
    this.box.classList.toggle('select-search-empty', term !== '' && shown === 0);
  };

  State.prototype.refresh = function () {
    if (this.suppress) return;
    this.capture();
    // A rebuild means a different route or a different airline; the old filter
    // is about the old list.
    this.box.value = '';
    this.box.classList.remove('select-search-empty');
    this.box.style.display = this.options.length >= MIN_OPTIONS ? '' : 'none';
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

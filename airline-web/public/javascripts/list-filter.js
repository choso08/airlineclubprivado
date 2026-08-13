/*
 * A filter box above the long lists.
 *
 * The aircraft market is a hundred and forty models, the hangar is every
 * aeroplane anybody owns, and the route list grows for as long as you play.
 * All three are sortable and none of them was searchable, so finding one thing
 * meant scrolling and hoping - which is the complaint this answers.
 *
 * It filters what is already on the page rather than asking the server, so it
 * is instant and works on lists the server has no search for. Accents and case
 * are ignored, and several words all have to match, in any order and in any
 * column: "boeing 737" finds the 737s among the Boeings, "lis" finds every
 * route touching Lisbon.
 *
 * The lists are rebuilt from scratch whenever the game refreshes them, which
 * would drop the filter every cycle, so each box watches its own list and
 * re-applies itself when the rows are replaced.
 */
(function (global) {
  'use strict';

  function fold(text) {
    return (text || '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toUpperCase();
  }

  function terms(value) {
    return fold(value).split(/\s+/).filter(function (term) { return term.length > 0 });
  }

  function textMatches(text, wanted) {
    for (var i = 0; i < wanted.length; i++) {
      if (text.indexOf(wanted[i]) === -1) {
        return false;
      }
    }
    return true;
  }

  /**
   * Put a filter box above a list.
   *
   * container   the element whose children are the rows
   * rows        selector for the rows inside it; the header is never hidden
   * insertBefore where the box goes, if not immediately above the container
   * toggle      an optional checkbox: { label, test(row) }
   */
  function attach(options) {
    var $container = $(options.container);
    if (!$container.length || $container.data('listFilterAttached')) {
      return;
    }
    $container.data('listFilterAttached', true);

    var rowSelector = options.rows || '> div.table-row';
    var $box = $('<div class="listFilter" style="margin: 4px 0 6px 0;"></div>');
    var $input = $('<input type="text" class="listFilterInput" style="width: 220px;"/>');
    $input.attr('placeholder', options.placeholder || 'Filter');
    $box.append($input);

    var $toggle;
    if (options.toggle) {
      var $label = $('<label style="margin-left: 12px; cursor: pointer;"></label>');
      $toggle = $('<input type="checkbox" style="vertical-align: middle;"/>');
      $label.append($toggle).append('<span style="vertical-align: middle;">&nbsp;' + options.toggle.label + '</span>');
      $box.append($label);
    }

    var $count = $('<span class="remarks" style="margin-left: 12px;"></span>');
    $box.append($count);

    var $anchor = options.insertBefore ? $(options.insertBefore) : $container;
    $anchor.first().before($box);

    function apply() {
      var wanted = terms($input.val());
      var onlyToggle = $toggle && $toggle.is(':checked');
      var shown = 0;
      var total = 0;

      $container.find(rowSelector).each(function () {
        var $row = $(this);
        total++;
        var visible = textMatches(fold($row.text()), wanted);
        if (visible && onlyToggle && options.toggle.test) {
          visible = !!options.toggle.test($row);
        }
        // display is set rather than using .hide(), because these lists are
        // laid out with flex in the hangar and jQuery's hide/show would put
        // the sections back as blocks.
        $row.css('display', visible ? '' : 'none');
        if (visible) {
          shown++;
        }
      });

      if (wanted.length === 0 && !onlyToggle) {
        $count.text('');
      } else if (shown === 0) {
        $count.text('nothing matches');
      } else {
        $count.text(shown + ' of ' + total);
      }
    }

    $input.on('input', apply);
    if ($toggle) {
      $toggle.on('change', apply);
    }

    // The game replaces these rows wholesale on every refresh. Watching for
    // that is what keeps a filter typed a minute ago from silently lapsing -
    // and only childList is watched, so the hiding done above cannot set this
    // off again.
    if (global.MutationObserver) {
      var pending = null;
      var observer = new MutationObserver(function () {
        if (pending) {
          return;
        }
        pending = setTimeout(function () {
          pending = null;
          apply();
        }, 20);
      });
      observer.observe($container[0], { childList: true });
    }

    return apply;
  }

  global.attachListFilter = attach;

  $(document).ready(function () {
    // The market: a hundred and forty models, and the one you want is a name
    // you already know.
    attach({
      container: '#airplaneModelTable',
      insertBefore: $('#airplaneModelSortHeader').closest('.table.data'),
      placeholder: 'Filter aircraft'
    });

    // The hangar: one section per model, so filtering the sections is what
    // finds an aeroplane.
    attach({
      container: '#airplaneCanvas .hangar .sectionContainer',
      rows: '> div',
      placeholder: 'Filter aircraft'
    });

    // Routes, where the useful question is usually not "which route" but
    // "which ones are losing money".
    attach({
      container: '#linksTable',
      insertBefore: $('#linksTableSortHeader').closest('.table.data'),
      placeholder: 'Filter routes',
      toggle: {
        label: 'Losing money',
        test: function ($row) {
          var link = $row.data('link');
          return link && link.profit < 0;
        }
      }
    });
  });
})(window);

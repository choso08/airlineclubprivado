/*
 * Freight contracts.
 *
 * An aircraft's hold flies empty every week of its life in this game, and the
 * only thing an airline can sell is a seat. Cargo added as "your routes now
 * also earn a bit" would be money arriving without a decision; as contracts it
 * is a decision - hold space promised away on a route you now have to keep
 * flying, paid for steadily whatever the passengers do that week.
 */
(function (global) {
  'use strict';

  function money(value) {
    var rounded = Math.round(value || 0);
    return '$' + rounded.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  function panel() {
    var existing = document.getElementById('cargoPanel');
    if (existing) {
      return $(existing);
    }
    var $panel = $(
      '<div id="cargoPanel" class="section" style="display: none; position: fixed; ' +
      'top: 60px; right: 20px; width: 520px; max-height: 80%; overflow-y: auto; z-index: 60;">' +
      '<span class="button" style="float: right;" onclick="closeCargo()">&#10006;</span>' +
      '<h3>Cargo</h3>' +
      '<div class="cargoBody"></div>' +
      '</div>');
    $('body').append($panel);
    return $panel;
  }

  function render(result) {
    var $body = panel().find('.cargoBody');
    if (!result.enabled) {
      $body.html('<h5>Cargo is switched off in this world.</h5>');
      return;
    }
    var contracts = result.contracts || [];
    if (contracts.length === 0) {
      $body.html('<h5>Nothing offered yet. Offers turn up on routes you already fly.</h5>');
      return;
    }

    var html = '<div class="table data" style="width: 100%;">';
    $.each(contracts, function (index, contract) {
      var buttons = '';
      if (contract.status === 'OFFERED') {
        var canCarry = contract.capacityNow >= contract.tonnesPerWeek;
        buttons =
          (canCarry
            ? '<div class="button" onclick="respondToCargo(' + contract.id + ', \'accept\')">Accept</div> '
            : '<span class="label">Hold too small (' + contract.capacityNow + 't)</span> ') +
          '<div class="button" onclick="respondToCargo(' + contract.id + ', \'decline\')">Decline</div>';
      } else if (contract.status === 'ACCEPTED') {
        buttons =
          '<span class="label">' + contract.weeksLeft + ' week(s) left</span> ' +
          '<div class="button" onclick="cancelCargo(' + contract.id + ', ' + contract.pricePerWeek * 4 + ')">Break it</div>';
      } else {
        buttons = '<span class="label">' + contract.statusLabel + '</span>';
      }

      var warning = ''
      if (contract.status === 'ACCEPTED') {
        if (contract.capacityNow < contract.tonnesPerWeek) {
          warning = '<br/><span class="warning" style="font-size: 0.85em;">The route only holds ' + contract.capacityNow + 't now</span>'
        }
        if (contract.missedWeeks > 0) {
          warning += '<br/><span class="warning" style="font-size: 0.85em;">' + contract.missedWeeks + ' of 3 weeks missed</span>'
        }
      }

      html +=
        '<div class="table-row">' +
        '<div class="cell" style="width: 62%;">' +
        '<h5>' + contract.from + ' &rarr; ' + contract.to + '</h5>' +
        '<span style="font-size: 0.85em;">' + contract.tonnesPerWeek + 't a week &middot; ' +
        money(contract.pricePerWeek) + ' a week &middot; ' + contract.weeks + ' weeks</span>' +
        warning +
        '</div>' +
        '<div class="cell" style="width: 38%;">' + buttons + '</div>' +
        '</div>';
    });
    html += '</div>';
    $body.html(html);
  }

  function reload() {
    $.ajax({
      type: 'GET', url: 'airlines/' + activeAirline.id + '/cargo-contracts', dataType: 'json',
      success: render,
      error: function (jqXHR) { console.log('Could not load the cargo contracts: ' + JSON.stringify(jqXHR)); }
    });
  }

  global.showCargo = function () {
    var $panel = panel();
    if ($panel.is(':visible')) {
      $panel.hide();
      return;
    }
    $panel.show();
    reload();
  };

  global.closeCargo = function () {
    $('#cargoPanel').hide();
  };

  global.respondToCargo = function (contractId, action) {
    $.ajax({
      type: 'PUT',
      url: 'airlines/' + activeAirline.id + '/cargo-contracts/' + contractId + '/' + action,
      dataType: 'json',
      success: reload,
      error: function (jqXHR) {
        if (typeof showFloatMessage === 'function') {
          showFloatMessage(jqXHR.responseText || 'That did not work');
        }
        reload();
      }
    });
  };

  global.cancelCargo = function (contractId, penalty) {
    var ask = 'Breaking this contract costs ' + money(penalty) + '. Go ahead?';
    var go = function () {
      $.ajax({
        type: 'PUT',
        url: 'airlines/' + activeAirline.id + '/cargo-contracts/' + contractId + '/cancel',
        dataType: 'json',
        success: function () {
          reload();
          if (typeof refreshPanels === 'function') {
            try { refreshPanels(activeAirline.id) } catch (e) { }
          }
        },
        error: function (jqXHR) {
          if (typeof showFloatMessage === 'function') {
            showFloatMessage(jqXHR.responseText || 'That did not work');
          }
          reload();
        }
      });
    };
    if (typeof promptConfirm === 'function') {
      promptConfirm(ask, go);
    } else {
      go();
    }
  };
})(window);

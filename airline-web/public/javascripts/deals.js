/*
 * Deals between two airlines.
 *
 * Five friends sharing a world can do exactly two things to each other:
 * compete for the same passengers, and join an alliance. Everything else they
 * agree on - "I will sell you that 787", "pay me and I stay out of Madrid",
 * "here is ten million to get you started" - happens in a chat window and then
 * cannot be carried out, because there is no way for money or aircraft to
 * move between two people.
 *
 * One shape covers all of it: offer cash and aircraft, ask for cash, the other
 * side says yes or no. What the deal is FOR is between the two of you.
 */
(function (global) {
  'use strict';

  function money(value) {
    var rounded = Math.round(value || 0);
    return '$' + rounded.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  function panel() {
    var existing = document.getElementById('dealsPanel');
    if (existing) {
      return $(existing);
    }
    var $panel = $(
      '<div id="dealsPanel" class="section" style="display: none; position: fixed; ' +
      'top: 60px; right: 20px; width: 520px; max-height: 80%; overflow-y: auto; z-index: 60;">' +
      '<span class="button" style="float: right;" onclick="closeDeals()">&#10006;</span>' +
      '<h3>Deals</h3>' +
      '<div class="dealsBody"></div>' +
      '<h4 style="margin-top: 20px;">Offer something</h4>' +
      '<div class="dealsForm"></div>' +
      '</div>');
    $('body').append($panel);
    return $panel;
  }

  function renderDeals(result) {
    var $body = panel().find('.dealsBody');
    var deals = (result && result.deals) || [];

    if (deals.length === 0) {
      $body.html('<h5>Nothing yet.</h5>');
      return;
    }

    var html = '<div class="table data" style="width: 100%;">';
    $.each(deals, function (index, deal) {
      var who = deal.incoming ? ('From ' + deal.fromAirlineName) : ('To ' + deal.toAirlineName);
      var gives = [];
      if (deal.offeredAirplanes.length > 0) {
        gives.push($.map(deal.offeredAirplanes, function (a) { return a.name + ' (' + a.condition + '%)'; }).join(', '));
      }
      if (deal.offeredCash > 0) gives.push(money(deal.offeredCash));
      var givesText = gives.length ? gives.join(' and ') : 'nothing';
      var wantsText = deal.requestedCash > 0 ? money(deal.requestedCash) : 'nothing';

      var buttons = '';
      if (deal.status === 'PENDING') {
        if (deal.incoming) {
          buttons =
            '<div class="button" onclick="respondToDeal(' + deal.id + ', \'accept\')">Accept</div> ' +
            '<div class="button" onclick="respondToDeal(' + deal.id + ', \'decline\')">Decline</div>';
        } else {
          buttons = '<div class="button" onclick="respondToDeal(' + deal.id + ', \'cancel\')">Withdraw</div>';
        }
      } else {
        buttons = '<span class="label">' + deal.statusLabel + '</span>';
      }

      html +=
        '<div class="table-row">' +
        '<div class="cell" style="width: 62%;">' +
        '<h5>' + who + '</h5>' +
        '<span style="font-size: 0.85em;">Gives ' + givesText + ' &middot; wants ' + wantsText + '</span>' +
        (deal.note ? '<br/><span style="font-size: 0.85em; opacity: 0.75;">' + $('<div>').text(deal.note).html() + '</span>' : '') +
        '</div>' +
        '<div class="cell" style="width: 38%;">' + buttons + '</div>' +
        '</div>';
    });
    html += '</div>';
    $body.html(html);
  }

  function renderForm(airlines, airplanes) {
    var $form = panel().find('.dealsForm');

    if (airlines.length === 0) {
      $form.html('<h5>There is nobody else here to deal with yet.</h5>');
      return;
    }

    var html = '<div class="table" style="width: 100%;">';
    html += '<div class="table-row"><div class="cell" style="width: 40%;">To</div><div class="cell">' +
      '<select id="dealToAirline">' +
      $.map(airlines, function (a) { return '<option value="' + a.id + '">' + a.name + '</option>'; }).join('') +
      '</select></div></div>';

    html += '<div class="table-row"><div class="cell">Cash you give</div><div class="cell">' +
      '<input type="number" id="dealOfferedCash" value="0" min="0" style="width: 90%;"/></div></div>';

    html += '<div class="table-row"><div class="cell">Cash you want</div><div class="cell">' +
      '<input type="number" id="dealRequestedCash" value="0" min="0" style="width: 90%;"/></div></div>';

    if (airplanes.length > 0) {
      html += '<div class="table-row"><div class="cell">Aircraft you give</div><div class="cell">' +
        '<select id="dealAirplanes" multiple size="' + Math.min(5, airplanes.length) + '" style="width: 90%;">' +
        $.map(airplanes, function (a) {
          return '<option value="' + a.id + '">#' + a.id + ' ' + a.name + ' (' + a.condition + '%)</option>';
        }).join('') +
        '</select></div></div>';
    } else {
      html += '<div class="table-row"><div class="cell">Aircraft you give</div><div class="cell">' +
        '<span class="label">None idle - aircraft on a route cannot change hands</span></div></div>';
    }

    html += '<div class="table-row"><div class="cell">Note</div><div class="cell">' +
      '<input type="text" id="dealNote" maxlength="200" style="width: 90%;" placeholder="what this is for"/></div></div>';
    html += '</div>';
    html += '<div class="button" style="margin-top: 10px;" onclick="sendDeal()">Send offer</div>';

    $form.html(html);
  }

  function reload() {
    var airlineId = activeAirline.id;
    $.ajax({
      type: 'GET', url: 'airlines/' + airlineId + '/deals', dataType: 'json',
      success: renderDeals,
      error: function (jqXHR) { console.log('Could not load deals: ' + JSON.stringify(jqXHR)); }
    });
    $.ajax({
      type: 'GET', url: 'airlines/' + airlineId + '/deals/counterparties', dataType: 'json',
      success: function (counterparties) {
        $.ajax({
          type: 'GET', url: 'airlines/' + airlineId + '/deals/offerable-airplanes', dataType: 'json',
          success: function (offerable) {
            renderForm(counterparties.airlines || [], offerable.airplanes || []);
          }
        });
      }
    });
  }

  global.showDeals = function () {
    var $panel = panel();
    if ($panel.is(':visible')) {
      $panel.hide();
      return;
    }
    $panel.show();
    reload();
  };

  global.closeDeals = function () {
    $('#dealsPanel').hide();
  };

  global.respondToDeal = function (dealId, action) {
    $.ajax({
      type: 'PUT',
      url: 'airlines/' + activeAirline.id + '/deals/' + dealId + '/' + action,
      dataType: 'json',
      success: function () {
        reload();
        //money and aircraft may have just moved
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

  global.sendDeal = function () {
    var airplaneIds = $('#dealAirplanes').val() || [];
    var payload = {
      toAirlineId: parseInt($('#dealToAirline').val()),
      offeredCash: parseInt($('#dealOfferedCash').val()) || 0,
      requestedCash: parseInt($('#dealRequestedCash').val()) || 0,
      offeredAirplaneIds: $.map(airplaneIds, function (id) { return parseInt(id); }),
      note: $('#dealNote').val() || ''
    };

    $.ajax({
      type: 'PUT',
      url: 'airlines/' + activeAirline.id + '/deals',
      data: JSON.stringify(payload),
      contentType: 'application/json; charset=utf-8',
      dataType: 'json',
      success: function () {
        if (typeof showFloatMessage === 'function') showFloatMessage('Offer sent');
        reload();
      },
      error: function (jqXHR) {
        if (typeof showFloatMessage === 'function') {
          showFloatMessage(jqXHR.responseText || 'That offer was not accepted');
        }
      }
    });
  };
})(window);

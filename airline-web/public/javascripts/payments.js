/*
 * Everything being paid for by the week, in one place.
 *
 * A lease and a set of instalments are commitments that run for months, and
 * the only way to see one was to open each aircraft in turn - which is no way
 * to answer the question anybody actually has: how much of my week is already
 * spoken for, and when does it stop?
 */
(function (global) {
  'use strict';

  function money(value) {
    var rounded = Math.round(value || 0);
    return '$' + rounded.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  function panel() {
    var existing = document.getElementById('paymentsPanel');
    if (existing) {
      return $(existing);
    }
    var $panel = $(
      '<div id="paymentsPanel" class="section" style="display: none; position: fixed; ' +
      'top: 60px; right: 20px; width: 560px; max-height: 80%; overflow-y: auto; z-index: 60;">' +
      '<span class="button" style="float: right;" onclick="closePayments()">&#10006;</span>' +
      '<h3>Payments</h3>' +
      '<div class="paymentsBody"></div>' +
      '</div>');
    $('body').append($panel);
    return $panel;
  }

  function render(result) {
    var $body = panel().find('.paymentsBody');
    if (!result.enabled) {
      $body.html('<h5>Leasing and instalments are switched off in this world.</h5>');
      return;
    }

    var html = '';

    // The two numbers worth knowing before anything else: what leaves every
    // week, and how much tax is already paid for.
    html += '<div class="table" style="width: 100%; margin-bottom: 10px;">';
    html += '<div class="table-row"><div class="cell" style="width: 60%;"><h5>Leaving every week</h5></div>' +
      '<div class="cell">' + money(result.weeklyTotal) + '</div></div>';
    if (result.vatPercent > 0) {
      html += '<div class="table-row"><div class="cell"><h5>VAT still to reclaim</h5></div>' +
        '<div class="cell">' + money(result.vatCredit) + '</div></div>';
    }
    html += '</div>';

    var plans = result.plans || [];
    if (plans.length === 0) {
      html += '<h5>Nothing on a payment plan. Aircraft bought outright do not appear here.</h5>';
      $body.html(html);
      return;
    }

    html += '<div class="table data" style="width: 100%;">';
    $.each(plans, function (index, plan) {
      var lease = plan.kind === 'LEASE';
      var when = lease
        ? 'for as long as you keep it'
        : plan.weeksRemaining + ' week(s) left, ' + money(plan.leftToPay) + ' to go';

      html +=
        '<div class="table-row">' +
        '<div class="cell" style="width: 58%;">' +
        '<h5>' + plan.name + ' <span class="label">#' + plan.airplaneId + '</span></h5>' +
        '<span style="font-size: 0.85em;">' + (lease ? 'Leased' : 'On instalments') + ' &middot; ' + when + '</span>' +
        '<br/><span style="font-size: 0.85em; opacity: 0.75;">Paid so far ' + money(plan.paidSoFar) +
        ' over ' + plan.weeksPaid + ' week(s)' + (plan.onRoute ? ' &middot; flying' : ' &middot; idle') + '</span>' +
        '</div>' +
        '<div class="cell" style="width: 42%;">' + money(plan.weeklyPayment) + ' a week</div>' +
        '</div>';
    });
    html += '</div>';

    $body.html(html);
  }

  function reload() {
    $.ajax({
      type: 'GET', url: 'airlines/' + activeAirline.id + '/airplane-payments', dataType: 'json',
      success: render,
      error: function (jqXHR) { console.log('Could not load the payments: ' + JSON.stringify(jqXHR)); }
    });
  }

  global.showPayments = function () {
    var $panel = panel();
    if ($panel.is(':visible')) {
      $panel.hide();
      return;
    }
    $panel.show();
    reload();
  };

  global.closePayments = function () {
    $('#paymentsPanel').hide();
  };
})(window);

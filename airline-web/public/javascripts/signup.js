$( document ).ready(function() {
    $('input#airlineName').on('input', function() {
        var airlineName = $(this).val()
        $.ajax({
    		type: 'GET',
    		url: "signup/airline-name-check?airlineName=" + airlineName,
    	    contentType: 'application/json; charset=utf-8',
    	    dataType: 'json',
    	    success: function(result) {
    	    	if (result.ok) {
                    $('.airlineName dd.error').text('')
    	    	} else {
    	    	    $('.airlineName dd.error').text(result.rejection)
    	    	}
    	    },
            error: function(jqXHR, textStatus, errorThrown) {
    	            console.log(JSON.stringify(jqXHR));
    	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
    	    }
    	});
    })
})

function signup(form) {
	// On a private instance reCAPTCHA is normally disabled: the upstream site
	// key is registered for airline-club.com, so it rejects tokens from any
	// other host and nobody would be able to register. The server skips
	// verification in that case, so an empty token is fine.
	if (!window.RECAPTCHA_ENABLED) {
		$('body .loadingSpinner').show()
		form.append('<input type="hidden" name="recaptchaToken" value="" />');
		form.submit()
		return;
	}

	grecaptcha.ready(function() {
		grecaptcha.execute(window.RECAPTCHA_SITE_KEY, {action: 'signup'})
			.then(function(token) {
			    $('body .loadingSpinner').show()
             	form.append('<input type="hidden" name="recaptchaToken" value="' + token + '" />');
				form.submit()
			});
		});
}

	
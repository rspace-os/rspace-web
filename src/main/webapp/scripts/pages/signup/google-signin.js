function onSignUp(googleUser) {
	var id_token = googleUser.credential;
	var client_id = googleUser.clientId;
	var device_tz = $('#timezone_field').val();
	console.log('auth: ' + id_token);
	RS.blockPage(RS.msg("legacyjs.googleSignin.registeringAccount"));
	$.post("/externalAuth/ajax/signup",
		{
			idTokenString : id_token,
			clientId : client_id,
			timezone:device_tz
		}, function(xhr) {
			if (xhr.data != null) {
				console.log(xhr);
				window.location = xhr.data;
			} else {
				apprise(getValidationErrorString(xhr.errorMsg));
			}
		}
	)
	.fail(function (jqxhr) {
		googleSignupAjaxFailed(RS.msg("legacyjs.googleSignin.actionSignup"), jqxhr)
	})
	.always(function (jxqr) {
		RS.unblockPage();
	});
}

function onSignIn(googleUser) {
	var id_token = googleUser.credential;
	var client_id = googleUser.clientId;
	var device_tz = $('#timezone_field').val();
	console.log('auth: ' + id_token);
	RS.blockPage(RS.msg("legacyjs.googleSignin.signingIn"));
	$.post("/externalAuth/ajax/login",
		{
			idTokenString : id_token,
			clientId : client_id,
			timezone:device_tz
		}, function(xhr) {
			if (xhr.data != null) {
				console.log(xhr);
				window.location = xhr.data;
			} else {
				apprise(getValidationErrorString(xhr.errorMsg));
			}
		}
	)
	.fail(function(jqxhr) {
		googleSignupAjaxFailed(RS.msg("legacyjs.googleSignin.actionLoggingIn"), jqxhr)
	})
	.always(function(){
		RS.unblockPage();
	});
}

function googleSignupAjaxFailed(action, jqxhr) {
	RS.unblockPage();
	
	var responseText = jqxhr.responseText;
	if (responseText.match(/No account found for email .*, please sign up/g)){
		/* Short, user-friendly message when they log in instead of signing up */
		var msg = responseText.match(/No account found for email .*, please sign up/g)[0]
			.replace("sign up", "<a href='/signup'>sign up</a>");
		Apprise("<h4>" + msg + "</h4>", { override: false });
	} else {
		RS.ajaxFailed(action, false, jqxhr)
	}
}

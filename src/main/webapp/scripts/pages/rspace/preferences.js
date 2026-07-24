
$(document).ready(function (e) {
	
	$(document).on('click','#prefssubmit', function (e) {
		e.preventDefault();
		var json = $('#messageSettingsForm').serialize();
		var jxqr = $.post($('#messageSettingsForm').attr('action'), json, function (response){
			if(response.data != null) {
				RS.confirm(response.data, "success", 3000);
			} else if(response.errorMessages != null){
				apprise(getValidationErrorString(response.errorMessages));
			}
		});
		jxqr.fail(function () {
			RS.ajaxFailed(RS.msg("legacyjs.core.preferences.changeFailed"), false, jxqr);
		});
	});
});

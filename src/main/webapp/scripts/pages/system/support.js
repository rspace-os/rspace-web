$(document).ready(function() {
	
	addViewLogHandler();
	addMailLogHandler();
	addViewLicenseHandler();
	addForceRefreshLicenseHandler();
});

function addForceRefreshLicenseHandler (){
	$(document).on('click', '#forceRefreshLicenseLink', function (e) {
		$('#mainArea').empty();
		var jxqr =  $.post('/system/support/ajax/forceRefreshLicense', {}, function(response) {
			if(response.data != null &&  response.data == true) {
				RS.confirm(RS.msg("legacyjs.system.support.licenseRefreshSuccessful"), "success", 3000);
			} 
			else if (response.data != null &&  response.data == false){
				RS.confirm(RS.msg("legacyjs.system.support.licenseRefreshFailed"), "error", 3000);
			}else if (response.errorMsg != null){
				apprise(getValidationErrorString(response.errorMsg));
			}
		});
		jxqr.fail(function () {
			 RS.ajaxFailed(RS.msg("legacyjs.system.support.refreshingLicenseAction"), false, jxqr);
		});		
	});
}

function addViewLicenseHandler(){
	$(document).on('click', '#showLicenseLink', function (e) {
		$('#mainArea').empty();
		var jxqr =  $.get('/system/support/ajax/license', {}, function(response) {
			if(response.data != null) {
				var htmlTemplate = $('#licenseInfoTemplate').html();		
				var html = Mustache.render(htmlTemplate, response.data);
				$('#mainArea').append(html);
			} 
			
			else if (response.errorMsg != null){
				apprise(getValidationErrorString(response.errorMsg));
			}
		});
		jxqr.fail(function () {
			 RS.ajaxFailed(RS.msg("legacyjs.system.support.refreshingLicenseAction"), false, jxqr);
		});		
	});
}

function addMailLogHandler() {
	$(document).on('click', '#mailServerLogsLink', function (e) {
		$('#mainArea').empty();
		$('#mainArea').append($("#postLogsFormContainer").html());
	});
	
	$(document).on('click', '#logSubmit', function (e) {
		e.preventDefault();
		var formData = $(this).closest('form').serialize();
		var jxqr =  $.post('/system/support/ajax/mailLog', formData, function(response) {
			if(response.data != null) {
				$('#mainArea').empty();
				RS.confirm(RS.msg("legacyjs.system.support.logFilesMailed"), "success", 3000);
			} else if (response.errorMsg != null){
				apprise(getValidationErrorString(response.errorMsg));
			}
		});
		jxqr.fail(function () {
			 RS.ajaxFailed(RS.msg("legacyjs.system.support.mailingLogFilesAction"), false, jxqr);
		});
	});
}

function addViewLogHandler() {
	
	$(document).on('click', '#viewServerLogsLink', function (e) {
		$('#mainArea').empty();
		var jxqr = $.get('/system/support/ajax/viewLog', function (resp) {
			if(resp.data != null) {
				$.get("/scripts/templates/serverlogs.html", function (dataT) {
					console.log("Got template");
					var template = $(dataT).filter("#serverLogs-template").html();
					var html = Mustache.render(template, resp);
					$('#mainArea').html(html);
				});
			} else if (resp.errorMsg != null){
				apprise(getValidationErrorString(resp.errorMsg));
			}
		});
		jxqr.fail(function () {
			 RS.ajaxFailed(RS.msg("legacyjs.system.support.retrievingLogsAction"),false,jxqr);
		});
	});
}
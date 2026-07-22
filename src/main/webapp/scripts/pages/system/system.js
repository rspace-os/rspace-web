function setUpRunAsUserDlg() {
	$(document).ready(function () {
		$('#runAsUserDlg').dialog({
			title: RS.msg("legacyjs.system.system.runAsUserDialogTitle"),
			resizable: true,
			autoOpen: false,
			height: 450,
			width: 350,
			modal: true,
			/**
			* On open we retrieve dialog contents from createAdmin.jsp and insert HTML
			*/
			open: function () {
				var jxqr = $.get(createURL("/system/ajax/runAs/"), function (response) {
					$('#runAsUserDlgContent').html(response);
					setUpAutoCompleteUsernameBox('#runAsUsername', '/system/ajax/listRunAsUsers');
				});
				jxqr.fail(function () {
					RS.unblockPage();
					RS.ajaxFailed(RS.msg("legacyjs.system.system.runAsInitFailedAction"), false, jxqr);
				});
			},
			buttons:
			{
				[RS.msg("legacyjs.system.common.cancel")]: function () {
					$("#runAsUserDlg").dialog('close');
				},
				[RS.msg("legacyjs.system.common.submit")]: function () {
					// Remove the inventory JWT token
					window.sessionStorage.removeItem("id_token");

					var data = $('#runAsUserDlgContent').find('form').serialize();
					var jxqr = $.post(createURL("/system/ajax/runAs/"), data, function (response) {
						$('<html>' + response + '</html').filter('#formCompleted').each(function () {
							$('#runAsUserDlgContent').html(response);
							setTimeout(function () {
								window.location.href = "/workspace";
							}, 2000);
						});
						$('#runAsUserDlgContent').html(response);
						setUpAutoCompleteUsernameBox('#runAsUsername', '/system/ajax/listRunAsUsers');
					});
				}
			}
		});
	});
}

function init() {
	setUpRunAsUserDlg();
}

$(document).ready(function () {
	init();
	
	$('body').on('click', '.reauthrequired', function (e) {
		e.preventDefault();
		var dlgId = "#" + $(this).data("dlgid");
		$.get('/vfpwd/ajax/checkVerificationPasswordNeeded', function (response) {
			if (response.data) {
				apprise(RS.msg("legacyjs.system.system.verificationPasswordRequired"));
			} else {
				$(dlgId).dialog('open');
			}
		});
	});
});

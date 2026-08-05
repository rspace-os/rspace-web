
function _selfDeclareAsPi() {
	RS.blockPage(RS.msg("legacyjs.selfDeclarePi.addingPiRole"));
	var jqxhr= $.post(createURL("/userform/ajax/selfDeclareAsPi"));
	jqxhr.done(function (result) {
		RS.unblockPage();
		if (result.data) {
			RS.confirmAndNavigateTo(RS.msg("legacyjs.selfDeclarePi.promotedToPi"), "success", 2000, "/userform");
		} else {
			var errorMsgs = result.error.errorMessages.join(';');
			RS.confirm(errorMsgs, "warning", 5000, { sticky: true });
		}
	});
	jqxhr.fail(function(){
		RS.unblockPage();
		RS.ajaxFailed(RS.msg("legacyjs.selfDeclarePi.actionSelfDeclareProcess"), false, jqxhr);
	});
}
function _selfDeclareAsRegularUser() {
	RS.blockPage(RS.msg("legacyjs.selfDeclarePi.removingPiRole"));
	var jqxhr= $.post(createURL("/userform/ajax/selfDeclareAsRegularUser"));
	jqxhr.done(function (result) {
		RS.unblockPage();
		if (result.data) {
			RS.confirmAndNavigateTo(RS.msg("legacyjs.selfDeclarePi.removedPiRole"), "success", 2000,"/userform");
		} else {
			var errorMsgs = result.error.errorMessages.join(';');
			RS.confirm(errorMsgs, "warning", 5000, { sticky: true });
		}
	});
	jqxhr.fail(function(){
		RS.unblockPage();
		RS.ajaxFailed(RS.msg("legacyjs.selfDeclarePi.actionSelfDeclareProcess"), false, jqxhr);
	});
}

$(document).ready(function (e) {

	var selfDeclarePiInstitutionName = $('div#selfDeclarePi_institutionName').text();

	$('#promoteToPiButton').click(function() {
		if (!isUserAllowedPiRole) {
			apprise(RS.msg("legacyjs.selfDeclarePi.cannotBecomePi", selfDeclarePiInstitutionName));
			return;
		}
		_selfDeclareAsPi();
	})

	$('#demoteFromPiButton').click(function() {
		if (isUserAPiOfSomeGroup) {
			apprise(RS.msg("legacyjs.selfDeclarePi.isPiOfLabGroup"));
			return;
		}
		
		if (!isUserAllowedPiRole) {
			apprise(RS.msg("legacyjs.selfDeclarePi.confirmRemovePiStatus", selfDeclarePiInstitutionName),
				{ confirm: true, textOk: RS.msg("legacyjs.selfDeclarePi.removePiRoleConfirm"), textCancel: RS.msg("legacyjs.selfDeclarePi.removePiRoleCancel") },
				_selfDeclareAsRegularUser);
			return;
		}
		_selfDeclareAsRegularUser();
	})

});


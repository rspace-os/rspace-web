
function _selfDeclareAsPi() {
	RS.blockPage("Adding PI role...");
	var jqxhr= $.post(createURL("/userform/ajax/selfDeclareAsPi"));
	jqxhr.done(function (result) {
		RS.unblockPage();
		if (result.data) {
			RS.confirmAndNavigateTo("Promoted to PI", "success", 2000, "/userform");
		} else {
			var errorMsgs = result.error.errorMessages.join(';');
			RS.confirm(errorMsgs, "warning", 5000, { sticky: true });
		}
	});
	jqxhr.fail(function(){
		RS.unblockPage();
		RS.ajaxFailed("Self-declare PI process", false, jqxhr);
	});
}
function _selfDeclareAsRegularUser() {
	RS.blockPage("Removing PI role...");
	var jqxhr= $.post(createURL("/userform/ajax/selfDeclareAsRegularUser"));
	jqxhr.done(function (result) {
		RS.unblockPage();
		if (result.data) {
			RS.confirmAndNavigateTo("Removed PI role", "success", 2000,"/userform");
		} else {
			var errorMsgs = result.error.errorMessages.join(';');
			RS.confirm(errorMsgs, "warning", 5000, { sticky: true });
		}
	});
	jqxhr.fail(function(){
		RS.unblockPage();
		RS.ajaxFailed("Self-declare PI process", false, jqxhr);
	});
}

$(document).ready(function (e) {

	var selfDeclarePiInstitutionName = $('div#selfDeclarePi_institutionName').text();

	$('#promoteToPiButton').click(function() {
		if (!isUserAllowedPiRole) {
			apprise("Based on your status at " + selfDeclarePiInstitutionName+ ", you cannot become a PI " +
				"unless a system administrator manually enables this for you.");
			return;
		}
		_selfDeclareAsPi();
	})

	$('#demoteFromPiButton').click(function() {
		if (isUserAPiOfSomeGroup) {
			apprise("You are a PI of at least one LabGroup. You must delete or transfer over " +
				"all LabGroups before you can remove your PI status.");
			return;
		}
		
		if (!isUserAllowedPiRole) {
			apprise("Based on your status at " + selfDeclarePiInstitutionName + ", if you remove your PI status, " +
				"you will need a system administrator to restore it, are you sure you wish to proceed.",
				{ confirm: true, textOk: "Yes, remove my PI role", textCancel: "No, don't" },
				_selfDeclareAsRegularUser);
			return;
		}
		_selfDeclareAsRegularUser();
	})

});


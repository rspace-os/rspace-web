$(document).ready(function() {

    addIpAddressConfig();
	
});

function addIpAddressConfig () {
	$(document).on('click', '#whitelistLink', function (e) {
		e.preventDefault();
		_reloadIpAddresses();
	});
	
	$(document).on('click', '.ipedit', function (e){
		e.preventDefault();

		var currDesc = $(this).closest('tr').find('.ipDesc').text();
		$(this).closest('tr').find('.ipDesc').html("<input class='ipDesc-input' name='description' value='"+currDesc+"'>");
		$(this).text(RS.msg("legacyjs.system.config.saveSeparator"));
		$(this).removeClass('ipedit');
		$(this).addClass('ipsave');
	});
	
	$(document).on('click', '.ipsave', function (e){
		var ipVal= $(this).closest('tr').find('.ipAddress').text().trim();
		var desc = $(this).closest('tr').find('.ipDesc-input').val().trim();
		var id = $(this).data('id');
		if(!_validate(ipVal, desc)) {
			return;
		}
	
		var jxqr = $.post("/system/config/ajax/updateIpAddress", {ipAddress:ipVal, id:id, description:desc}, function () {
			_reloadIpAddresses();
		});
		jxqr.fail(function () {
			RS.ajaxFailed(RS.msg("legacyjs.system.config.savingIpAddressAction"), false, jxqr);
		})
	});
	
	$(document).on('click', '.removeIp', function (e) {
		var link = $(this).attr("data-href");
		var jxqr = $.post(link, function () {
			_reloadIpAddresses();
		});
		jxqr.fail(function () {
			RS.ajaxFailed(RS.msg("legacyjs.system.config.deletingIpAddressAction"), false, jxqr);
		})
	});
	
	$(document).on('click', '#newIpAddressSubmit', function (e) {
		var ipVal= $('#newIpAddress').val().trim();
		var desc = $('#newIpDesc').val().trim(); 
		if(!_validate(ipVal, desc)) {
			return;
		}
	
		var jxqr = $.post("/system/config/ajax/addIpAddress", {ipAddress:ipVal, description:desc}, function () {
			_reloadIpAddresses();
		});
		jxqr.fail(function () {
			RS.ajaxFailed(RS.msg("legacyjs.system.config.addingIpAddressAction"), false, jxqr);
		})
	});
}

function _validate (ipVal, desc){
	if(! ipVal.match(/^[0-9\.:A-Za-z/]+$/)) {
		apprise(RS.msg("legacyjs.system.config.invalidIpAddress"));
		return false;
	}
	
	if(desc.length == 0) {
		apprise(RS.msg("legacyjs.system.config.missingIpDescription"));
		return false;
	}
	return true;
}

function _reloadIpAddresses(){
	$('#mainArea').empty();
	var currIps = $.get("/system/config/ajax/ipAddresses", function (data) {
		console.log("Got data");
		$.get("/scripts/templates/whiteListedIpAddresses.html", function (dataT) {
			console.log("Got template");
			var template = $(dataT).filter("#whitelistedIpAddresses-template").html();
			var html = Mustache.render(template, data);
			$('#mainArea').append(html);
		});
	});
}

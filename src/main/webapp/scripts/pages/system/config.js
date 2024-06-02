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
		$(this).text("Save | ");
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
			RS.ajaxFailed("Saving ip address", false, jxqr);
		})
	});
	
	$(document).on('click', '.removeIp', function (e) {
		var link = $(this).attr("data-href");
		var jxqr = $.post(link, function () {
			_reloadIpAddresses();
		});
		jxqr.fail(function () {
			RS.ajaxFailed("Deleting ip address", false, jxqr);
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
			RS.ajaxFailed("Adding ip address", false, jxqr);
		})
	});
}

function _validate (ipVal, desc){
	if(! ipVal.match(/^[0-9\.:A-Za-z/]+$/)) {
		apprise("Please enter a valid IPv4, CIDR, or IPv6  address");
		return false;
	}
	
	if(desc.length == 0) {
		apprise("Please enter a human-readable identifier for this IP address");
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

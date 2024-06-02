/**
 * Script loaded inside Egnyte tinymce plugin dialog 
 */

var egnyteToken;
var activeEditor;

function initEgnyteDialog() {

	var integrationRequest = $.get('/integration/integrationInfo', { name: 'EGNYTE' });
	var egnyteTokenRequest = $.get('/egnyte/egnyteSessionToken');

	var requestsPromise = $.when(integrationRequest, egnyteTokenRequest);
	requestsPromise.done(function (integrationResponse, egnyteTokenResponse) {

		var integrationInfo = integrationResponse[0].data;
		var egnyteDomain = null;
		$.each(integrationInfo.options, function () {
			egnyteDomain = this.EGNYTE_DOMAIN;
		});

		if (!egnyteDomain) {
			alert("Egnyte Domain URL is not set, please go to Apps page and set it up.");
			activeEditor.windowManager.close();
			return;
		}

		if (!egnyteToken) {
			egnyteToken = egnyteTokenResponse[0];
		}
		if (!egnyteToken) {
			openAuthorizationDialogForEgnyte(initEgnyteDialog, egnyteDomain);
			return;
		}

		loadEgnyteFilePicker(egnyteDomain, egnyteToken);
	});
}

function openAuthorizationDialogForEgnyte(onSuccess, egnyteDomain) {

	var jqxhr = $.get('/deploymentproperties/ajax/property', { name: 'egnyte.client.id' });
	jqxhr.done(function (egnyteClientId) {
		if (!egnyteClientId) {
			alert('Egnyte is not configured properly on this RSpace instance. Please contact your System Admin');
			return;
		}
		var authUrl = egnyteDomain + "/puboauth/token?client_id=" + egnyteClientId
			+ "&redirect_uri=https://" + window.location.host + "/scripts/externalTinymcePlugins/egnyte/dialog.html"
			+ "&scope=Egnyte.filesystem Egnyte.link&response_type=token";

		window.location = authUrl;
	});
}

function loadEgnyteFilePicker(egnyteDomain, egnyteToken) {

	$.getScript("/scripts/externalTinymcePlugins/egnyte/egnyte.js", function () {
		var egnyte = Egnyte.init(egnyteDomain, {
			token: egnyteToken
		});

		egnyte.filePicker($('#egnyteWrapper').get(0), {
			selection: function (list) {
				$.each(list, function (i, elem) {
					insertSimpleEgnyteLink(egnyteDomain, elem);
				});
				parent.tinymce.activeEditor.windowManager.close();
			},
			cancel: function () {
				parent.tinymce.activeEditor.windowManager.close();
			},
			error: function (e) {
				console.error('problem with egnyte filepicker', e)
			}
		});
	});
}

var insertSimpleEgnyteLink = function (egnyteDomain, egnyteElem) {

	var id, link, iconPath;
	if (egnyteElem.is_folder) {
		id = egnyteElem.folder_id;
		link = egnyteDomain + "/navigate/folder/" + egnyteElem.folder_id;
		iconPath = '/images/icons/folder.png';
	} else {
		id = egnyteElem.group_id;
		link = egnyteDomain + "/navigate/file/" + egnyteElem.group_id;
		var extension = RS.getFileExtension(egnyteElem.name);
		iconPath = RS.getIconPathForExtension(extension);
	}

	var templateData = {
		fileStore: 'egnyte',
		id: id,
		recordURL: link,
		name: egnyteElem.name,
		iconPath: iconPath,
		badgeIconPath: '/images/icons/egnyte.png'
	};

	window.parent.document.dispatchEvent(new CustomEvent('egnyte-insert', {'detail': templateData}));
}

$(document).ready(function () {
	var hash = window.location.hash;

	if (hash && hash.startsWith('#access_token=')) {
		egnyteToken = hash.substring('#access_token='.length, hash.indexOf('&'));
		$.post('/egnyte/egnyteSessionToken', { token: egnyteToken });
	}

	initEgnyteDialog();
});

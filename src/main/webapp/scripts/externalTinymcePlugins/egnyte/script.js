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
			alert(RS.msg("legacyjs.tinymce.egnyte.domainMissing"));
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

var EGNYTE_OAUTH_STATE_KEY = 'egnyteOAuthState';

// Random nonce sent as the OAuth `state` param and checked when the token comes
// back, so a token can only be accepted for a flow this browser started (guards
// against an attacker pasting a dialog.html#access_token=... URL to a victim).
function generateEgnyteOAuthState() {
	var bytes = new Uint8Array(16);
	window.crypto.getRandomValues(bytes);
	var state = Array.from(bytes, function (b) {
		return b.toString(16).padStart(2, '0');
	}).join('');
	sessionStorage.setItem(EGNYTE_OAUTH_STATE_KEY, state);
	return state;
}

function openAuthorizationDialogForEgnyte(onSuccess, egnyteDomain) {

	var jqxhr = $.get('/deploymentproperties/ajax/property', { name: 'egnyte.client.id' });
	jqxhr.done(function (egnyteClientId) {
		if (!egnyteClientId) {
			alert(RS.msg("legacyjs.tinymce.egnyte.configMissing"));
			return;
		}
		var redirectUri = "https://" + window.location.host
			+ "/scripts/externalTinymcePlugins/egnyte/dialog.html";
		var state = generateEgnyteOAuthState();
		var authUrl = egnyteDomain + "/puboauth/token"
			+ "?client_id=" + encodeURIComponent(egnyteClientId)
			+ "&redirect_uri=" + encodeURIComponent(redirectUri)
			+ "&scope=" + encodeURIComponent("Egnyte.filesystem Egnyte.link")
			+ "&response_type=token"
			+ "&state=" + encodeURIComponent(state);

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

	if (hash && hash.indexOf('access_token=') !== -1) {
		var fragment = new URLSearchParams(hash.substring(1));
		var returnedToken = fragment.get('access_token');
		var returnedState = fragment.get('state');
		var expectedState = sessionStorage.getItem(EGNYTE_OAUTH_STATE_KEY);
		sessionStorage.removeItem(EGNYTE_OAUTH_STATE_KEY);

		if (!expectedState || returnedState !== expectedState) {
			alert(RS.msg("legacyjs.tinymce.egnyte.authError"));
			return;
		}
		egnyteToken = returnedToken;
		$.post('/egnyte/egnyteSessionToken', { token: egnyteToken });
	}

	initEgnyteDialog();
});


var tinymcesetup = {
	theme: "silver",
	width: "100%",
	height: "580px",
	menu: {
		file: {
			title: 'File',
			items: 'optSave optSaves | print | undo redo | searchreplace selectall | optSnip | confShortcuts confToolbar'
		},
		insert: {
			title: 'Insert',
			items: 'optMediaGallery optFromComputer | anchor charmap nonbreaking insertdatetime hr | link optInternallink optComments optSketch optMathjax codesample'
		},
		format: {
			title: 'Format',
			items: 'bold italic underline strikethrough superscript subscript | formats | indent outdent | removeformat'
		},
		table: {
			title: 'Table',
			items: 'inserttable inserttabledialog optHandsontable tableprops deletetable | cell row column'
		},
		view: {
			title: 'View',
			items: 'visualaid visualblocks visualchars optHideShowToolbar | code preview fullscreen'
		},
		tools: {
			title: 'Science Tools',
			items: 'optDilutionCalc optMasterMix optRSpaceTools'
		},
		onlineTools: {
			title: 'Online Tools',
			items: 'optOnlineBC optOnlineCS optOnlinePM optOnlineSA optOnlineSG'
		},
  },
  mobile: {
    menubar: true
  },

	menubar: 'file insert format table view tools onlineTools',

	plugins: [
		"commandpalette advlist autolink anchor lists link charmap print preview anchor visualchars nonbreaking",
		"searchreplace visualblocks code insertdatetime codesample hr table paste noneditable fullscreen"
	],

	external_plugins: {
		"confirmcancel": "/scripts/externalTinymcePlugins/confirmcancel/plugin.min.js",
		"crudops": "/scripts/externalTinymcePlugins/crudops/plugin.min.js",
		"showinfo": "/scripts/externalTinymcePlugins/info/plugin.min.js",
		"datetime": "/scripts/externalTinymcePlugins/datetime/plugin.min.js",
		"comments": "/scripts/externalTinymcePlugins/comments/plugin.js",
		"shortcuts": "/scripts/externalTinymcePlugins/shortcuts/plugin.min.js",
		"mathjax": "/scripts/externalTinymcePlugins/mathjax/plugin.min.js",
		"handsontable": "/scripts/externalTinymcePlugins/handsontable/plugin.min.js",
		"tools": "/scripts/externalTinymcePlugins/tools/plugin.min.js",
		"toolbar": "/scripts/externalTinymcePlugins/toolbar/plugin.js",
		"lineheight": "/scripts/externalTinymcePlugins/lineheight/plugin.min.js",
		"internallink": "/scripts/externalTinymcePlugins/internallink/plugin.min.js",
		"sketch": "/scripts/externalTinymcePlugins/sketch/plugin.min.js",
		"snip": "/scripts/externalTinymcePlugins/snip/plugin.min.js",
		"mention": "/scripts/externalTinymcePlugins/mention/plugin.min.js",
		"contexttoolbars": "/scripts/externalTinymcePlugins/contexttoolbars/plugin.min.js",
		"resizeimage": "/scripts/externalTinymcePlugins/resizeimage/plugin.min.js"
	},

	table_default_attributes: { width: '300', border: '1', cellpadding: '5' },
	codesample_languages: [
		{ text: 'HTML/XML', value: 'markup' },
		{ text: 'C', value: 'c' },
		{ text: 'C#', value: 'csharp' },
		{ text: 'C++', value: 'cpp' },
		{ text: 'CSS', value: 'css' },
		{ text: 'Java', value: 'java' },
		{ text: 'JavaScript', value: 'javascript' },
		{ text: 'JSON', value: 'json' },
		{ text: 'LaTeX', value: 'latex' },
		{ text: 'MATLAB', value: 'matlab' },
		{ text: 'PHP', value: 'php' },
		{ text: 'Python', value: 'python' },
		{ text: 'R', value: 'r' },
		{ text: 'Ruby', value: 'ruby' },
		{ text: 'SQL', value: 'sql' },
		{ text: 'Wiki markup', value: 'wiki' },
	],
	elementpath: false,
	fontsize_formats: '9px 10px 12px 14px 16px 20px 24px 30px 40px 64px',
	lineheight_formats: '9px 10px 12px 14px 16px 20px 24px 30px 40px 64px',
	gecko_spellcheck: true,
	relative_urls: false,
	content_css: ["/styles/simplicity/typoEdit.css", "/scripts/tinymce/tinymce516/plugins/codesample/css/prism.css"],
	image_advtab: true,
	smart_paste: false,
	branding: false,
	icons: 'custom_icons',
	paste_data_images: false,

	paste_postprocess(plugin, args) {
		var innerText = args.node.innerText.trim();
		var innerHTML = args.node.innerHTML.trim();

		var processed = RS.tinymcePasteHandler.processPastedContent(innerText, innerHTML);
		if (processed) {
			args.node.innerText = '';
		}
	},

	setup(ed) {
		var codeSampleAreaSelector = 'pre[class*="language-"]';
		var attachmentSelector = '.attachmentDiv';
		var calcTableSelector = '.rsCalcTableDiv';

		ed.on('keypress', function () {
			ed.setDirty(true);
		});

		// The help button can obstruct text that is being written in fullscreen mode
		ed.on("FullscreenStateChanged", function (event) {
			if (event.state) {
				RS.hideHelpButton();
			} else {
				RS.showHelpButton();
			}
		});

		ed.on("dblclick", function (e) {
			var $target = $(e.target ? e.target : e.explicitOriginalTarget);
			if ($target.is('img')) {
				if ($target.hasClass("commentIcon")) {
					tinyMCE.activeEditor.execCommand("cmdComments");
				} else if ($target.hasClass("imageDropped")) {
					tinyMCE.activeEditor.execCommand("cmdSketch");
				} else if ($target.hasClass("sketch")) {
					tinyMCE.activeEditor.execCommand("cmdSketch");
				} else if ($target.hasClass("attachmentIcon") && $target.parents(".boxVersionLink").length) {
					showBoxLinkInfo($target.parents(".boxVersionLink"));
				}
			} else if ($target.is('a') && $target.hasClass("rsEquationClickableWrapper")) {
				tinyMCE.activeEditor.execCommand("cmdRsMathJax");
			} else if ($target.hasClass("master-mix") || $target.closest('.master-mix').size() > 0) {
				tinyMCE.activeEditor.execCommand("cmdRSMasterMix");
			}

			var $targetAttachmentDivArea = $target.is(attachmentSelector) ? $target : $target.parents(attachmentSelector);
			if ($targetAttachmentDivArea.length) {
				var attachmentId = getAttachmentIdFrom$Div($targetAttachmentDivArea);
				showRecordInfo(attachmentId);
			}

			var $targetCalcTableArea = $target.is(calcTableSelector) ? $target : $target.parents(calcTableSelector);
			if ($targetCalcTableArea.length) {
				tinyMCE.activeEditor.execCommand("cmdHandsontable");
			}
		});

		ed.on("click", function (e) {
			var fieldId = getFieldIdFromTextFieldId(tinyMCE.activeEditor.id);
			var $fieldName = $('#field-name-' + fieldId);
			hideFieldHint($fieldName);

			var $target = $(e.target ? e.target : e.explicitOriginalTarget);
			if ($target.is('img')) {
				if ($target.hasClass("commentIcon")) {
					showFieldHint($fieldName, "Hint: double-click on the comment to see the details");
				} else if ($target.hasClass("imageDropped") || $target.hasClass("sketch")) {
					showFieldHint($fieldName, "Hint: double-click on the image to open annotation tool");
				} else if ($target.hasClass("chem")) {
					showFieldHint($fieldName, "Hint: double-click on the chemical structure to edit");
				}
			} else if ($target.is('a') && $target.hasClass("rsEquationClickableWrapper")) {
				showFieldHint($fieldName, "Hint: double-click on the math equation to edit");
			}

			var $targetAttachmentDivArea = $target.is(attachmentSelector) ? $target : $target.parents(attachmentSelector);
			if ($targetAttachmentDivArea.length) {
				showFieldHint($fieldName, "Hint: double-click on the attachment to see the details");
			}

			var $targetCodeSampleArea = $target.is(codeSampleAreaSelector) ? $target : $target.parents(codeSampleAreaSelector);
			if ($targetCodeSampleArea.length) {
				showFieldHint($fieldName, "Hint: double-click on code sample to edit");
			}

			var $targetCalcTableArea = $target.is(calcTableSelector) ? $target : $target.parents(calcTableSelector);
			if ($targetCalcTableArea.length) {
				showFieldHint($fieldName, "Hint: double-click on calculation table to edit");
			}
		});

		ed.on("keydown", function (e) {
			if (e.keyCode === $.ui.keyCode.BACKSPACE || e.keyCode === $.ui.keyCode.DELETE) {
				var $currentNode = $(ed.selection.getNode());
				if ($currentNode.is('p')) {
					var contentWithoutSpaces = $currentNode.html().replace(/&nbsp;/g, '').replace(/<br>/g, '').replace(/\s/g, '');
					// if delete or backspace is hit inside empty paragraph remove the paragraph (RSPAC-821)
					if (contentWithoutSpaces === '' || contentWithoutSpaces === "<brdata-mce-bogus=\"1\">") {
						$currentNode.remove();
						e.preventDefault();
						e.stopPropagation();
						e.stopImmediatePropagation();
					}
				}
			}
		});

		ed.on("keyup", function (e) {
			if (e.keyCode === $.ui.keyCode.ENTER) {
				var $currentNode = $(ed.selection.getNode());
				if ($currentNode.is('p')) {
					// RSPAC-1058
					var $moz_abspos_attr = $currentNode.attr('_moz_abspos');
					if ($moz_abspos_attr && $moz_abspos_attr === 'white') {
						$currentNode.removeAttr('_moz_abspos');
					}
				}
			}
		});

		ed.on("init", function () {
			if (navigator.userAgent.indexOf("Firefox") != -1) {
				$(".tox.tox-tinymce").css('display', '');
			}

			// tell React the iframe has beeen rendered
			var event = new CustomEvent('tinymce-iframe-loaded', {'detail': `#${ed.id}_ifr`});
			document.dispatchEvent(event);
		});

    /* dragover event may be triggered on the editor in some cases e.g. if user drops local
    * file directly over tinymce editor, before drag-drop span was applied to the editor */
		ed.on("dragover", function (e) {
			e.preventDefault();
			e.stopPropagation();
			markTinyMCEAreaDroppable();
		});

		ed.on("drop", function (e) {
			/* file upload on drop event is handled by drag-drop span added in documentView.js */
			e.preventDefault();
			e.stopPropagation();
		});
	},

	// 'mention' plugin comes from https://github.com/StevenDevooght/tinyMCE-mention
	mentions: {
		items: Infinity,

		renderDropdown: function() {
			return '<ul class="rte-autocomplete dropdown-menu mentions-list-wrapper"></ul>';
		},

		// get the items to be shown in the pop up box
		source(query, process, delimiter) {
			let id = $('.rs-global-id a:not(.recordInfoIcon)').text().replace(/\D/g, '');
			var get_users = $.get("/messaging/ajax/recipients", { term: query, messageType: "SIMPLE_MESSAGE", targetFinderPolicy: "STRICT", recordId: id }, function (data) {
				data = data.data;
				if (!data) {
					process({});
					return;
				}
				for (var i = 0; i < data.length; i++) {
					data[i].name = data[i].firstName + " " + data[i].lastName + " <" + data[i].username + ">";
				}
				process(data);
			});
			get_users.fail(function () {
				process({});
			});
		},

		// play with the content of the inserted mention
		// send message and insert fullname in blue color
		insert(item) {
			var global_id = $('.rs-global-id a:not(.recordInfoIcon)').text();
			var id = global_id.replace(/\D/g, '');
			var name = $('#recordNameInHeader').text();
			var userRole = "userRole=" + item.role;
			var recipientnames = "&recipientnames=" + item.username;
			var messageType = "&messageType=SIMPLE_MESSAGE";
			var optionalMessage = "&optionalMessage=You were mentioned in " + name + " ( " + global_id + " ).";
			var targetFinderPolicy = "&targetFinderPolicy=ALL";
			var recordId = "&recordId=" + id;
			var requestParams = userRole + recipientnames + messageType + optionalMessage + targetFinderPolicy + recordId;
			var jqxhr = $.post('/messaging/ajax/createMention', requestParams);
			jqxhr.fail(function () {
				RS.ajaxFailed("Sending message", false, jqxhr);
			});
			return '<span style="color: #1465b7"> + ' + item.fullname + ' </span>&nbsp;';
		}
	}
};

tinymce.PluginManager.add('commandpalette', function (editor) {
  if(!window.insertActions) window.insertActions = new Map();
  window.insertActions.set("charmap", {
    text: 'Special character...',
    icon: 'insert-character',
    action: () => {
      editor.execCommand('mceShowCharmap');
    },
  });
  window.insertActions.set("nonbreaking", {
    text: 'Nonbreaking space',
    icon: 'non-breaking',
    action: () => {
      editor.execCommand('mceNonBreaking');
    },
  });
  window.insertActions.set("insertdate", {
    text: 'Date',
    icon: 'insert-time',
    action: () => {
      editor.execCommand('mceInsertDate');
    },
  });
  window.insertActions.set("inserttime", {
    text: 'Time',
    icon: 'insert-time',
    action: () => {
      editor.execCommand('mceInsertTime');
    },
  });
  window.insertActions.set("hr", {
    text: 'Horizontal line',
    icon: 'horizontal-rule',
    action: () => {
      editor.execCommand('InsertHorizontalRule');
    },
  });
  window.insertActions.set("link", {
    text: 'External link',
    icon: 'link',
    action: () => {
      editor.execCommand('mceLink');
    },
  });
  window.insertActions.set("codesample", {
    text: 'Code sample',
    icon: 'code-sample',
    action: () => {
      editor.execCommand('CodeSample');
    },
  });

  editor.ui.registry.addAutocompleter('insertActions', {
    ch: '/',
    minChars: 0,
    columns: 1,
    fetch: function (pattern) {
      const matchedActions = [...window.insertActions.values()].filter((action) => {
        // Normalize the input to match the same action regardless if the end-user used upper or lowercase.
        return (
            action.type === 'separator' ||
            action.text.toLowerCase().indexOf(pattern.toLowerCase()) !== -1
        );
      }).filter((action, i, actions) => {
        // As the end-user filters the list, separators can end up adjacent to
        // each other which looks bad. This function resolves that
          const prevAction = actions[i - 1];
          const nextAction = actions[i + 1];
          const isRedundantSeparator = action.type === 'separator' && (
              !prevAction ||
              !nextAction ||
              prevAction.type === 'separator' ||
              nextAction.type === 'separator'
          );
          return !isRedundantSeparator;
      });

      // Here the matched autocompleter objects are built
      // https://www.tiny.cloud/docs/ui-components/autocompleter/#autocompleteitem
      return new tinymce.util.Promise(function (resolve) {
        resolve(matchedActions.map((action) => ({
          meta: action,
          text: action.text,
          icon: action.icon,
          value: action.text,
          type: action.type
        })));
      });
    },
    onAction: function (autocompleteApi, rng, action, meta) {
      // In this use-case we want to remove the trigger character and any
      // further characters the user typed to filter the actions.
      // We begin by creating a selection around those characters
      // https://www.tiny.cloud/docs/api/tinymce.dom/tinymce.dom.selection/
      editor.selection.setRng(rng);

      // Delete the selection
      // https://www.tiny.cloud/docs/api/tinymce/tinymce.editor/#execcommand
      editor.execCommand('Delete');

      // Perform the selected action
      meta.action();

      // Finally we hide the autocompleter menu
      // https://www.tiny.cloud/docs/ui-components/autocompleter/#api
      autocompleteApi.hide();
    },
  });
  return {};
});

var initTinyMCE_cachedPropertiesResponse;
var initTinyMCE_cachedIntegrationsResponse;

function initTinyMCE(selector) {
	var localTinymcesetup = tinymcesetup;
	localTinymcesetup.selector = selector;
	var toolbarRequest, propertiesRequest, integrationsRequest;

	// load toolbar configuration
	if (localStorage.getItem('custom_toolbar')) {
		let config = Object.values(JSON.parse(localStorage.getItem('custom_toolbar')));
		toolbarRequest = $.Deferred()
		toolbarRequest.resolve(config);
	} else {
		toolbarRequest = $.getJSON("/scripts/externalTinymcePlugins/toolbar/config.json");
	}
	toolbarRequest.done(function (toolbarResponse) {
		localTinymcesetup.toolbar = toolbarResponse['default'] || toolbarResponse;
	});

	// load the map of deployment properties to see which buttons are globally enabled
	if (initTinyMCE_cachedPropertiesResponse) {
		console.log('using cached deploymentproperties/ajax/properties response');
		propertiesRequest = $.Deferred()
		propertiesRequest.resolve(initTinyMCE_cachedPropertiesResponse);
	} else {
		propertiesRequest = $.get("/deploymentproperties/ajax/properties");
	}

	// load the map of integration preferences to see which options user have enabled
	if (initTinyMCE_cachedIntegrationsResponse) {
		console.log('using cached /integration/allIntegrations response');
		integrationsRequest = $.Deferred()
		integrationsRequest.resolve(initTinyMCE_cachedIntegrationsResponse);
	} else {
		integrationsRequest = $.get("/integration/allIntegrations");
	}

	let requestsPromise = $.when(propertiesRequest, integrationsRequest, toolbarRequest);
	requestsPromise.done(function (propertiesResponse, integrationsResponse, toolbarResponse) {

		initTinyMCE_cachedPropertiesResponse = propertiesResponse;
		initTinyMCE_cachedIntegrationsResponse = integrationsResponse;

		var properties = propertiesResponse[0];
		var integrations = integrationsResponse[0].data;

		var dropboxEnabled     = integrations.DROPBOX.enabled && integrations.DROPBOX.available && integrations.DROPBOX.options['dropbox.linking.enabled'];
		var boxEnabled         = integrations.BOX.enabled && integrations.BOX.available && integrations.BOX.options['box.linking.enabled'];
		var oneDriveEnabled    = integrations.ONEDRIVE.enabled && integrations.ONEDRIVE.available && integrations.ONEDRIVE.options['onedrive.linking.enabled'];
		var googleDriveEnabled = integrations.GOOGLEDRIVE.enabled && integrations.GOOGLEDRIVE.available && integrations.GOOGLEDRIVE.options['googledrive.linking.enabled'];
		var egnyteEnabled      = integrations.EGNYTE.enabled && integrations.EGNYTE.available;
		var gitHubEnabled      = integrations.GITHUB.enabled && integrations.GITHUB.available;
		var chemistryEnabled      = integrations.CHEMISTRY.enabled && integrations.CHEMISTRY.available;
		var mendeleyEnabled    = integrations.MENDELEY.enabled && integrations.MENDELEY.available;
		var protocolsIOEnabled = integrations.PROTOCOLS_IO.enabled && integrations.PROTOCOLS_IO.available;
		var ownCloudEnabled    = integrations.OWNCLOUD.enabled && integrations.OWNCLOUD.available && properties["ownCloud.url"] !== '';
		var nextCloudEnabled    = integrations.NEXTCLOUD.enabled && integrations.NEXTCLOUD.available && properties["nextcloud.url"] !== '';
		let pyratEnabled       = integrations.PYRAT.enabled && integrations.PYRAT.available && properties["pyrat.url"] !== "";
		const clustermarketEnabled =  integrations.CLUSTERMARKET.enabled && integrations.CLUSTERMARKET.available && properties["clustermarket.web.url"] !== "";
		const omeroEnabled =  integrations.OMERO.enabled && integrations.OMERO.available && properties["omero.api.url"] !== "";
		const joveEnabled =  integrations.JOVE.enabled && integrations.JOVE.available;

		// File repositories section
		var enabledFileRepositories = "";
		var fileRepositoriesMenu = "";
		if (dropboxEnabled) {
			localTinymcesetup.external_plugins["dropbox"] = "/scripts/externalTinymcePlugins/dropbox/plugin.min.js";
			enabledFileRepositories += " dropbox";
			fileRepositoriesMenu += " optDropbox";
		}
		if (clustermarketEnabled) {
			localTinymcesetup.external_plugins["clustermarket"] = "/scripts/externalTinymcePlugins/clustermarket/plugin.min.js";
			localTinymcesetup.clustermarket_api_url = properties["clustermarket.api.url"];
			localTinymcesetup.clustermarket_web_url = properties["clustermarket.web.url"];
			enabledFileRepositories += " clustermarket";
			fileRepositoriesMenu += " optClustermarket";
		}
		if (omeroEnabled) {
			localTinymcesetup.external_plugins["omero"] = "/scripts/externalTinymcePlugins/omero/plugin.min.js";
			localTinymcesetup.omero_web_url = properties["omero.api.url"];
			enabledFileRepositories += " omero";
			fileRepositoriesMenu += " optOmero";
		}
		if (joveEnabled) {
			localTinymcesetup.external_plugins["jove"] = "/scripts/externalTinymcePlugins/jove/plugin.min.js";
			localTinymcesetup.jove_api_url = properties["jove.api.url"];
			enabledFileRepositories += " jove";
			fileRepositoriesMenu += " optJove";
		}
		if (boxEnabled) {
			localTinymcesetup.external_plugins["box"] = "/scripts/externalTinymcePlugins/box/plugin.min.js";
			enabledFileRepositories += " box";
			fileRepositoriesMenu += " optBox";
		}
		if (oneDriveEnabled) {
			localTinymcesetup.external_plugins["onedrive"] = "/scripts/externalTinymcePlugins/onedrive/plugin.min.js";
			enabledFileRepositories += " onedrive";
			fileRepositoriesMenu += " optOneDrive";
		}
		if (googleDriveEnabled) {
			localTinymcesetup.external_plugins["googledrive"] = "/scripts/externalTinymcePlugins/googledrive/plugin.min.js";
			enabledFileRepositories += " googledrive";
			fileRepositoriesMenu += " optGoogleDrive";
		}
		if (egnyteEnabled) {
			localTinymcesetup.external_plugins["egnyte"] = "/scripts/externalTinymcePlugins/egnyte/plugin.min.js";
			enabledFileRepositories += " egnyte";
			fileRepositoriesMenu += " optEgnyte";
		}
		if (gitHubEnabled) {
			localTinymcesetup.external_plugins["github"] = "/scripts/externalTinymcePlugins/github/plugin.min.js";
			enabledFileRepositories += " github";
			fileRepositoriesMenu += " optGitHub";
		}
		if (ownCloudEnabled) {
			localTinymcesetup.external_plugins["owncloud"] = "/scripts/externalTinymcePlugins/owncloud/plugin.min.js";
			enabledFileRepositories += " owncloud";
			fileRepositoriesMenu += " optOwnCloud";
		}
		if (nextCloudEnabled) {
			localTinymcesetup.external_plugins["nextcloud"] = "/scripts/externalTinymcePlugins/nextcloud/plugin.min.js";
			enabledFileRepositories += " nextcloud";
			fileRepositoriesMenu += " optNextCloud";
		}
		if (enabledFileRepositories) {
			addToToolbarIfNotPresent(localTinymcesetup, " | " + enabledFileRepositories);
			addToMenuIfNotPresent(localTinymcesetup, " | " + fileRepositoriesMenu);
		}
		if (protocolsIOEnabled) {
			localTinymcesetup.external_plugins["protocols_io"] = "/scripts/externalTinymcePlugins/protocols_io/plugin.min.js";
			localTinymcesetup.protocols_io_access_token = getAccessToken(integrations.PROTOCOLS_IO);
			addToToolbarIfNotPresent(localTinymcesetup, " | protocols_io");
			addToMenuIfNotPresent(localTinymcesetup, " | optProtocols_io");
		}
		if (pyratEnabled) {
			localTinymcesetup.external_plugins["pyrat"] = "/scripts/externalTinymcePlugins/pyrat/plugin.min.js";
			addToToolbarIfNotPresent(localTinymcesetup, " | pyrat");
			addToMenuIfNotPresent(localTinymcesetup, " | optPyrat");
		}
		if (mendeleyEnabled) {
			localTinymcesetup.external_plugins["mendeley"] = "/scripts/externalTinymcePlugins/mendeley/plugin.min.js";
			addToToolbarIfNotPresent(localTinymcesetup, " | mendeley");
			addToMenuIfNotPresent(localTinymcesetup, " | optMendeley");
		}
		if (chemistryEnabled) {
			localTinymcesetup.external_plugins["cheminfo"] = "/scripts/externalTinymcePlugins/chemInfo/plugin.min.js";
		}
	});

	requestsPromise.fail(function () {
		console.log('properties, integrations or toolbar call failed - starting with default tinymce settings');
	});

	let tinymceInitialisedDeferred = $.Deferred();
	requestsPromise.always(function () {
		if (localTinymcesetup.toolbar) {
			complete2ndToolbar(localTinymcesetup);
		}
		$('#' + selector).tinymce(localTinymcesetup);
		tinyMCE.activeEditor.on("init", function () {
			tinymceInitialisedDeferred.resolve();
		});
	});

	return tinymceInitialisedDeferred.promise();
}

// reset tinymce
function resetTinyMCE() {
	var id = tinymce.activeEditor.id;
	tinymce.activeEditor.destroy();
	initTinyMCE(id);
}

function runAfterTinymceActiveEditorInitialized(afterInitCallback) {
	if (tinyMCE.activeEditor && tinyMCE.activeEditor.initialized !== true) {
		console.log("noticed tinymce editor during initialization, will wait until it's complete");
		tinyMCE.activeEditor.on("init", afterInitCallback);
	} else {
		afterInitCallback();
	}
	return;
}

function addToToolbarIfNotPresent(localTinymcesetup, toolBarName) {
	if (localTinymcesetup.toolbar[1].indexOf(toolBarName) === -1) {
		localTinymcesetup.toolbar[1] = localTinymcesetup.toolbar[1] + toolBarName;
	}
}

function complete2ndToolbar(localTinymcesetup) {
	addToToolbarIfNotPresent(localTinymcesetup, localTinymcesetup.toolbar[1]);
}

function addToMenuIfNotPresent(localTinymcesetup, menuName) {
	if (localTinymcesetup.menu.insert.items.indexOf(menuName) === -1) {
		localTinymcesetup.menu.insert.items = localTinymcesetup.menu.insert.items + menuName;
	}
}

function loadVideo(videoId, videoData) {
	var data = videoData.split(",");
	var id = data[0];
	var filename = data[1];
	var extension = data[2];
	openMedia(id, filename, extension);
}

//Handles Dnd into text editor using jqueryFileUpload plugin.
function uploadGalleryFile(event, ed) {
	if (event.dataTransfer) {
		var files = event.dataTransfer.files;
		var fieldEditing = getFieldIdFromTextFieldId(ed.id);
		$('#fromLocalComputerToGallery_' + fieldEditing).fileupload('add', { files: files });
	}
}

//Handles Dnd into text editor using jqueryFileUpload plugin for chem
function uploadChemFile(event, ed) {
	if (event.dataTransfer) {
		let files = event.dataTransfer.files;
		// fetch accepted extensions
		$.ajax({
			url: "/chemical/ajax/supportedFileTypes",
			success: function (data) {
				let allowed_extensions = data.data, extension = null;

				files = Array.from(files);
				let allowed_files = files.filter(file => allowed_extensions.includes(file.name.split('.').pop()));
				let removed_files = files.filter(file => !allowed_extensions.includes(file.name.split('.').pop()));

				if (removed_files.length) {
					RS.confirm(`The following file${removed_files.length > 1 ? 's have' : ' has'} an incompatible extension: <ul>${removed_files.map(f => `<li>${f.name}</li>`)}</ul>`, 'error', 5000 + removed_files.length * 1000);
				}
				if (allowed_files.length) {
					var fieldEditing = getFieldIdFromTextFieldId(ed.id);
					$(`#fromLocalComputerToChem_${fieldEditing}`).fileupload('add', { files: allowed_files });
				}
			}
		});
	}
}

function showFieldHint($fieldName, message) {
	$fieldName.find('.fieldHint')
		.html(message)
		.toggle(message !== "");
}

function hideFieldHint($fieldName, message) {
	showFieldHint($fieldName, "");
}

/* looks to see for file upload if a link is selected, if this is the case
 * then fileupload will use this same link.
 * result = 0 no link selected
 * result = -1 more than one links selected
 * result = any number id attachment selected. */
function checkSelection(ed) {
	var result = "0";
	var contentSelection = ed.selection.getContent();
	if (contentSelection != "") {
		var initAttach = contentSelection.indexOf("<a id=");
		var endAttach = contentSelection.indexOf("</a>");

		//if it find more than one occurrence of link it returns -1
		if (initAttach != -1) {
			if (contentSelection.match(/<a id=/g).length > 1) {
				result = -1;
			} else {
				var attachElement = $(contentSelection.substr(initAttach, endAttach + 2));
				result = $(attachElement).attr('id');
			}
		}
	}
	return result;
}

function getAccessToken(integration) {
	var accessToken = null;
	$.each(integration.options, function (k, v) {

		if (k === 'ACCESS_TOKEN')
			accessToken = 'Bearer ' + v;
	});
	return accessToken;
}

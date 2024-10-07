/**
 * Handles move, copy, delete, rename, publish, select/deselect all
 */

function initGalleriesCrudopsDialogs() {
	initRenameGalleriesDialog();
	initMoveGalleriesDialog();
	initPublishGalleriesDialog();
}

function initGalleriesCrudopsActions() {
	$('.galleriesCrudAction').hide();
	$('.galleriesCrudActionSelect').hide();
}

function openMoveGalleriesDialog() {
	var idsToMove = [];
	var namesToMove = [];
	getIdsAndNamesGalleries(idsToMove, namesToMove);
	$('#moveGalleries').data('toMoveIds', idsToMove).dialog('open');
}

function openRenameGalleriesDialog() {
	var selectedChkBxes = $(".inputCheckbox").filter(":checked");
	var $selected = $(selectedChkBxes[0]);
	var selectedId = $selected.data('recordid');
	var isMediaFile = $selected.data('ismediafile');
	var recordName = String($selected.data('recordname')).trim();
	var recordExt = "";
	if (isMediaFile && recordName.indexOf('.') >= 0) {
		recordExt = recordName.substring(recordName.lastIndexOf('.'));
	}
	var recordNameWithoutExt = recordName.substring(0, recordName.length - recordExt.length);

	$('#renameGalleries')
		.data("selectedId", selectedId)
		.data("recordNameWithoutExt", recordNameWithoutExt)
		.data("recordExt", recordExt)
		.dialog("open");
}

function openPublishGalleriesDialog() {
	var selectedChkBxes = $(".inputCheckbox").filter(":checked");
	var recordId = $(selectedChkBxes[0]).attr('data-recordId');
	var publishName = $('#galleryTable').find('img#' + recordId).data('publish-name');

	var loc = window.location;
	var link = loc.protocol + '//' + loc.host + '/public/publish/' + publishName;
	$('#fileLink').attr("href", link);
	$('#fileLink').text(link);

	$('#publishGalleries').dialog("open");
}

function copyGalleries() {

	var idsToCopy = [];
	var namesToCopy = [];

	getIdsAndNamesGalleries(idsToCopy, namesToCopy);

	namesToCopy = $.map(namesToCopy, function (name) {
		var parts = name.split('.');
		var extension = name.substr(name.lastIndexOf('.'));
		var flename = parts.slice(0, parts.length - 1).join(".");

		if (name.lastIndexOf('.') == -1)
			return name + "_copy";
		else
			return flename + "_copy" + extension;
	});

	var data = {
		newName: namesToCopy,
		idToCopy: idsToCopy
	};

	if (idsToCopy.length == 0)
		return false;

	RS.blockPage("Copying...");
	var jxqr = $.post(createURL("/gallery/ajax/copyGalleries"), data,
		function (result) {
			RS.unblockPage();
			if (result.data == true) {
				$('#pageId').val(0);
				gallery();
			} else if (result.errorMsg != null) {
				apprise(getValidationErrorString(result.errorMsg));
			}
		}).always(function () {
			RS.unblockPage();
		}).fail(function () {
			RS.ajaxFailed("Copy", false, jxqr);
		});
}

function initMoveGalleriesDialog() {
	let selectedDestinationId = null;
	$(document).ready(function () {
		RS.switchToBootstrapButton();
		$('#moveGalleries').dialog({
			modal: true,
			autoOpen: false,
			title: "Select target folder",
			open: function (event, ui) {
				$("#galleries-folder-move-path").html("");
				$('#galleries-folder-tree').fileTree({
					root: '/',
					script: createURL('/fileTree/ajax/gallery?' + 'mediatype=' + $('#mediaTypeSelected').val()),
					expandSpeed: 1000,
					collapseSpeed: 1000,
					multiFolder: false
				}, function (file, type) {
					if ("directory" == type) {
						selectedDestinationId = file.slice(0,-1);
						var currDir$ = $('#galleries-folder-tree').find("a[rel='" + file + "']");
						// get parents, then reverse order
						var parents$ = $(currDir$.parents("li").get().reverse());
						var path = "/";
						// construct the path
						parents$.each(function () {
							path = path + $(this).children("a").text() + "/";
						});
						var safePath = RS.escapeHtml(path);
						$("#galleries-folder-move-path").html(safePath);
					}
				});
			},
			buttons: {
				Cancel: function () {
					$(this).dialog('close');
				},
				Move: function () {
					$(this).dialog('close');

					var filesId = [];
					var namesToMove = [];

					getIdsAndNamesGalleries(filesId, namesToMove);
					var data = {
						filesId: filesId,
						mediaType: $('#mediaTypeSelected').val(),
						target: selectedDestinationId,
					};

					RS.blockPage("Moving...");
					var jxqr = $.post(createURL('/gallery/ajax/moveGalleriesElements'), data,
						function (result) {
							RS.unblockPage();
							if (result.data == true) {
								$('#pageId').val(0);
								gallery();
							} else if (result.errorMsg != null) {
								apprise(getValidationErrorString(result.errorMsg));
							}
						});
					jxqr.fail(function () {
						RS.ajaxFailed("Move", true, jqxhr);
						RS.unblockPage();
					});
				}
			}
		});
		RS.switchToJQueryUIButton();
	});
}

function initRenameGalleriesDialog() {
	$(document).ready(function () {
		RS.switchToBootstrapButton();
		$('#renameGalleries').dialog({
			modal: true,
			autoOpen: false,
			title: "Rename",
			open: function (event, ui) {
				var name = $(this).data('recordNameWithoutExt');
				$('#galleryNameInput').val(name);
			},
			buttons: {
				Cancel: function () {
					$('#renameGalleries').dialog('close');
				},
				Rename: function () {
					var recordId = $('#renameGalleries').data('selectedId');
					var extension = $('#renameGalleries').data('recordExt');
					var newName = $('#galleryNameInput').val() + extension;

					if (newName == "") {
						apprise("The new name should not be empty");
					} else {
						var data = { recordId: recordId, newName: newName };
						RS.blockPage("Processing...");
						var jxqr = $.post(createURL("/workspace/editor/structuredDocument/ajax/rename"), data, function (result) {
							RS.unblockPage();
							if (result.data == "Success") {
								$('#galleryTable').find('#' + recordId).find('.nameGallery').text(newName);
								$('#galleryTable').find('img#' + recordId).attr('title', newName);
								$('.infoPanel-name').text(newName); //RSPAC-1595 update info panel after rename
								if (typeof updatePhotoswipeImageName != undefined) {
									updatePhotoswipeImageName(recordId, newName);
								}
								$('#renameGalleries').dialog('close');
								gallery();
							} else if (result.errorMsg != null) {
								apprise(getValidationErrorString(result.errorMsg));
							}
						});
						jxqr.fail(function () {
							RS.ajaxFailed("Renaming", true, jxqr);
						});
					}

				}
			},
			close: function () {
				var selectedChkBxes = $(".inputCheckbox").filter(":checked");
				$(selectedChkBxes).attr('checked', false);
				$('#galleryNameInput').val("");
				initGalleriesCrudopsActions();
			}
		});
		RS.switchToJQueryUIButton();
	});
	$('#renameGalleries').keypress(function (e) {
		if (e.keyCode === 13) {
			$(this).parent().find(".ui-dialog-buttonset button:eq(1)").click();
		}
	});
}

function initPublishGalleriesDialog() {
	$(document).ready(function () {
		RS.switchToBootstrapButton();
		$('#publishGalleries').dialog({
			autoOpen: false,
			height: 180,
			width: 550,
			modal: true,
			title: "Public Link",
			buttons: {
				Cancel: function () {
					$('#publishGalleries').dialog('close');
				},
				OK: function () {
					$('#publishGalleries').dialog('close');
				}
			},
			close: function () {
				var selectedChkBxes = $(".inputCheckbox").filter(":checked");
				$(selectedChkBxes).attr('checked', false);
				initGalleriesCrudopsActions();
			}
		});
		RS.switchToJQueryUIButton();
	});
}
/*
- takes 1 parameter 'isDMPs' if it is a DMP being deleted
*/
function deleteGalleries(isDMPs) {
    isDMPs = isDMPs || false;
	var idsToDelete = [];
	var namesToDelete = [];
	getIdsAndNamesGalleries(idsToDelete, namesToDelete);
	if (!idsToDelete.length) return false;

	var callback = function () {
		RS.blockPage("Removing items...");
		var data = { idsToDelete: idsToDelete };
		var jxqr = $.post(createURL("/gallery/ajax/deleteElementFromGallery"), data, function (result) {
			if (result.data == true) {
				$('#pageId').val(0);
				var currInfoId = "";
				// it is likely there will always be a getinfo panel displayed for an item
				// we are deleting, but we should be defensive and check we can find a globalId
				// also in DOM there is sometimes an empty, hidden panel, hence the need to iterate
				// RSPAC-1595
				$('.infoPanel-objectIdLink').each(function (i, o) {
					if ($(this).text().length > 2) {
						currInfoId = $(this).text();
					}
				});
				// we might have clicked 'info' panel on an item but not selected it,
				$(data.idsToDelete).each(function (i, idToDelete) {
					if (currInfoId.length > 2 && idToDelete == currInfoId.substring(2)) {
						emptyInfo();
					}
				});
				gallery();
			}
		});
		jxqr.fail(function () {
			RS.ajaxFailed("Delete items failed", true, jxqr);
		});
		jxqr.always(function () {
			RS.unblockPage();
		});
		// Hide gallery crudops
		initGalleriesCrudopsActions();
	}

	var text = idsToDelete.length == 1 ? "this item" : `these ${idsToDelete.length} items`
	var event = new CustomEvent('confirm-action', {
		'detail': {
			title: "Confirm deletion",
			consequences: `Do you want to delete ${text}?`
			 +  `${isDMPs? " Deleting a DMP attachment will also dissociate the DMP from your account.":""}`,
			variant: "warning",
			callback: callback
		}
	});
	document.dispatchEvent(event);
}

/*
 * Takes two empty arrays and will populate them with ids/ names of
 * records corresponding to checkboxes
 * - plus optional 3rs array to hold recordTYpes
 */
function getIdsAndNamesGalleries(idsChecked, namesChecked, typesChecked) {

	$(".inputCheckbox").filter(":checked").each(function (index, value) {
		var recordID = $(value).attr('data-recordid');
		var name = $(value).attr('data-recordname');
		idsChecked.push(recordID);
		namesChecked.push(name);
		if (typesChecked) {
			var type = $(value).attr('data-recordtype');
			typesChecked.push(type);
		}
	});
}

function _showSelectNone() {
	$("#gallerySelectAll").removeClass("active");
}

function _showSelectAll() {
	$("#gallerySelectAll").addClass("active");
}

function _toggleInsertButton(status) {
	var button = $('.ui-dialog-buttonpane').find('button:contains("Insert")');
	if (status == "disabled") {
		button.prop('disabled', true);
	} else {
		button.prop('disabled', false);
	}
}

function updateGalleryCrudMenu() {
	var selectedChkBxes$ = $(".inputCheckbox:checked");
	var selectedFolderChkBxes = $(".folderCheckbox:checked");
	var numSelectedChkBxes = selectedChkBxes$.size();

	if (numSelectedChkBxes == 0) {
		_showSelectAll();
		initGalleriesCrudopsActions();
		_toggleInsertButton("disabled");
		return;
	}

	_showSelectNone();
	_toggleInsertButton("enabled");

	// we only show expanded crudops on gallery page, on dialogs it's just 'insert' button
	if (typeof showExpandedGalleryCrudops === "undefined") {
		$('#galleriesCrudInsert').css('display', 'inline-block');
		return;
	}

	// also no expanded crud operations for network files subgallery
	if ($("#mediaTypeSelected").val() == "NetworkFiles") {
		$('#galleriesCrudInsert').css('display', 'inline-block');
		return;
	}

	$('.galleriesCrudAction').css('display', 'inline-block');
	$('.galleriesCrudActionSelect').css('display', 'inline-block');

	if (numSelectedChkBxes > 1) {
		$('#galleriesCrudRename').hide();
		$('#galleriesCrudPublish').hide();
		$('#galleriesCrudEdit').hide();
	}

	if ($("#mediaTypeSelected").val() != "Images" || selectedFolderChkBxes.size() > 0) {
		$('#galleriesCrudEdit').hide();
	}

	if ($("#mediaTypeSelected").val() != "PdfDocuments") {
		$('#galleriesCrudPublish').hide();
	}

	if ($("#mediaTypeSelected").val() != "DMPs") {
    	$('#galleriesCrudDeleteDMP').hide();
    } else {
        $('#galleriesCrudDelete').hide();
    }

	if ($("#mediaTypeSelected").val() !== "Snippets" || selectedFolderChkBxes.size() !== 0) {
		$('#shareRecord').hide();
	} else if ($("#mediaTypeSelected").val() === "Snippets") {
		$('#galleriesCrudExport').hide();
		$('#shareRecord').show();
	}
	if($('#breadcrumbTag_galleryBcrumb').find(".breadcrumbLink").length>1){
		const midBreadCrumb = $('#breadcrumbTag_galleryBcrumb').find(".breadcrumbLink")[1].text.trim();
		if(midBreadCrumb.toLowerCase() === 'snippets_shared'){
			$('#galleriesCrudRename').hide();
			$('#galleriesCrudPublish').hide();
			$('#galleriesCrudEdit').hide();
			$('#shareRecord').hide();
			$('#galleriesCrudMove').hide();
			$('#galleriesCrudDelete').hide();
			$('#galleriesCrudCopy').hide();
		}
	}
}

$(document).on("click", ".inputCheckbox", function () {
	updateGalleryCrudMenu();
});

$('body').on("click", "#gallerySelectAll", function (e) {
	e.preventDefault();
	var selectAll = $(this).hasClass("active");
	var selector = ".inputCheckbox:visible";
	selector += (selectAll) ? ":not(:checked)" : ":checked";
	//toggle previous values
	$(selector).prop("checked", function (idx, oldProp) {
		return !oldProp;
	});
	updateGalleryCrudMenu();
});

$(document).ready(function (e) {
	let isGallery = typeof isGalleryPage !== "undefined" && isGalleryPage;
	if (isGallery) {
		_setUpShareSnippetDialog();
	}
	$('body').on("click", "#galleriesCrudCopy", function (e) {
		e.preventDefault();
		copyGalleries();
		var selectedChkBxes = $(".inputCheckbox").filter(":checked");
		$(selectedChkBxes).attr('checked', false);
		initGalleriesCrudopsActions();
	});
	$('body').on("click", "#galleriesCrudRename", function (e) {
		e.preventDefault();
		openRenameGalleriesDialog();
	});
	$('body').on("click", "#galleriesCrudMove", function (e) {
		e.preventDefault();
		openMoveGalleriesDialog();
	});
	$('body').on("click", "#galleriesCrudDelete", function (e) {
		e.preventDefault();
		deleteGalleries();
	});
	// this calls regular 'delete' but has a different labelled button RSPAC-2358
	$('body').on("click", "#galleriesCrudDeleteDMP", function (e) {
    	e.preventDefault();
    	deleteGalleries(true);
    });
	$('body').on("click", "#galleriesCrudPublish", function (e) {
		e.preventDefault();
		openPublishGalleriesDialog();
	});
	$('body').on("click", "#galleriesCrudExport", function (e) {
		e.preventDefault();
		//RS.getExportSelectionForExportDlg = getGalleryExportSelectionForExportDlg;
		//openExportArchiveDlg();
		RS.exportModal.openWithExportSelection(getGalleryExportSelectionForExportDlg());
	});
	$('body').on("click", "#galleriesCrudEdit", function (e) {
		e.preventDefault();
		var selectedChkBxes = $(".inputCheckbox").filter(":checked");
		var event = new CustomEvent('open-image-editor', { 'detail': {
			recordid: selectedChkBxes[0].dataset.recordid
		}});
		document.dispatchEvent(event);
	});
});

function _setUpShareSnippetDialog() {
	var idsToShareGetter = function () {
		var recordIdsToShare = [];
		getSelectedIdsAndNames(recordIdsToShare, []);
		return recordIdsToShare;
	};

	//update icons & img title
	var onshare = function (ids) {
		$.each(ids, function (index, item) {
			var img$ = $("tr[data-recordId='" + item + "'] img.sharedStatusImg");
			if (img$.attr("alt") !== 'Shared') {
				img$.attr("src", "/images/documentStatusShared.png").attr(
					"alt", "Shared").attr("title", "Shared");
			}
		});
	};
	createShareDialog("Share Snippet", idsToShareGetter, onshare,
		'#share-dialog', SHARE_SNIPPETS_TYPE);
}
//this overrides a function of same name in core editor - sharing from Gallery fails otherwise
function getSelectedIdsAndNames(ids, names) {
	$("input[class='inputCheckbox']:checked").each(function () {
		var recordID = $(this).attr('data-recordid');
		var name = $(this).attr('data-recordname');
		ids.push(recordID);
		names.push(name);
	});
}
const galleries_getSelectedIdsNamesAndTypes = () => [];

function getGalleryExportSelectionForExportDlg() {
	var ids = [];
	var names = [];
	var types = [];
	getIdsAndNamesGalleries(ids, names, types);
	types = $.map(types, function (item, i) {
		return item === 'Folder' ? 'FOLDER' : 'MEDIA_FILE';
	});
	return {
		'type': 'selection',
		'exportTypes': types,
		'exportNames': names,
		'exportIds': ids
	};
}

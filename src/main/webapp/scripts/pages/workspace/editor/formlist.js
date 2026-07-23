function isEmpty(str) {return (!str || 0 === str.length);}
function isBlank(str) {return (!str || /^\s*$/.test(str)); }

var settings = {
	url : "/workspace/editor/form/ajax/list",
	searchUrl : "/workspace/editor/form/ajax/search",
	urlParams : {
		pageNumber : 0,
		resultsPerPage : 10,
		sortOrder : "ASC",
		orderBy : "name",
		userFormsOnly:"true"
	},
	searchMode : false,
	searchTerm : "",
	orderByActive : false
};
var tableElement = ".mainTable tbody";
var arrowUpElement = "<image src='/images/arrow_up.png' id='orderByArrows' style='vertical-align: middle;'/>";
var arrowDownElement = "<image src='/images/arrow_down.png' id='orderByArrows' style='vertical-align: middle;'/>";

$(document).ready(function (){
	var noOfRows = parseInt($("#noOfRows").val());
	if (noOfRows <= 0) {
		$("#searchModePanel #message").text(RS.msg("legacyjs.workspace.formList.noFormsToManage"));
		$("#searchModePanel").addClass("searchError").slideDown(fadeTime).find("#resetSearch").hide();
		$("#formListContainer").find(".panel, .tabularViewBottom").hide();
	}
	
  setUpContextMenu();
  setupPublishDlg();

  $(".tabularViewBottom").detach().appendTo($("#formListContainer .panel")).attr("style", "");
  correctFieldsetFormProblem();

	if ($("#resultsPerPage").val().length > 0) {
		settings.urlParams.resultsPerPage = parseInt($("#resultsPerPage").val());
	}
	
	setUpDeleteForm();
	setUpCopyForm();	
	setUpPermissionsManage();
	setUpPublishForm();
	setUpAddToMenu();
	setUpRemoveFromMenu();
	setUpUnpublishForm();
	setUpInfoIcon();
	showCorrectUserFormsOrAllFormsRadio();

	var paginationEventHandler = function (source, e){
		RS.blockPage(RS.msg("legacyjs.workspace.formList.loadingForms"), false, $(tableElement));
		var url = createURL(source.attr('id').split("_")[1]);
		var params = RS.getJsonParamsFromUrl(url);
		settings.urlParams.pageNumber = params.pageNumber;
		displayData(settings.searchTerm);		
	};
	RS.setupPagination(paginationEventHandler);


	// Changes the number of records per page
	$(document).on('click', '#applyNumberRecords', function(e){
		e.preventDefault();
		RS.blockPage(RS.msg("legacyjs.workspace.formList.changingPageSize"), false, $(tableElement));
		settings.urlParams.pageNumber = 0;
		settings.urlParams.resultsPerPage = $('#numberRecordsId').val();
		displayData(settings.searchTerm);
	});
	
	// Enables/disables the button to apply new value of number of records per page
	$(document).on('change', '#numberRecordsId', function(e){
		e.preventDefault();
		var newValue = $(this).val();
		if (newValue != settings.urlParams.resultsPerPage) {
			$('#applyNumberRecords').show();
			$(this).addClass('submittable');
		} else {
			$('#applyNumberRecords').hide();
			$(this).removeClass('submittable');
		}
	});

	// Enables/disables the button to apply new value of number of records per page
	$(document).on('click', '.userFormsOnly', function(e){
		var userFormsOnly = $(this).val();
		var msg = userFormsOnly ? RS.msg("legacyjs.workspace.formList.showingMyForms") : RS.msg("legacyjs.workspace.formList.showingAllForms");
		RS.blockPage(msg, false, $(tableElement));
		settings.urlParams.pageNumber = 0;
		settings.urlParams.userFormsOnly = userFormsOnly;
		displayData(settings.searchTerm);
		
	});

  $(document).on("click", "#resetSearch", function(e) {
  	if (!settings.searchMode) return;
  	document.dispatchEvent(new Event('reset-search-input'));
	RS.blockPage(RS.msg("legacyjs.workspace.formList.abandoningSearch"), false, $(tableElement));
  	resetOrdering();
  	displayData("");
  });

  // Ordering buttons handler
	$('body').on('click', '.orderByLink', function(e) {
		e.preventDefault();
		RS.blockPage(RS.msg("legacyjs.workspace.formList.reorderingForms"), false, $(tableElement));
		settings.urlParams.pageNumber = 0;
		settings.urlParams.orderBy = $(this).attr('data-orderby');
		settings.urlParams.sortOrder = $(this).attr('data-sortorder');
		settings.orderByActive = true;
		displayData(settings.searchTerm);
	});
});

function showCorrectUserFormsOrAllFormsRadio() {
	var q = window.location.search;
	var found = q.match(/userFormsOnly=(true|false)/);
	if(found != undefined && found.length == 2){
		$('.userFormsOnly[value="'+ found[1]+ '"]').attr("checked", true);
	} else {
		$('.userFormsOnly[value="true"]').attr("checked", true);
	}
}

function toggleSortOrder() {
	var selectedLink$ = $(".orderByLink[data-orderby='" + settings.urlParams.orderBy + "']");
	$("#orderByArrows").remove();
	if (settings.urlParams.sortOrder === "ASC") {
		selectedLink$.attr('data-sortorder','DESC');
		selectedLink$.parent().append(arrowUpElement);
	} else if (settings.urlParams.sortOrder === "DESC") {
		selectedLink$.attr('data-sortorder','ASC');
		selectedLink$.parent().append(arrowDownElement);
	}
}

/* fetches and displays table contents, using new parameters */
function displayData(searchTerm) {
	console.log("display data", searchTerm)
	// search term too short (and non-empty)
	if (searchTerm.length > 0 && searchTerm.length < RS.minSearchTermLength) {
		RS.unblockPage($(tableElement));
		apprise(RS.msg("legacyjs.workspace.formList.searchTermTooShort"));
		return;
	}

	// resetting search by providing empty term
	if (settings.searchMode && searchTerm.length == 0) {
		settings.searchMode = false;
		settings.searchTerm = "";
		settings.urlParams.pageNumber = 0;
	}
	
	// starting search by providing non-empty term
	if (!settings.searchMode && searchTerm.length > 0) {
		settings.urlParams.pageNumber = 0;
		settings.searchMode = true;
		settings.searchTerm = searchTerm;
	}

	var url = (searchTerm.length > 0 ? settings.searchUrl : settings.url);
	var requestData = $.extend(true, {}, settings.urlParams);
	if (settings.searchMode == true) {
		requestData.searchTerm = settings.searchTerm;
	}
	url = url + "?" + $.param(requestData);
	delete requestData;

	if (RS.webResultCache.get(url) != undefined) {
		insertData(RS.webResultCache.get(url));
		toggleFormActions(false);
		RS.unblockPage($(tableElement));
	} else {
	  	var jxqr = $.get(url, function (data) {
			insertAndCache(url, data, 1000 * 30 ); // save search data or not?				
		}).always(function(){
			toggleFormActions(false);
			RS.unblockPage($(tableElement));				
		}).fail(function() {
			var message = (settings.searchMode ? RS.msg("legacyjs.workspace.formList.searchingFormListAction") : RS.msg("legacyjs.workspace.formList.loadingFormsAction"));
			RS.ajaxFailed(message, false, jxqr);
		});
	}
}

function insertAndCache (url, data, expiryMS) {
	RS.webResultCache.put(url, data, expiryMS );
	insertData(data);
}

function setupPublishDlg (){
	$(document).ready(function() {
		$('#publishShareDlg').dialog({	
		 	modal : true,
			autoOpen:false,
			width: 350,
			title: RS.msg("legacyjs.workspace.formList.configureAccessTitle"),
			buttons :
			{
				[RS.msg("legacyjs.workspace.formList.cancelButton")]: function (){
					$(this).dialog('close');
				},
			
				[RS.msg("legacyjs.workspace.formList.okButton")]: function (){

					// this  dlg is either being called from  'Manager permissions' or 'publish'
					// and we need to distinguish between them as they use different URLs.
					var isManaging = $(this).data('manage');
					var id =$(this).data('id');
					
					$(this).dialog('close');
					var form$=$('#rsFormSharingForm');
					if (isManaging != null){
						form$.attr('action', createURL('/workspace/editor/form/ajax/updateSharePermissions'));
						RS.blockPage(RS.msg("legacyjs.workspace.formList.changingPermissions"), false, $(tableElement));
					} else {
						RS.blockPage(RS.msg("legacyjs.workspace.formList.publishing"), false, $(tableElement));
					}
					var data = form$.serialize();
					
					$.ajax({ url : form$.attr('action'),
		                type : form$.attr('method'),
		                dataType: 'json',
		                data : data,
		                success : function( response ) { 
		            	    // if we were publishing, we need to activate the unpublish link
		            	    if (isManaging == null){		
		            			updatePublishingStateText(id, 'PUBLISHED');
								$('.publish').hide();
								$('.unpublish').show();
								_setValueForInputName (id, 'publishingstate', 'PUBLISHED');
		            	    } else {
								var form$=$('#rsFormSharingForm');
								// make sure to replace URL 
								form$.attr('action',createURL("/workspace/editor/form/ajax/publishAndShare"));
		            	    }
		            	},
		                error : function( xhr, err ) { alert(RS.msg("legacyjs.workspace.formList.genericError")); }
		            }).always(function(){
		            	RS.unblockPage($(tableElement));
		            });					
				}
			},
		});
	});
}

function setUpDeleteForm() {
	// deletes the form if it's new
	$(document).on("click", ".deleteForm", function(e) {
		e.preventDefault();
		RS.blockPage(RS.msg("legacyjs.workspace.formList.deletingForms"), false, $(tableElement));
		var ids = getSelectedIds();
		var jxqr = $.post(createURL("/workspace/editor/form/ajax/deleteForm"), {templateId : ids}, function(response) {
			RS.webResultCache.clearAll();
			if (response.data != null) {
				toggleFormActions(false);
			} else {
				RS.unblockPage($(tableElement));
				apprise(RS.msg("legacyjs.workspace.formList.deleteFailed", response.errorMsg.errorMessages.join(" ")));
			}
			displayData(settings.searchTerm);
		}).fail(function (){
			RS.unblockPage($(tableElement));
			RS.ajaxFailed(RS.msg("legacyjs.workspace.formList.deleteFormAction"), false, jxqr);
		});		
	});
	RS.emulateKeyboardClick('.deleteForm');
}

function setUpCopyForm (){
	$(document).on("click", ".copyForm", function (e){
		e.preventDefault();
		RS.blockPage(RS.msg("legacyjs.workspace.formList.copyingForms"), false, $(tableElement));
		var ids = getSelectedIds();
		$.post(createURL("/workspace/editor/form/ajax/copyForm"),
			{templateId: ids},
			function (response) {
				// this was originally injecting the response data into the table, but
				// it didn't respect resultsPerPage and pageNumber parameters, so now
				// another request is made in displayData(), which respects it all
				RS.webResultCache.clearAll();
				toggleFormActions(false);
				displayData(settings.searchTerm);
			}
		).fail(function(){
			// unblocking only if something went wrong, otherwise unblocked in displayData()
			RS.unblockPage($(tableElement));
		});
	});
	RS.emulateKeyboardClick('.copyForm');
}
/*
 * Takes two empty arrays and populates them with ids/ names of records
 * corresponding to checkboxes
 */
function getSelectedIds() {
	var ids = [];
	$("input[class='form_checkbox']:checked").each(function() {
		var id = $(this).data('formid');
		ids.push(id);
	});
	return ids;
}

function setUpPermissionsManage (){
	$(document).on("click", ".managePermissions", function (e){
		e.preventDefault();	
		RS.blockPage(RS.msg("legacyjs.workspace.formList.openingPermissionsTools"), false, $(tableElement));
		var ids = getSelectedIds();
		var data = {
			publish:true,
			templateId:ids[0]
		};
		$.get(createURL("/workspace/editor/form/ajax/publishAndShare?templateId="+ids[0]),
			function (data) {
				$('#publishShareDlgContent').html(data);
				$('#publishShareDlg').data('manage', 'true'); // we're managing, not publishing
				$('#publishShareDlg').dialog('open');
				displayData(settings.searchTerm);
			}
		).fail(function(){
			RS.unblockPage($(tableElement));
		});
	});
	RS.emulateKeyboardClick('.managePermissions');
}

function setUpPublishForm(){
	$(document).on("click", ".publish", function (e){
		e.preventDefault();
		RS.blockPage(RS.msg("legacyjs.workspace.formList.openingPublishingTools"), false, $(tableElement));
		var ids = getSelectedIds();
		var data = {
			publish:true,
			templateId:ids[0]
		};
	
		$.get(createURL("/workspace/editor/form/ajax/publishAndShare?templateId="+ids[0]),
			function (data) {
				$('#publishShareDlgContent').html(data);
				$('#publishShareDlg').data('id', ids[0]).dialog('open');
				displayData(settings.searchTerm);
			}
		).fail(function(){
			RS.unblockPage($(tableElement));
		});
	});
	RS.emulateKeyboardClick('.publish');
}

function setUpAddToMenu (){
	$(document).on("click", ".addToMenu", function (e){
		e.preventDefault();
		RS.blockPage(RS.msg("legacyjs.workspace.formList.addingToCreateMenu"), false, $(tableElement));
		var link$ = $(this);
		var ids = getSelectedIds();// only 1 at a time allowed just now
		var data = {
				menu:true,
				formId:ids[0]
			};
		var jxqr = $.post(createURL("/workspace/editor/form/ajax/menutoggle"),
			data,
			function(response) {
				// toggle menu items 
				link$.hide();
				$('.removeFromMenu').show();
				_setValueForInputName (ids[0], 'menu', 'true');
			}
		).always(function(){
			RS.unblockPage($(tableElement));
		}).fail(function(){
			RS.ajaxFailed(RS.msg("legacyjs.workspace.formList.addToMenuAction"),false,jxqr);
		});
	});
	RS.emulateKeyboardClick('.addToMenu');
}

function setUpRemoveFromMenu (){
	$(document).on("click", ".removeFromMenu", function (e){
		e.preventDefault();
		RS.blockPage(RS.msg("legacyjs.workspace.formList.removingFromCreateMenu"), false, $(tableElement));
		var link$ = $(this);
		var ids = getSelectedIds();
		var data = {
			menu:false,
			formId:ids[0]
		};
		var jxqr = $.post(createURL("/workspace/editor/form/ajax/menutoggle"),
			data,
			function(response) {
				link$.hide();
				$('.addToMenu').show();
				_setValueForInputName (ids[0], 'menu', 'false');
			}
		).always(function(){
			RS.unblockPage($(tableElement));
		}).fail(function(){
			RS.ajaxFailed(RS.msg("legacyjs.workspace.formList.removeFromMenuAction"),false,jxqr);
		});
	});
	RS.emulateKeyboardClick('.removeFromMenu');
}

function updatePublishingStateText (id, text){
	$("tr[data-formid='"+id+"']").find('td.publishingState').text(text);
	$("tr[data-formid='"+id+"']").find('input').data('publishingstate',text);
}

function setUpUnpublishForm(){
	$(document).on("click", ".unpublish", function (e){
		e.preventDefault();
		RS.blockPage(RS.msg("legacyjs.workspace.formList.unpublishingForms"), false, $(tableElement));
		var link$ = $(this);
		var ids = getSelectedIds();
		var data = {
			publish:false,
			templateId:ids[0]
		};
		var jxqr = $.post(createURL("/workspace/editor/form/ajax/publish"),
			data,
			function (response) {
				$.each(ids, function(index,value){
					updatePublishingStateText(value, 'UNPUBLISHED');
					link$.hide();
					$('.publish').show();
					_setValueForInputName (ids[0], 'publishingstate', 'UNPUBLISHED');
				});
			}
		).always(function(){
			RS.unblockPage($(tableElement));
		}).fail(function(){
			RS.ajaxFailed(RS.msg("legacyjs.workspace.formList.unpublishAction"),false,jxqr);
		});
	});
	RS.emulateKeyboardClick('.unpublish');
}

function setUpContextMenu (){
	toggleFormActions(false);
	$(document).on('click', '.form_checkbox', function (){
		// hide menu if no records are checked.
		var selectedChkBxes$ = $("input[class='form_checkbox']:checked");
		var numSelectedChkBxes = selectedChkBxes$.size();
		if (numSelectedChkBxes == 0) {
			toggleFormActions(false);
			return;
		} else {
			toggleFormActions(true);
		}
		if (numSelectedChkBxes == 1) {
			$('.single').show();
			calculateOptionDisplay(selectedChkBxes$);			
		} else {
			calculateOptionDisplay(selectedChkBxes$);
			$('.single').hide();			
		}
	});
}

function calculateOptionDisplay(selectedChkBxes) {
	var hideDelete = false;
	var hidePublish = false;
	var hideUnPublish = false;
	var hideAddToMenu = false;
	var hideRemoveFromMenu = false;
	var hidePermissions = false;
	
	selectedChkBxes.each(function() {
	
		var cbox$ = $(this);
		var id = cbox$.data('formid');
		console.log("Del" + _getValueForInputName(id,'deletable') + " pub:" + _getValueForInputName(id,'publishingstate') + ",menu:" +_getValueForInputName(id,'menu') );
		if  (_getValueForInputName(id,'deletable') != "true" ){
			hideDelete = true;
		}
		var publishState = _getValueForInputName(id,'publishingstate');
		var canPublish = _getValueForInputName(id,'permissionpublish');
		if(canPublish == 'false') {
			hidePublish = true;
			hideUnPublish = true;
		} else if(publishState == 'PUBLISHED') {
			hidePublish = true;
		} else if (publishState == 'UNPUBLISHED' || publishState == 'NEW') {
			hideUnPublish = true;
		}  
		var menuState = _getValueForInputName(id,'menu');
		if(publishState != 'PUBLISHED') {
			hideAddToMenu = true;
			hideRemoveFromMenu = true;
		} else if(menuState == 'true') {
			hideAddToMenu = true;
		} else if (menuState == 'false') {
			hideRemoveFromMenu = true;
		} 
		if(_getValueForInputName(id,'permissionsedit')=='false') {
			hidePermissions = true;
		} 
	});
	if(hideDelete) {
		$('.deleteForm').hide();
	} 
	if(hidePublish) {
		$('.publish').hide();
	} 
	if(hideUnPublish) {
		$('.unpublish').hide();
	} 
	if(hideAddToMenu) {
		$('.addToMenu').hide();
	}
	if(hideRemoveFromMenu) {
		$('.removeFromMenu').hide();
	}
	if(hidePermissions) {
		$('.managePermissions').hide();
	}
}

function setUpInfoIcon() {
	$(document).on("click", ".infoImg", function() {
		RS.blockPage(RS.msg("legacyjs.workspace.formList.openingRecordInfo"), false, $(tableElement));
		var formId = $(this).attr("data-formid");
		$.get("/workspace/editor/form/ajax/getFormInformation", { templateId: formId }, function(data, status) {
			if (data.errorMsg != null) {
				alert(data.errorMsg.errorMessages[0]);
			} else {
        var val = data.data;
        if (val !== null) {
          var $infoArea = $('#recordInfoDialog');
          var $recordInfoPanel = generate$RecordInfoPanel(val);
          $infoArea.find('.recordInfoPanel').replaceWith($recordInfoPanel);
          $infoArea.dialog('open');
        }
			}
		}).always(function(){
			RS.unblockPage($(tableElement));
		});
	});
	RS.emulateKeyboardClick('.infoImg');
}

function _getValueForInputName (id, inputName) {
	return $("tr[data-formid='"+id+"']").find("input[name='"+inputName+"']").val();
}

function _setValueForInputName (id, inputName, val) {
	$("tr[data-formid='"+id+"']").find("input[name='"+inputName+"']").val(val);
}

// TO-DO: Find out why the fieldset jumps out of the form element and try to
// get rid of this behaviour so this function doesn't have to exist.
function correctFieldsetFormProblem() {
	$(".tabularViewBottom fieldset").detach().appendTo($(".tabularViewBottom .numRecordsForm"));
}

/* Inject data into table, show appropriate message when no data (no records or empty search results) */
function insertData(html) {
	var noOfRows = parseInt($('<div/>').html(html).find("#noOfRows").val());

	if (noOfRows > 0) {
		$("#searchModePanel").removeClass("searchError").addClass("searchSuccess");
		if (settings.searchMode) {
			var message = RS.msg("legacyjs.workspace.formList.searchResultsShown", RS.escapeHtml(settings.searchTerm));
			$("#searchModePanel #message").html(message);
			$("#resetSearch").show();
			$("#searchModePanel").slideDown(fadeTime);
		} else {
			$("#searchModePanel").slideUp(fadeTime);
		}
		$("#formListContainer").find(".panel table tbody, .tabularViewBottom").remove();
		var tableBody = $('<tbody/>');
		tableBody.html(html);
		tableBody.appendTo($("#formListContainer table"));
		tableBody.find(".tabularViewBottom")
			.detach().appendTo($("#formListContainer .panel"))
			.attr("style", "");
		$("#formListContainer").find(".panel, .tabularViewBottom").show();
		correctFieldsetFormProblem();

		// RSPAC-1212 Blocking: Setting the height to fit the height of the previously displayed content and the blocking message
		if ($(tableElement).height() < parseInt(temporaryHeightOfBlockedElement)) {
			$(tableElement).css({height : temporaryHeightOfBlockedElement});			
		}

		if (settings.orderByActive) {
			toggleSortOrder();	
		}
	} else {
		$("#searchModePanel").removeClass("searchSuccess").addClass("searchError");
		if (settings.searchMode) {
			$("#searchModePanel #message").html(RS.msg("legacyjs.workspace.formList.searchNoResults", RS.escapeHtml(settings.searchTerm)));
			$("#resetSearch").show();
			$("#searchModePanel").slideDown(fadeTime);
			$("#formListContainer").find(".panel table tbody, .tabularViewBottom").remove();
		} else {
			$("#searchModePanel #message").html(RS.msg("legacyjs.workspace.formList.noFormsToManage"));
			$("#resetSearch").hide();
			$("#searchModePanel").slideDown(fadeTime);
			$("#formListContainer").find(".panel table tbody, .tabularViewBottom").remove();
		}		
	}
}

function toggleFormActions(show) {
	if (show) {
		$('.formAction').show();
		$('#formActions').addClass('breadcrumbForm').slideDown(fadeTime);
	} else {
		$('#formActions').removeClass('breadcrumbForm').slideUp(fadeTime);
		$('.formAction').hide();
	}
}

function handleSearchForms(){
	var searchTerm = $('#search-form-list-input').val().trim();
	console.log(searchTerm);
	RS.blockPage(RS.msg("legacyjs.workspace.formList.searching"), false, $(tableElement));

	// page number reset every time new search is made (even with empty term)
	settings.urlParams.pageNumber = 0;
	settings.searchTerm = searchTerm;
	resetOrdering();

	displayData(searchTerm);
}

function resetOrdering() {
	$("#orderByArrows").remove();
	settings.orderByActive = false;
	settings.urlParams.sortOrder = "ASC";
	settings.urlParams.orderBy = "name";
}

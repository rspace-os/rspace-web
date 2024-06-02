var settings = {
	url : "/workspace/trash/ajax/list",
    urlParams : {
        pageNumber : 0,
        resultsPerPage : 10,
        sortOrder : "DESC",
        orderBy : "deletedDate"
    },
 	searchMode : false,
 	searchTerm : "",
 	orderBy : "deletedDate",
 	sortOrder : "DESC",
 	orderByActive : false
};
var tableElement = ".mainTable tbody";
var arrowUpElement = "<image src='/images/arrow_up.png' id='orderByArrows' style='vertical-align: middle;'/>";
var arrowDownElement = "<image src='/images/arrow_down.png' id='orderByArrows' style='vertical-align: middle;'/>";

$(document).ready(function () {
	setUpRestoreHandler();
	var noOfRows = parseInt($("#noOfRows").val());
	if (noOfRows <= 0) {
		$("#searchModePanel #message").text("There are no deleted items to view.");
		$("#searchModePanel").addClass("searchError").slideDown(fadeTime).find("#resetSearch").hide();
		$("#deletedItemsList").hide();
	}
});

// Enables/disables the button to apply new value of number of records per page
$(document).on('change', '#numberRecordsId', function(e){
    e.preventDefault();
    if ($("#resultsPerPage").val().length > 0) {
        settings.urlParams.resultsPerPage = parseInt($("#resultsPerPage").val());
    }
    var newValue = $(this).val();
    if (newValue != settings.urlParams.resultsPerPage) {
        $('#applyNumberRecords').show();
        $(this).addClass('submittable');
    } else {
        $('#applyNumberRecords').hide();
        $(this).removeClass('submittable');
    }
});

$(document).on('click', '#applyNumberRecords', function(e){
    e.preventDefault();
    RS.blockPage("Changing the number of records per page...", false, $(tableElement));
    settings.urlParams.pageNumber = 0;
    settings.urlParams.resultsPerPage = $('#numberRecordsId').val();
    displayData(settings.searchTerm);
});

function searchDeleted() {
	RS.blockPage("Searching in deleted items...", false, $(tableElement));
	var searchTerm = $("#searchDeletedListInput").val();
	settings.searchMode = searchTerm.length > 0;
	settings.searchTerm = searchTerm;
	displayData(searchTerm);
}

// Ordering buttons handler
$('body').on('click', '.orderByLink', function(e) {
	e.preventDefault();
	RS.blockPage("Reordering deleted items...", false, $(tableElement));
	settings.orderBy = $(this).attr('data-orderby');
	settings.sortOrder = $(this).attr('data-sortOrder');
	settings.orderByActive = true;
	displayData(settings.searchTerm);
});

$(document).on("click", "#resetSearch", function() {
	if (!settings.searchMode) return;
	document.dispatchEvent(new Event('reset-search-input'));
	RS.blockPage("Abandoning search...", false, $(tableElement));
	settings.searchMode = false;
	resetOrdering();
	displayData("");
});

function _toggleSortOrder() {
	var selectedLink$ = $(".orderByLink[data-orderby='" + settings.orderBy + "']");
	$("#orderByArrows").remove();
	if (settings.sortOrder === "ASC") {
		selectedLink$.attr('data-sortorder','DESC');
		selectedLink$.parent().append(arrowUpElement);
	} else if (settings.sortOrder === "DESC") {
		selectedLink$.attr('data-sortorder','ASC');
		selectedLink$.parent().append(arrowDownElement);
	}
}

function setUpRestoreHandler() {
	$('body').on('click', '.restore', function(e) {
		e.preventDefault();
		var link$ = $(this);
		var revision = $(this).nextAll('input').eq(0).val();
		var recordId = $(this).nextAll('input').eq(1).val();
		var data = {
			recordId: recordId,
			revision: revision,
			deleted: true
		};
		var url = createURL('/workspace/revisionHistory/restore');
		$.post(url,data, function(rc) {
			if(rc.data == 'EDIT_MODE') {
				$().toastmessage('showToast', {
	        text     : "Restored",
	        sticky   : false,
	        position : 'top-right',
	        type     : 'success',
	        close    : function () {
	        	window.location.href=createURL('/workspace/editor/structuredDocument/'+recordId);
	        }
	      });
			} else if(rc.data.indexOf("Folder") != -1) {
				var recordid = rc.data.split(":")[1];
				window.location.href=createURL('/workspace/'+recordid);

			} else if (rc.data.indexOf("Notebook") != -1) {
				var recordid = rc.data.split(":")[1];
				window.location.href=createURL('/notebookEditor/'+recordid);
			}else if (rc.data.indexOf("Media") != -1) {
				//rspac429
				RS.confirm("Restored deleted media file into the Gallery...", "success", 3000);
				// at the moment we can't show the user the restored image in the gallery
				// so we just remove the row - this removed in the DB and will refresh OK
				link$.closest('tr').remove(); // remove the row, stay in view
			}else {
				RS.confirm("Restored deleted document...", "success", 3000);
				window.location.href=createURL('/workspace/editor/structuredDocument/' + rc.data);
			}
		});
	});
}

function resetOrdering() {
	$("#orderByArrows").remove();
	settings.orderByActive = false;
	settings.sortOrder = "DESC";
	settings.orderBy = "deletedDate";
}
var paginationEventHandler = function(source, e) {
    RS.blockPage("Loading the chosen page...", false, $(tableElement));
    var url = source.attr('id').split("_")[1];
    var params = RS.getJsonParamsFromUrl(url);
    settings.urlParams.pageNumber = params.pageNumber;
    displayData(settings.searchTerm);
};
RS.setupPagination(paginationEventHandler);

function insertAndCache (url, data, expiryMS) {
    RS.webResultCache.put(url, data, expiryMS );
    insertData(data);
}
function displayData(searchTerm) {
    var requestData = $.extend(true, {}, settings.urlParams);
	if (settings.searchMode) {
		if (searchTerm.length < RS.minSearchTermLength) {
			RS.unblockPage($(tableElement));
			apprise("Please enter at least three characters.");
			return;
		} else {
			requestData.name = searchTerm;
		}
	} else {
		$("#searchModePanelf").hide();
	}
	requestData.orderBy = settings.orderBy;
	requestData.sortOrder = settings.sortOrder;

	var url = settings.url + "?" + $.param(requestData);

	var jqxhr = $.get(url, function(data) {
		insertData(data);
		if (settings.orderByActive) {
			_toggleSortOrder();
		}
	}).always(function() {
		RS.unblockPage($(tableElement));
	}).fail(function() {
		RS.ajaxFailed("Getting list of deleted items", false, jqxhr);
	});
}

function insertData(html) {
	var noOfRows = $("<div/>").html(html).find("#noOfRows").val();
	if (noOfRows > 0) {
		$("#searchModePanel").removeClass("searchError").addClass("searchSuccess");
		if (settings.searchMode) {
			var message = "Showing deleted items for search term '" + settings.searchTerm + "'.";
			$("#searchModePanel #message").text(message);
			$("#resetSearch").show();
			$("#searchModePanel").slideDown(fadeTime);
		} else {
			$("#searchModePanel").slideUp(fadeTime);
		}
        $("#deletedItemsList").find(".panel, .tabularViewBottom").remove();
        $("#deletedItemsList").append(html);
		// $('#deletedItemsList .mainTable tbody').html(html);

		// RSPAC-1212 Blocking: Setting the height to fit the height of the previously displayed content and the blocking message
		if ($(tableElement).height() < parseInt(temporaryHeightOfBlockedElement)) {
			$(tableElement).css({height : temporaryHeightOfBlockedElement});
		}

		$('#deletedItemsList').css({opacity : 0}).show().animate({opacity : 1}, fadeTime);
	} else {
		$("#searchModePanel").removeClass("searchSuccess").addClass("searchError");

		if (settings.searchMode) {
			$("#searchModePanel #message").text("Your search for '" + settings.searchTerm +
				"' returned no results. Please search again or");
			$("#resetSearch").show();
		} else {
			$("#searchModePanel #message").text("There are no deleted items to view.");
			$("#resetSearch").hide();
		}
		$("#searchModePanel").slideDown(fadeTime);
		$('#deletedItemsList').hide();
		$('#deletedItemsList .mainTable tbody tr').remove();
	}
}

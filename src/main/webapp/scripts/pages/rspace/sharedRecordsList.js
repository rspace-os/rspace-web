
var settings = {
	url : "/record/share/ajax/manage",
	urlParams : {
		pageNumber : 0,
		resultsPerPage : 10,
		sortOrder : "DESC",
		orderBy : "creationDate"
	},

	searchMode : false,
	searchTerm : "",
	orderByActive : false
};
var tableElement = ".mainTable tbody";

$(document).ready(function (){
	var noOfRows = parseInt($("#noOfRows").val());
	if (noOfRows <= 0) {
		const containsPublicLinks = $("#containsPublicLinks").length > 0;
		if(containsPublicLinks){
			$("#searchModePanel #message").text("There are no published records to manage.");
		} else {
			$("#searchModePanel #message").text("There are no shared records to manage.");
		}
		$("#searchModePanel").addClass("searchError").slideDown(fadeTime).find("#resetSearch").hide();
		$("#sharedRecordsListContainer").find(".panel, .tabularViewBottom").hide();
	}
	$("#sharedDataTable tr:contains('public')").each(function(){
		$( this ).find(".update").prop( "disabled", true );
	});
	$('body').on("click",'.linkShare', function () {
		const link = getLink(this);
		navigator.clipboard.writeText(link).then(()=>console.log('copied link:' + link));
	});
	$('body').on("click",'#copyAllLinks', function () {
		console.log('copyall');
		let links = "";
		$('.linkShare').each(function(){
			links+=(getLink(this) +"\n");
		})
		navigator.clipboard.writeText(links).then(()=>console.log(links));
	});
	const getLink = (element) =>{
		const id = $( element ).attr('copy-target');
		const url = $("#publicLink_"+id).attr('href');
		const itemName = $("tr[data-recordid='"+id+"']").attr('data-recordname');
		return itemName + " "+ window.location.origin +url;
	}

	$('body').on("click",'.unshare', function (e) {
		e.preventDefault();
		RS.blockPage("Unsharing document...", false, $(tableElement));

		var id = $(this).attr('id');
		/* originally, unsharing was done with page reload
		var form$ = $('<form></form>');
		form$.attr('method', 'POST');
		form$.attr('action', createURL('/record/share/unshare'));
		form$.append("<input type='hidden' name='grpShareId' value='" + id + "'>");
		$('body').append(form$);
		form$.submit();
		*/

		RS.webResultCache.clearAll();
		var jxqr = $.post(createURL('/record/share/unshare'), {grpShareId : id}, function(data) {
			let message = "Unshared";
			if(e.target.textContent === 'Unpublish'){
				message = "Unpublished"
			}
			RS.confirm(message, "success", 3000);
			settings.urlParams.pageNumber = 0;
		}).always(function() {
			displayData(settings.searchTerm);
		}).fail(function() {
			RS.ajaxFailed("Unsharing shared document", false, jxqr);
		});
	});

	$("body").on("click", ".orderBy", function(e) {
		e.preventDefault();
		// RS.blockPage("Reordering shared items...", false, $(tableElement));
		var params = RS.getJsonParamsFromUrl($(this).attr("href"));
		settings.urlParams.pageNumber = 0;
		settings.urlParams.orderBy = params.orderBy;
		settings.urlParams.sortOrder = params.sortOrder;
		settings.orderByActive = true;
		displayData(settings.searchTerm);
	});

 	$(document).on("click", "#resetSearch", function(e) {
		if (!settings.searchMode) return;
		document.dispatchEvent(new Event('reset-search-input'));
		RS.blockPage("Abandoning search...", false, $(tableElement));
		displayData("");
	});

	var currSelection;

	$('body').on("change", 'select.update', function(e) {
		RS.blockPage("Updating permissions...", false, $(tableElement));
		currSelection = $(this).val();
		var id = getIdFromRow($(this));
		var jxqr = $.post(createURL('/record/share/permissions'),
			{
				id: id,
			    action: currSelection
			},
			function (data) {
		    	if (currSelection === 'WRITE') { currSelection = 'EDIT'};
		    	$(this).closest('td').html(generateSelectionView(currSelection));
		    	RS.confirm("Updated permissions", "success", 3000);
		    }
		).always(function(){
			RS.unblockPage($(tableElement));
		}).fail(function(){
			RS.ajaxFailed("Updating permissions of the shared document", false, jxqr);
		});
	});

	/*
	$('body').on("click", '.revert', function(e) {
		$(this).closest('td').html(generateSelectionView(currSelection));
	});
	*/

	$(document).on("click", ".cancel", function(e){
		e.preventDefault();
		var requestId = $(this).data("requestid");

		var data = {
			requestId:requestId,
		};

		$.post(createURL('/dashboard/ajax/cancelSharedRecordRequest'),
		data, function (xhr) {
	 		var msg = xhr.data.entity;
	 		$().toastmessage('showToast', {
	 			text     : "<br>" + msg,
				sticky   : false,
				position : 'top-right',
				type     : 'notice',
				stayTime : 3000,
				close : function() {
					if(xhr.data.success) {
						window.location.href = createURL('manage');
					}
				}
	 		});
	 	});
	});

	$(document).on('click', '.recordInfoIcon', function() {
		var id = $(this).parents('tr').data('recordid');
		openRecordInfoDialog(id);
		return false;
	});

	var paginationEventHandler = function(source, e) {
		RS.blockPage("Loading the chosen page...", false, $(tableElement));
		var url = source.attr('id').split("_")[1];
		var params = RS.getJsonParamsFromUrl(url);
		settings.urlParams.pageNumber = params.pageNumber;
		displayData(settings.searchTerm);
	};
	RS.setupPagination(paginationEventHandler);

	// update resultsPerPage based on number remembered (passed from the back end)
	if ($("#resultsPerPage").val().length > 0) {
		settings.urlParams.resultsPerPage = parseInt($("#resultsPerPage").val());
	}

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

	// Changes the number of records per page
  $(document).on('click', '#applyNumberRecords', function(e){
		e.preventDefault();
		RS.blockPage("Changing the number of records per page...", false, $(tableElement));
		settings.urlParams.pageNumber = 0;
		settings.urlParams.resultsPerPage = $('#numberRecordsId').val();
		displayData(settings.searchTerm);
  });
});

function resetOrdering() {
	settings.orderByActive = false;
	settings.urlParams.sortOrder = "ASC";
	settings.urlParams.orderBy = "name";
}

var handleSearchShared = function(e) {
	var searchTerm = $('#searchSharedListInput').val().trim();
	if (!settings.searchMode && searchTerm == "") return;

	RS.blockPage("Searching shared items...", false, $(tableElement));
	searchTerm = searchTerm.replace(/,\s*$/,"");
	settings.urlParams.pageNumber = 0;
	settings.searchTerm = searchTerm;
	resetOrdering();
	displayData(searchTerm);
};

function getIdFromRow(selectBox$) {
	var tr$ = selectBox$.closest('tr');
	return tr$.find('a.unshare').attr('id');
}

function generateSelectionView(currSelection) {
	var localSelection = currSelection;
	if (localSelection === 'READ'){localSelection='Read';}
	else if(localSelection === 'WRITE'){localSelection='Edit';}
	return "<span class='permType'>" + localSelection + "</span><a href='#' class='alterPermissions'><span style='font-size:70%'>Alter</span></a>"
}

function insertData(html) {
	var noOfRows = $("<div/>").html(html).find("#noOfRows").val();

	if (noOfRows > 0) {
		$("#searchModePanel").removeClass("searchError").addClass("searchSuccess");
		if (settings.searchMode) {
			var message = "Showing shared documents for search term '" + settings.searchTerm + "'.";
			$("#searchModePanel #message").text(message);
			$("#resetSearch").show();
			$("#searchModePanel").slideDown(fadeTime);
		} else {
			$("#searchModePanel").slideUp(fadeTime);
		}
		$("#sharedRecordsListContainer").find(".panel, .tabularViewBottom").remove();
		$("#sharedRecordsListContainer").append(html);

		// RSPAC-1212 Blocking: Setting the height to fit the height of the previously displayed content and the blocking message
		if ($(tableElement).height() < parseInt(temporaryHeightOfBlockedElement)) {
			$(tableElement).css({height : temporaryHeightOfBlockedElement});
		}

		//Check to see which table heading was clicked. then assign html ID so we can apply asc/desc
		if (settings.orderByActive) {
			var id;
			switch(settings.urlParams.orderBy) {
				case "name":
					id = "orderByName";
					break;
				case "sharee":
					id = "orderBySharee";
					break;
				case "creationDate":
					id = "orderByCreationDate"
					break;
				default:
					id = "orderByName";
			}

			var href = $("#" + id).attr("href");
			var sortOrderInLink = settings.urlParams.sortOrder == "ASC" ? "DESC" : "ASC";
			_toggleSortOrder(href, sortOrderInLink, id);
		}
	} else {
		$("#searchModePanel").removeClass("searchSuccess").addClass("searchError");
		if (settings.searchMode) {
			$("#searchModePanel #message").text("Your search for '" + settings.searchTerm +
				"' returned no results. Please search again or");
			$("#resetSearch").show();
			$("#searchModePanel").slideDown(fadeTime);
		} else {
			$("#searchModePanel #message").text("There are no shared documents to view.");
			$("#resetSearch").hide();
			$("#searchModePanel").slideDown(fadeTime);
		}
		$("#sharedRecordsListContainer").find(".panel, .tabularViewBottom").remove();
	}
}

	/* fetches and displays table contents, using new parameters */
	function displayData(searchTerm) {
		var jxqr = $.Deferred().resolve().promise(); // dummy promise

		// resetting search by providing empty term
		if (settings.searchMode && searchTerm.length == 0) {
			settings.searchMode = false;
			settings.searchTerm = "";
			settings.urlParams.pageNumber = 0;
			resetOrdering();
		}
		// starting search by providing non-empty term
		if (!settings.searchMode && searchTerm.length > 0) {
			settings.urlParams.pageNumber = 0;
			settings.searchMode = true;
			settings.searchTerm = searchTerm;
			resetOrdering();
		}

		var requestData = $.extend(true, {}, settings.urlParams);
		if (settings.searchMode) {
			if (searchTerm.length < 3) {
				RS.unblockPage($(tableElement));
				apprise("Please enter at least three characters.");
				return;
			} else {
				requestData.allFields = searchTerm;
			}
		} else {
			$("#searchModePanel").hide();
		}
		const unpublishPublicLinks = $("#containsPublicLinks").length > 0;
		const publicRecordsPage = $("#publicRecordsPage").length > 0;
		const url = (unpublishPublicLinks?"/record/share/ajax/publiclinks/manage":
			publicRecordsPage?"/public/publishedView/publishedDocuments/sort" :settings.url) + "?" + $.param(requestData);

		if (RS.webResultCache.get(url) != undefined) {
			insertData(RS.webResultCache.get(url));
			RS.unblockPage($(tableElement));
		} else {
		  	jxqr = $.get(url, function (data) {
				insertAndCache(url, data, 1000 * 30 );
			}).always(function(){
				RS.unblockPage($(tableElement));
			}).fail(function() {
				RS.ajaxFailed("Getting shared documents", false, jxqr);
			});
		}
		return jxqr;
	}

	function insertAndCache (url, data, expiryMS) {
		RS.webResultCache.put(url, data, expiryMS );
		insertData(data);
	}

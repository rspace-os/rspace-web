var settings = {
	url : "/directory/ajax/",
	mode : "users",
	list : "userlist",
	urlParams : {
		pageNumber : 0,
		//resultsPerPage : 10, default will come from prefs now (Also 10).
		sortOrder : "ASC",
		orderBy : "lastName",
		onlyEnabled : true
	},
	searchMode : false,
	searchTerm : "",
	orderByActive : false,
	orderByParameters : {
		"users" : {
			"defaultOrderByParam" : "lastName",
			"allOrderByParams" : {
				"lastName" : "orderByName",
				"username" : "orderByUserName",
				"email" : "orderByEmail"
			}
		},
		"groups" : {
			"defaultOrderByParam" : "displayName",
			"allOrderByParams" : {
				"displayName" : "orderByName",
				"owner.lastName" : "orderByPi"
			}
		},
		"communities" : {
			"defaultOrderByParam" : "displayName",
			"allOrderByParams" : {
				"displayName" : "orderByName"
			}
		},
		"projectgroups": {
			"defaultOrderByParam": "displayName",
			"allOrderByParams" : {
				"displayName" : "orderByName"
			}
		},
	}
};
var tableElement = ".mainTable tbody";

$(document).ready(function() {
	var noOfRows = parseInt($("#noOfRows").val());
	if (!isCloud && noOfRows <= 0) {
		$("#searchModePanel #message").text("There are no users to view.");
		$("#searchModePanel").addClass("searchError").slideDown(fadeTime).find("#resetSearch").hide();
		$("#directoryContainer").find(".panel, .tabularViewBottom").hide();
	}

	RS.emulateKeyboardClick('.publicListButton');
	$(document).on("click", ".publicListButton", function(e) {
		e.preventDefault();
		$("#searchModePanel").hide();
		var title = $(this).text().trim();
		$(".tabularViewTop .title").text(title);

		if ($(this).attr("id") == "groupListButton") {
			settings.list = "grouplist";
			settings.mode = "groups";
		} else if ($(this).attr("id") == "communityListButton") {
			settings.list = "communitylist";
			settings.mode = "communities";
		} else if ($(this).attr("id") == "projectGroupListButton") {
			settings.list = "projectgrouplist";
			settings.mode = "projectgroups";
		} else {
			settings.list = "userlist";
			settings.mode = "users";
		}
		RS.blockPage("Loading list of " + settings.mode + "...", false, $(tableElement));

		$("#searchDirectoryListInput").val("");
		settings.searchMode = false;
		settings.searchTerm = "";
		settings.urlParams.pageNumber = 0;
		resetOrdering();
		
		if (settings.list == "userlist") {
			settings.urlParams.onlyEnabled = true;
		} else {
			delete settings.urlParams.onlyEnabled;
		}
		displayData(settings.searchTerm);
	});

  // Changes the number of records per page
  $(document).on('click', '#applyNumberRecords', function(e){
		e.preventDefault();
		RS.blockPage("Changing the number of records per page...", false, $(tableElement));
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

	$(document).on("click", "#resetSearch", function(e) {
		if (!settings.searchMode) return;
		document.dispatchEvent(new Event('reset-search-input'));
		RS.blockPage("Abandoning search...", false, $(tableElement));
		displayData("");
	});

	$(document).on("click", ".orderBy", function(e) {
		e.preventDefault();
		RS.blockPage("Reordering " + settings.mode + "...", false, $(tableElement));
		var params = RS.getJsonParamsFromUrl($(this).attr("href"));
		settings.urlParams.pageNumber = 0;
		settings.urlParams.orderBy = params.orderBy;
		settings.urlParams.sortOrder = params.sortOrder;
		settings.orderByActive = true;
		displayData(settings.searchTerm);
	});

	var paginationEventHandler = function(source, e) {
		RS.blockPage("Loading the chosen page...", false, $(tableElement));
		var url = createURL(source.attr('id').split("_").slice(1).join("_"));
		var params = RS.getJsonParamsFromUrl(url);
		settings.urlParams.pageNumber = params.pageNumber;
		displayData(settings.searchTerm);
	};
	$('body').off('click', '.page_link');
	RS.setupPagination(paginationEventHandler);
});

function handleSearchDirectoryQuery(){
	var searchTerm = $("#searchDirectoryListInput").val().trim();
	if (!settings.searchMode && searchTerm == "") return;

	RS.blockPage("Searching...", false, $(tableElement));
	
	searchTerm = searchTerm.replace(/,\s*$/,"");
	// page number reset every time new search is made (even with empty term)
	settings.urlParams.pageNumber = 0;
	settings.searchTerm = searchTerm;
	resetOrdering();
	displayData(searchTerm);		
};

function resetOrdering() {
	settings.orderByActive = false;
	settings.urlParams.sortOrder = "ASC";
	settings.urlParams.orderBy = settings.orderByParameters[settings.mode].defaultOrderByParam;
}

function displayData(searchTerm) {
	// search term too short (and non-empty)
	if (searchTerm.length > 0 && searchTerm.length < RS.minSearchTermLength) {
		RS.unblockPage($(tableElement));
		apprise("Please enter at least three characters.");
		return;
	}

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
	if (settings.mode == "users") requestData.pageReload = true;
	if (settings.searchMode) {
		if (settings.mode == "users") {
			requestData.allFields = searchTerm;
		} else {
			requestData.displayName = searchTerm;
		}
	}
	var url = settings.url + settings.list + "?" + $.param(requestData);
	delete requestData;

	if (RS.webResultCache.get(url) != undefined) {
		insertData(RS.webResultCache.get(url));
		RS.unblockPage($(tableElement));
	} else {
		var jxqr = $.get(url, function(data) {
			insertAndCache(url, data, 1000*30);
			if ($(".user_autocompletable").length){
				$("#searchDirectoryListInput").attr("placeholder", "Type a user's name, email or username");
			} else {
				$("#searchDirectoryListInput").attr("placeholder", "Search...");
			}
		}).always(function() {
			RS.unblockPage($(tableElement));
		}).fail(function() {
			RS.ajaxFailed("Getting list of " + settings.mode, false, jxqr);
		});
	}
}

function insertAndCache (url, data, expiryMS) {
	RS.webResultCache.put(url, data, expiryMS );
	insertData(data);
}

/*
 * Inject data into table, show appropriate message when 
 * no data (no records or empty search results) 
 */
function insertData(html) {
	var noOfRows = $("<div/>").html(html).find("#noOfRows").val();

	if (noOfRows > 0) {
		$("#searchModePanel").removeClass("searchError").addClass("searchSuccess");
		if (settings.searchMode) {
			var message = "Showing " + settings.mode + " for search term '" + settings.searchTerm + "'.";
			$("#searchModePanel #message").text(message);
			$("#resetSearch").show();
			$("#searchModePanel").slideDown(fadeTime);
		} else {
			$("#searchModePanel").slideUp(fadeTime);
		}
		$("#directoryContainer").find(".panel, .tabularViewBottom").remove();
		$("#directoryContainer").append(html);

		// RSPAC-1212 Blocking: Setting the height to fit the height of the previously displayed content and the blocking message
		if ($(tableElement).height() < parseInt(temporaryHeightOfBlockedElement)) {
			$(tableElement).css({height : temporaryHeightOfBlockedElement});			
		}
		
		if (settings.orderByActive) {
			var id = settings.orderByParameters[settings.mode].allOrderByParams[settings.urlParams.orderBy];
			var href = $("#" + id).attr("href");
			var sortOrderInLink = settings.urlParams.sortOrder == "ASC" ? "DESC" : "ASC";
			_toggleSortOrder(href, sortOrderInLink, id);
		}
	} else {
		// Show cloud message if viewing whole list of users. 
		// Triggered on empty search, on initial page load and on clicking 'Users' at the top
		if (isCloud && settings.mode == "users" && !settings.searchMode) {
			var isMessageShown = $("<div/>").html(html).find(".directoryMsg.searchMessage").length > 0;
			if (isMessageShown) {
				$("#searchModePanel").slideUp(fadeTime);
				$("#directoryContainer").find(".panel, .tabularViewBottom").remove();
				$("#directoryContainer").append(html);					
			}
		} else { // handle no results in the standard way
			$("#searchModePanel").removeClass("searchSuccess").addClass("searchError");
			
			if (settings.searchMode) {
				$("#searchModePanel #message").text("Your search for '" + settings.searchTerm + 
					"' returned no results. Please search again or");
				$("#resetSearch").show();
				$("#searchModePanel").slideDown(fadeTime);
			} else {
				$("#searchModePanel #message").text("There are no " + settings.mode + " to view.");
				$("#resetSearch").hide();
				$("#searchModePanel").slideDown(fadeTime);
			}				
			$("#directoryContainer").find(".panel, .tabularViewBottom").remove();
		}
	}
}

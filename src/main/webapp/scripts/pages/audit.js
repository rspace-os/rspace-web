$(function() {
	$( "#datepickerFrom").datepicker({
		dateFormat:"yy-mm-dd"});
});

$(function() {
	$( "#datepickerTo").datepicker({
		dateFormat:"yy-mm-dd"});
});

// page counter
var nextPage = 1 ;
// base URL
var url = "/audit/query";
var orderBy='date'; // one of 'date' ,'action', 'user name'
var order = 'DESC'; // one of 'ASC' or 'DESC'
var classToToggle='date';
var noOfPages;
// for download
var previousOrderBy="date";
var previousOrder="DESC";
/**
 *Resets ordering of results to defaults 
 */
function resetSort(){
	orderBy='date';
	order = 'DESC';
}

$(document).ready(function() {
	init();
   
    $(document).on("click", ".addRow", function (){
    	// create the new element via clone()
    	var id = this.id;
    	var elementName = ('.' + id + 'Row');
    	var clickedLink$=$(this);
	    $(this).addClass("deLink");
	    // var newElem = $(elementName).last().clone();
	    // insert the new element after the last row in the set
    	$(elementName).last().appendTo(clickedLink$.closest(".auditIt"));
    });
    
    $(document).on("click", ".remover", function (){
    	if( $(this).attr('name') == 'x1') {
    	var theName = $(this).parent().find('input').attr('name');
    	}else if( $(this).attr('name') == 'x2') {
    		var theName = "dates";
    	}
    	var elementID = ('#' + theName);
    	$(elementID).removeClass("deLink");
    	$(this).closest(".auditRow").appendTo('#storedRowSet');
    });
    $(document).on('click', '.downloadAudit', function(e) {
    	RS.blockPage("Querying audit table..");
    	e.preventDefault();
        resetSort();
		if ($(this).hasClass('action')) {
			orderBy = 'action';
		} else if ($(this).hasClass('date')) {
			orderBy = 'date';
		} else if ($(this).hasClass('username')) {
			orderBy = 'username';
		}
    	if($(this).hasClass('asc')){
   		   order = 'ASC';
    	} else {
    	   order = 'DESC';
    	}
    	var requestData = doSerializeForm(true);
    	var requestUrl = "/audit/download" +"?";
    	for (var i =0; i<requestData.length; i++) {
    		requestUrl = requestUrl + requestData[i]["name"]+"="+ requestData[i]["value"]+"&"
    	}
    	requestUrl=requestUrl.substring(0, requestUrl.length - 1);
    	console.log(requestUrl);
    	window.open(requestUrl,"_blank");
    	RS.unblockPage();
    });
    /*
     *Form submission function 
     */
    $(document).on('click', '.getAudit', function(e) {
        RS.blockPage("Querying audit table..");
        e.preventDefault();
        resetSort();
		if ($(this).hasClass('action')) {
			orderBy = 'action';
		} else if ($(this).hasClass('date')) {
			orderBy = 'date';
		} else if ($(this).hasClass('username')) {
			orderBy = 'username';
		}
    	if($(this).hasClass('asc')){
   		   order = 'ASC';
    	} else {
    	   order = 'DESC';
    	}
    	 previousOrderBy=orderBy;
    	 previousOrder=order;

        nextPage = 0 ; // reset 'more' link
        var requestData = serializeForm();
        var jxqr = $.get(url, requestData, function(xhr) {
            RS.unblockPage();
            if(isInputValidationError(xhr)){
            	return;
            }
         // this is somewhat duplicated in pagination handler
            var templateHTML = $('#auditResultsTableTemplate').html();
            // perform any modifications required for display
            _convertAuditTrailResults(xhr);
     
            var resultsHTML = Mustache.render(templateHTML, xhr.data);
            var $insertedResults = $('#theData').html(resultsHTML);
            
            noOfPages = xhr.data.totalPages;
            var pagination = RS.generateBootstrapPagination(noOfPages, 1, "page_link_1");
            // Generate new pagination panel every time. Remove the old one. Needed when filters are applied.
            if ($(".pagination.new").size() > 0) { // old pagination exists, need to replace it
                $(".pagination.new").parent().empty().html(pagination);
            } else { // create pagination from scratch
                pagination = $("<div>").addClass("bootstrap-custom-flat").html(pagination);
                $insertedResults.after(pagination);
            }

            // set IDs
            $('#theData').find('table').attr("id", "renderedTable");
            //calculateIfMore(xhr.data);
            toggleOrder(xhr.data);
        });
        jxqr.fail(function(){
            RS.unblockPage();
            RS.ajaxFailed("Querying audit trail",false,jxqr);
        });
    });
    /*
    $(document).on('click', '.moreResults', function(e) {
        e.preventDefault();
        var requestData = serializeForm();
        // manipulate pagination to reload next pages
        nextPage++;
        requestData.push({name:"pageNumber", "value":nextPage});
        var requestUrl = url + "?" + $.param(requestData);
        console.log(requestUrl);

        if (RS.webResultCache.get(requestUrl) != undefined) {
            $('#directoryContainer').html(RS.webResultCache.get(requestUrl));
        } else {
            var jxqr = $.get(requestUrl, function(xhr) {
                if(isInputValidationError(xhr)){
                    return;
                }
                var templateHTML = $('#auditMoreResultsTemplate').html();
                var resultsHTML = Mustache.render(templateHTML, xhr.data);
                // console.log(resultsHTML);
                $('#renderedTable').find('tbody').html(resultsHTML);
                // then check again if we should display this link or not
                calculateIfMore(xhr.data);

                // $('#directoryContainer').html(data);
                RS.webResultCache.put(requestUrl, xhr.data, 30 * 1000 );//30 seconds
            });
            jxqr.fail(function() {
                RS.ajaxFailed("Getting user activity information", false, jxqr);
            });
        }

        // var jxqr = $.get(requestUrl, function(xhr) {
        //     if(isInputValidationError(xhr)){
        //         return;
        //     }
        //     // we just generate some more rows, and insert them in the existing table
        //     var templateHTML = $('#auditMoreResultsTemplate').html();
        //     var resultsHTML = Mustache.render(templateHTML, xhr.data);
        //     $('#renderedTable').find('tbody').append( resultsHTML);
        //     // then check again if we should display this link or not
        //     calculateIfMore(xhr.data);
        // });

        jxqr.fail(function(){
        	RS.ajaxFailed("Querying audit trail", false, jxqr);
        });
    });
    */

    var paginationEventHandler = function(source, e) {
        var pageNumber = source.data("pagenumber") - 1;
        var requestData = serializeForm();
        requestData.push({name: "pageNumber", "value": pageNumber});
        var requestUrl = url + "?" + $.param(requestData);

        if (RS.webResultCache.get(requestUrl) != undefined) {
            $('#renderedTable').find('tbody tr').first().nextUntil(':not(tr)').remove();
            $('#renderedTable').find('tbody').append(RS.webResultCache.get(requestUrl));
        } else {
            var jxqr = $.get(requestUrl, function(xhr) {
                if(isInputValidationError(xhr)){
                    return;
                }
                var templateHTML = $('#auditMoreResultsTemplate').html();
                // perform any modifications required for display
                // this is duplicated in main form submission handler
                _convertAuditTrailResults(xhr);
                var resultsHTML = Mustache.render(templateHTML, xhr.data);
                $('#renderedTable').find('tbody tr').first().nextUntil(':not(tr)').remove();
                $('#renderedTable').find('tbody').append(resultsHTML);
                RS.webResultCache.put(requestUrl, resultsHTML, 30 * 1000 );//30 seconds
            });
            jxqr.fail(function() {
                RS.ajaxFailed("Getting user activity information", false, jxqr);
            });
        }
        $(".pagination.new").parent().animate({opacity: 0}, fadeTime, function(){
            $(this).html(RS.generateBootstrapPagination(noOfPages, pageNumber+1, "page_link_1")).animate({opacity: 1}, fadeTime);
        });
        RS.addPaginationTooltips(".pagination.new");
    };
    RS.setupPagination(paginationEventHandler, ".page_link_1");
}); // End document ready

function _convertAuditTrailResults (xhr){
	 for (var i = 0; i< xhr.data.results.length; i++) {
     	//update date format
     	var result = xhr.data.results[i];
     	result.timestamp = new Date(result.timestamp).toISOString();
     	// show export description if possible:
     	if( result.data.action ==='EXPORT'
     		&& result.data.data && result.data.data.data) {
     		var resultObj = result.data.data.data;
     		var desc = "";
     		if(resultObj.exported) { // is an archive operation
     		    desc = resultObj.exported.length  + " item(s) exported";
     		  if(resultObj.configuration) {
     			desc = desc + " as " +resultObj.configuration.exportScope + " to " + resultObj.configuration.archiveType;
     		  }
     		} else if (resultObj.scope) {
     			desc = desc + "Items exported as " + resultObj.scope + " to " + resultObj.format;
     		}
     		result.event.description = desc; 
     	}
     	if( result.data.action ==='MOVE') {
     		var resultObj = result.data.data.data;
     		var desc = result.data.description;
     		if (!desc) {
     		    desc = `from ${resultObj.from.data.name} (${resultObj.from.data.id}) to   ${resultObj.to.data.name} (${resultObj.to.data.id})`;
     		}
     		result.event.description = desc; 
     	}
     }
}

function _pad(n){return n<10 ? '0'+n : n}

/**
 * Initialises autocomplete boxes
 */
function init() {
	
	$.get("/groups/ajax/admin/listAll", function (data) {
		setUpAutoCompleteGroupBox('#groupEntry', data.data);
    });
	setUpAutoCompleteUsernameBox('#userEntry', '/audit/queryableUsers');
	$.get("/system/ajax/getAllCommunities",function (data) {
 		setUpAutoCompleteCommunityBox('#communityEntry', data.data);
 	});
	$.get('/audit/actions', function (xhr) {
		var html = Mustache.render($('#auditactionTemplate').html(), xhr);
		$(".actionsRow").append(html);
	})
	var domainhtml = Mustache.render($('#auditdomainTemplate').html());
	$(".domainsRow").append(domainhtml);
};

 /**
  * Shows or hides the 'more ' link depending on number of results
  * @param data  - returned from ajax search query
  */
//function calculateIfMore(data) {
//	if (data.totalHits > ((data.pageNumber + 1) * data.numberRecords)) {
//		$('.moreResults').show();
//	} else {
//		$('.moreResults').hide();
//	}
//}
function serializeForm () {
	return doSerializeForm(false);
}

function _isNotDomain (obj) {
	return obj["name"] !== "domains"
}
/**
 * Serialized form to JSON array and adds additional data / configuration
 */
function doSerializeForm(forDownload) {
	// 'INV' and 'ELN' represent a collection of domains.
	// we can send multiple name-value pairs where value is a single domain, or a single name-value pair
	// where the value has a comma-separated list of domains, but we can't do both as Spring treats the comma-separated values
	// as a single value.
	// So, here we coalesce domain names into a single name-value pair to submit.
	var elnDomains = ["RECORD","NOTEBOOK","FOLDER","WORKSPACE","USER","UNKNOWN","AUDIT"]
	var invDomains = ["INV_SAMPLE","INV_SUBSAMPLE","INV_CONTAINER"]
	var domainsToSubmit = []
	var requestData = $('form').serializeArray();
	for (i=0; i< requestData.length; i++) {
		if (requestData[i]["name"] == "domains") {
			if (requestData[i]["value"] == "ELN") {
					domainsToSubmit = domainsToSubmit.concat(elnDomains)
			} else if (requestData[i]["value"] == "INV") {
				domainsToSubmit = domainsToSubmit.concat(invDomains)
			}	
		}
	}
	// replace >=1 'domains' value with single 'domains' value
	requestData = requestData.filter(_isNotDomain)
	if(domainsToSubmit.length > 0) {
		requestData.push({"name":"domains", "value":domainsToSubmit.join(",") })
	}
	
    // order by date descending until ordering is implemented
    if(forDownload) {
    	requestData.push({name:"sortOrder", value:previousOrder});
    	requestData.push({name:"orderBy", value:previousOrderBy});
    } else {
    	 requestData.push({name:"sortOrder", value:order});
    	 requestData.push({name:"orderBy", value:orderBy});
    }
   
    requestData.push({name:"resultsPerPage", value:50}); //RSPAC-1283
    return requestData;
}

/**
 * Swaps 'asc' and 'desc' classes in the relevant headr element, these need to be added again as tableheaders are reloaded
 * element - any jquery element
 */
function toggleOrder(data) {
	// reset current arrows
	$('.tableSortLink').removeClass('asc').removeClass('desc');
	// apply new order
	var header$ = $("."+data.paginationCriteria.orderBy);
	var order = data.paginationCriteria.sortOrder;
	if (order == 'ASC') {
		header$.addClass('desc');
	} else {
		header$.addClass('asc');
	}
}

/**
 * Examines an AjaxReponseObject for an errorMsg. If there is one, it displays it, and returns true
 * - param xhr An AjaxReponseObject in JSON format
 * Returns true if there was an error, false otherwise
 */
function isInputValidationError (result) {
	if(result.errorMsg !== null){
 	    apprise(getValidationErrorString(result.errorMsg));
 	    return true;
 	} 
	return false;
}

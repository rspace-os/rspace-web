
$(document).ready(function (){
	$('body').on('click','.restore',function(e){
		e.preventDefault();
		var revision =$(this).next('input').val();
		var data = {
				recordId:recordId,
				revision:revision
		};
		var url = createURL('/workspace/revisionHistory/restore');
		var jxqr = $.post(url,data, function(rc){
			if(rc.data  == 'EDIT_MODE') {
				 $().toastmessage('showToast', {
		         text     : "Restored",
		         sticky   : false,
		         position : 'top-right',
		         type     : 'success',
		         close    : function () {
		         window.location.href=createURL('/workspace/editor/structuredDocument/'+recordId);
		         }
		       });
			} else if (rc.errorMsg != null){ 
				apprise(getValidationErrorString (rc.errorMsg));
			}  			
		});
		jxqr.fail (function (){
			RS.ajaxFailed("Restore", false, jxqr);
		});
		
	});
	var paginationEventHandler = function(source, e){
    	var url= source.attr('id').split("_")[1];
    	if (RS.webResultCache.get(url) != undefined) {
			$('#revisionListContainer').html(RS.webResultCache.get(url));
			init();
    	} else {
    		$.get(createURL(url), function (data) {
				$('#revisionListContainer').html(data);
				RS.webResultCache.put(url, data, 1000 * 60 ); /* 1 minute expiry*/
				init();
			});
		}
    };
    RS.setupPagination(paginationEventHandler);

	$('body').on('keypress','#search_modifiedBy',function(e) {
		returnKeyPress(e);
		});
	$('body').on('click','#search_dateButton',function(e) {
		doSearch ();
		});
	$('body').on('click', '#clearSearch',function (){
		 $('.search_term').val("");
	});
	$('body').on('click', '#revisionSearch',function (){
		$(".spaceOver").toggle();
});
	init();
});

/**
 * Sets up jquery functions on elements just added by Ajax
 */
function init (){
	$('#search_dateRange').multiDatesPicker(	{
				dateFormat: "yy-mm-dd",
				maxPicks: 2
			});
	$('#search_dateButton').button({
      icons: {
          primary: "ui-icon-search",
          
        },
        text: false
	});
	$('.delta-msg').each(function (i, element){
		var text = element.innerHTML;
		var re = /(\{.+\})/;
		if(text.match(re)) {
			//replace json string from db with html
			var match = re.exec(text);
			var json = $.parseJSON(match[0]);
			var template = _getMustacheTemplate();
			var html = Mustache.render(template, json);
			element.innerHTML=html;
		}
		
	});
}

function _getMustacheTemplate(){
	return "<span class='templateCreateRevision'> {{deltaType}}  <a href='/globalId/{{globalId}}'>{{globalId}}</a> owned by <a href='/userform?userId={{ownerId}}'>{{ownerName}} </span>";
}

function returnKeyPress(e){
	if (e.which === $.ui.keyCode.ENTER) doSearch(e);
}
/**
 *	Submits search and refreshes the table and pagination if need be
 */
function doSearch (){
	var modifiedBy=$('#search_modifiedBy').val();
	var modifiedDateRange=$('#search_dateRange').val();
	var selectedFields = $('#search_modifiedFields').val();
	var url = "/workspace/revisionHistory/ajax/list/" + recordId+"?";
	var isAndNeeded= false;
	if(modifiedBy.length != 0) {
		  url = url +"modifiedBy="+modifiedBy;
		  isAndNeeded=true;
	}
	if(modifiedDateRange.length != 0) {
		if(isAndNeeded) {
			url = url +"&";
		}
	  url = url +"dateRange="+modifiedDateRange;
		isAndNeeded=true;
	  
	}
	if(selectedFields!= null && selectedFields.length > 0) {
		if(isAndNeeded) {
			url = url +"&";
		}
	  url = url +"selectedFields="+selectedFields;
		isAndNeeded=true;
	}
	 $.get(url, function (data) {
		 $('#revisionListContainer').html(data);
		 $('#search_modifiedBy').val(modifiedBy);//update
		 init();
		 $('#search_dateRange').val(modifiedDateRange);//this has to be after init(), as date picker init clears the date field
	 });
	
}
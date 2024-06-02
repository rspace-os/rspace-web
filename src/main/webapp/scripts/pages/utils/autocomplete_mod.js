
/**
 * For use in Community environment to search for users by email, username or last name
 *  once an initial number of numChars characters has been typed.
 * @param numChars
 * @param inputSelector
 */
function applyAutocomplete(inputSelector) {
	 $(inputSelector).autocomplete({
		minLength: 3,
		open: function() { 
			$('.ui-autocomplete').width(275);
		},
		source: autocompletePublicUserInfoSource,
		focus: function() {
			// prevent value inserted on focus
			return false;
		},
		select: function( event, ui ) {
			var terms = split( this.value );
			// remove the current input
			terms.pop();
			// add the selected item
			terms.push( ui.item.value );
			// add placeholder to get the comma-and-space at the end
			if(this.tagName == "TEXTAREA") {
				terms.push("");
				this.value = terms.join(",");
			} else {
				this.value = terms;
			}
			return false;
		},
	});
}

// store the autocomplete result list 
var autocompletePublicUserInfoSrcArray = []; 

function autocompletePublicUserInfoSource(request, response) {
	var term = extractLast( request.term ) ;
	if (term.trim().length < 3) {
		response([]);
		return;
	}
	var jxqr = $.get("/cloud/ajax/searchPublicUserInfoList", 
	    { term: term }, 
	    function (data) {
        	autocompletePublicUserInfoSrcArray = [];
        	if(data.error != null) {
        		apprise (getValidationErrorString(data.error));
        	} else {
	        	for (var i =0; i < data.data.length;i++){
	        		autocompletePublicUserInfoSrcArray.push( { 
	        			label : data.data[i].fullName +" ("+ data.data[i].username +") <" + data.data[i].email+">",
	        			value : data.data[i].email 
	        		});
	        	}
	            response(autocompletePublicUserInfoSrcArray);
        	}
        });
}

/**
 * For use in Community environment to search for organisations once an 
 * 	initial number of numChars characters has been typed.
 * @param numChars
 * @param inputSelector
 */
function applyAffiliationAutocomplete(numChars, inputSelector){
	$(inputSelector).autocomplete({
 		minLength: numChars,
		open: function() { 
			$('.ui-autocomplete').width(270);
		},
 		source: function( request, response ) {
 			var term = extractLast( request.term ) ;
 			if (term.trim().length < numChars) {
 				response([]);
 				return;
 			}
 			var jxqr = $.get("/organisation/ajax/approved", {
 				term: term
 			}, function (data) {
 	        	var srcArry = [];
 	        	if(data.error != null) {
 	        		apprise (getValidationErrorString(data.error));
 	        	} else {
 	        		for (var i =0; i < data.data.length;i++){
	 	        		srcArry.push( { 
	 	        			label : data.data[i].title,
	 	        			value : data.data[i].title 
	 	        		});
	 	        	}
	 	            response(srcArry);
 	        	}
 	        });			
 		},
 		focus: function() {
 			// prevent value inserted on focus
 			return false;
 		},
 		select: function( event, ui ) {
 			var terms = split( this.value );
 			// remove the current input
 			terms.pop();
 			// add the selected item
 			terms.push( ui.item.value );
 			this.value = terms;
 			return false;
 		},
 	});
}

function applyGroupAutocomplete(numChars, inputSelector) {
	 $(inputSelector).autocomplete({
		minLength: numChars,
		open: function() { 
			$('.ui-autocomplete').width(275);
		},
		source: function( request, response ) {
			var term = extractLast( request.term ) ;
			if (term.trim().length < 3) {
				response([]);
				return;
			}
			var jxqr = $.get("/cloud/ajax/searchGroupList", {
				term: term
			}, function (data) {
	        	var srcArry = [];
	        	if(data.error != null) {
	        		apprise (getValidationErrorString(data.error));
	        	} else {
	        	for (var i =0; i < data.data.length;i++){
	        		// adapts results to jquery-ui-autocomplete data structure
	        		srcArry.push( { 
	        			label : data.data[i].displayName + " (" + data.data[i].piFullname + ") ["+ data.data[i].piAffiliation + "]",		
	        			value : data.data[i].displayName,
	        			id : data.data[i].id,
	        		});
	        	}
	            response(srcArry);
	        	}
	        });			
		},
		focus: function() {
			// prevent value inserted on focus
			return false;
		},
		select: function( event, ui ) {
			var terms = split( this.value );
			// remove the current input
			terms.pop();
			// add the selected item
			terms.push( ui.item.value );
			// add placeholder to get the comma-and-space at the end
			this.value = terms;
			this.dataset.groupid = ui.item.id;
			return false;
		},
	});
}

/**
 * Sets  up an autocomplete input text-box  or text area.
 * Arguments
 *  inputSelector - an id or class indentifier for the text/input box - e.g, '#inputId'
 *  sourceUrl - server endpoint that returns the users (UserBasicInfo objects). 
 *  			endpoint has to handle 'term' parameter which contains user's input
 *  dataGetter - optional, function returning 'data' object that'll be passed with 
 *  			ajax get request to sourceUrl
 */
function setUpAutoCompleteUsernameBox(inputSelector, sourceUrl, dataGetter) {
	
	$(inputSelector).autocomplete({
		minLength: 3,
		open: function () { 
			$('.ui-autocomplete').width(275);
		},
		source: function (request, response) {
			var term = split( request.term ).pop().trim();
			if (term.length < 3) {
				response([]);
				return;
			}
			
	    	var data = {};
	    	if (dataGetter != undefined) {
	    		data = dataGetter();
	    	}
	    	data.term = term;
	    	
			$.get(createURL(sourceUrl), data, function (data) {
	        	var srcArray = [];
	        	if(data.error != null) {
	        		console.log(getValidationErrorString(data.error));
	        	} else {
		        	for (var i =0; i < data.data.length;i++){
		        		srcArray.push( { 
		        			label : data.data[i].fullName + "<" + data.data[i].email + ">",
		        			value : data.data[i].username + "<" + data.data[i].fullName + ">"
		        		});
		        	}
		            response(srcArray);
	        	}
			});
		},
		focus: function() {
			// prevent value inserted on focus
			return false;
		},
		select: function( event, ui ) {
			var terms = split( this.value );
			// remove the current input
			terms.pop();
			// add the selected item
			terms.push( ui.item.value );
			// add placeholder to get the comma-and-space at the end
			if(this.tagName == "TEXTAREA") {
				terms.push("");
				this.value = terms.join(",");
			} else {
				this.value = terms;
			}
			return false;
		},
	});
}

function setUpAutoCompleteWorkspaceSearchByOwnerUsername(inputSelector, srcUserList) {
	var srcArry = [];
	for (var i =0; i < srcUserList.length;i++){
		// adapts results to jquery-i-autocomplete data structure
		srcArry.push({
			// what is displayed in dropdown list and is searched over
			label:srcUserList[i].fullName +" ("+ srcUserList[i].username +") "+" <" + srcUserList[i].email+">",
			// what is used as value in input fiels
			value:srcUserList[i].username,
			}
		);
	}
	_bindSourceArray({selector:inputSelector, src:srcArry, addSeparator:false});	
}

/**
 * Sets  up an autocomplete input text-box  or text area.
 * Arguments
 *  inputSelector - an id or class indentifier for the text/input box - e.g, '#inputId'
 *  srcGroupList   - an array of {id: ,displayName: } properties ( this is a JSON
 *      interpretation of a GroupListResult Java object ).
 */
function setUpAutoCompleteGroupBox(inputSelector, srcGroupList) {
	var srcArry = [];
	for (var i =0; i < srcGroupList.length;i++){
		// adapts results to jquery-i-autocomplete data structure
		srcArry.push({
			// what is displayed in dropdown list and is searched over
			label:srcGroupList[i].displayName,
			// what is used as value in input fiels
			value:srcGroupList[i].displayName + "<"+srcGroupList[i].id+">",
			}
		);
	}
	_bindSourceArray({selector:inputSelector, src:srcArry, addSeparator:true});
}

/**
 * Sets  up an autocomplete input text-box  or text area.
 * Arguments
 *  inputSelector - an id or class indentifier for the text/input box - e.g, '#inputId'
 *  srcGroupList   - an array of {id: ,displayName: } properties ( this is a JSON
 *      interpretation of a CommunityListResult Java object ).
 */
function setUpAutoCompleteCommunityBox(inputSelector, srcCommunityList) {
	var srcArry = [];
	for (var i =0; i < srcCommunityList.length;i++){
		// adapts results to jquery-i-autocomplete data structure
		srcArry.push({
			// what is displayed in dropdown list and is searched over
			label:srcCommunityList[i].displayName,
			// what is used as value in input fiels
			value:srcCommunityList[i].displayName + "<"+srcCommunityList[i].id+">",
			}
		);
	}
	_bindSourceArray({selector:inputSelector, src:srcArry, addSeparator:true});
}

function setUpAutoCompleteTagBox(inputSelector, srcTagList) {
	var srcArry = [];
	for (var i =0; i < srcTagList.length;i++){
		// adapts results to jquery-i-autocomplete data structure
		srcArry.push({
			// what is displayed in dropdown list and is searched over
			label:srcTagList[i],
			// what is used as value in input fiels
			value:srcTagList[i],
			}
		);
	}
	_bindSourceArray({selector:inputSelector, src:srcArry, addSeparator:false, minLength:2, maxItems:20});	
}

/** 
 * helper function for autocomplete
 */
function _bindSourceArray(cfg) {
	
	cfg.minLength = cfg.minLength || 3;
	cfg.maxItems = cfg.maxItems || 10;
	$(cfg.selector)
	 .bind( "keydown", function( event ) {
		 if ( event.keyCode === $.ui.keyCode.TAB &&
				 $( this ).data( "ui-autocomplete" ).menu.active ) {
			 event.preventDefault();
			 }
		 }).autocomplete({
			 minLength: cfg.minLength, // min characters b4 displaying
			 open: function() { 
				 $('.ui-autocomplete').width(275);
			 },
    		 source: function( request, response ) {
        		 // delegate back to autocomplete, but extract the last term
    			 var items = $.ui.autocomplete.filter(
        				 cfg.src, extractLast( request.term ) );
    			 if(items.length > cfg.maxItems ){
    				 items = items.slice(0, cfg.maxItems);
    			 }
        		 response( items );
    		 },
    		 focus: function() {
        		 // prevent value inserted on focus
        		 return false;
    		 },
    		 select: function( event, ui ) {
        		 var terms = split( this.value );
        		 // remove the current input
        		 terms.pop();
        		 // add the selected item
        		 terms.push( ui.item.value );
        		 
        		 if(cfg.addSeparator){
	        		 // Add placeholder to get the comma-and-space at the end
	        		 terms.push("");
	        		 this.value = terms.join(", ");
	    		 } else {
	    			 this.value = terms;
	    		 }
        		 return false;
    		 },
		 });
}

/**
 * Helper function for splitting  a value by comma
 */
function split( val ) {
	return val.split( /,\s*/ );
}

/**
 * Helper function to get the last term
 */
function extractLast( term ) {
	return split( term ).pop();
}
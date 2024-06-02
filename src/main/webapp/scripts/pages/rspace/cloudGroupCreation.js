
$(document).ready(function() {

	$(document).on('change', '.options', function(e) {
		var fadeTime = 300;
		if ($(this).val() === "true") {
			$('#nominationOption').fadeIn( fadeTime );
			$('.nominationOptionError').fadeIn( fadeTime );
			$('#principalEmail').attr('required', true);
		} else {
			$('#nominationOption').fadeOut( fadeTime );
			$('.nominationOptionError').fadeOut( fadeTime );
			$('#principalEmail').attr('required', false);
		}
	});

	var autocompleteConfig = {
		minLength: 3,
		source: autocompletePublicUserInfoSource,
	};

	$("#principalEmail").tagit({
		fieldName: "principalEmail",
	    placeholderText: "Type and find PI or enter new email",
	    tagLimit: 1,
	    beforeTagAdded: function(event, ui) {
	        if(!validateEmail(ui.tagLabel)) {
	        	RS.confirm("Please check email syntax", "error", RS.defaultToastDisplayTime);
	        	return false;
	        }
	    },
	    afterTagAdded: function(event, ui) {
	    	$("#principalEmail li.tagit-new").hide();
	    	$('#principalEmail .tagit-choice').attr('tabindex', 0);
	    },
	    afterTagRemoved: function(event, ui) {
	    	$("#principalEmail li.tagit-new").show();
	    },
	    onTagLimitExceeded: function(event, ui) {
        	RS.confirm("You have already added an email.", "notice", RS.defaultToastDisplayTime);
        	return false;
	    },
	    autocomplete: autocompleteConfig,
	});
	RS.addOnKeyboardClickHandlerToDocument('#principalEmail .tagit-choice', function(e){
		$(this).find('.tagit-close').click();
	});

	$("#existingUsers").tagit({
		fieldName: "emails",
	    placeholderText: "Type and find users by name, username or email",
	    beforeTagAdded: function(event, ui) {
	        var isAutocompleteList = false;
	        $.each(autocompletePublicUserInfoSrcArray, function(i,obj) {
	        	if (obj.value === ui.tagLabel) { 
	        		isAutocompleteList = true; 
	        	}
	        });  
	        
	        if(isAutocompleteList === false){
	        	RS.confirm("Please check email. <br> Select an existing RSpace user email from the autocompleted list.", "notice", 5000);
	        	return false;
	        }
	    },
	    afterTagAdded: function(event, ui) {
	    	$('#existingUsers .tagit-choice').attr('tabindex', 0);
	    },
	    autocomplete: autocompleteConfig,
	});
	RS.addOnKeyboardClickHandlerToDocument('#existingUsers .tagit-choice', function(e){
		$(this).find('.tagit-close').click();
	});
	
	$("#nonExistingUsers").tagit({
		fieldName: "emails",
	    placeholderText: "Type and enter email",
	    beforeTagAdded: function(event, ui) {
	        if(!validateEmail(ui.tagLabel)) {
	        	RS.confirm("Please check email syntax", "error", RS.defaultToastDisplayTime);
	        	return false;
	        }
	    },
	});

	$(document).on('submit', '#createCloudGroup', function(e) {
		return formValidation();
	});
}); 

function validateEmail(email){
	var re = /^([\w-]+(?:\.[\w-]+)*)@((?:[\w-]+\.)*\w[\w-]{0,66})\.([a-z]{2,6}(?:\.[a-z]{2})?)$/i;
    return re.test(email);
}

function formValidation(){
	
	var nomination = $(".options:checked").val();
	var principalEmail = $("#principalEmail").tagit("assignedTags");
	
	if(nomination === "true" && !principalEmail.length) {
		RS.confirm("No PI email found to send an invitation.", "notice", RS.defaultToastDisplayTime);
		return false;
	}
}
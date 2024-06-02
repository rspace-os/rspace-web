function initSignDialog(dialogTitle) {
    $(document).ready(function () {
        RS.switchToBootstrapButton();
    	$('#signDocumentDialog').dialog({
    		title: dialogTitle,
    		resizable: true, 
    		autoOpen: false,
    		height: 680,
    		width: 400,
    		modal: true,
    		open: function ( e ) {
    			$('#confirmationSection').hide();
    			$('.witnesses').removeClass('ui-helper-hidden-accessible');
    			$('.statement').removeClass('ui-helper-hidden-accessible');
    			$('.statement').prop('disabled',false);
    			$('.statement[value=statement1]').prop('checked', true);
    			RS.disableJQueryDialogButtonWithLabel('Proceed');
    			RS.enableJQueryDialogButtonWithLabel('Sign');
    			
    			var rid = $(this).data('recordId');
    			var data = {
    				recordId:rid
    			};
    			
    			$.get(createURL("/workspace/editor/structuredDocument/getPotentialWitnesses"), data, function(result) {
    				var users = result.data;
    				var htmlList = "";
    				$.each(users, function( index, value ) {
    					htmlList += "<label><input class='witnesses' type='checkbox' value="+value.username+"> "+value.fullName +" (" +value.email+") </label>";
    				});
    				$("#witnessesList").append(htmlList);
    			});
            },
            close: function ( e ) {
            	$("#witnessesList").children().remove();
            	$(".passSignInput").val("");
            }, 
    		buttons: 
    		{
    			Cancel: function(){
    				$("#signDocumentDialog").dialog('close');
    			},
    			Sign: function (){
    				if (editable != 'VIEW_MODE'){
    					apprise("This document cannot be signed because it is being edited. Please click 'Save and View', then 'Sign' to initialize the signing process.");
    					// TO-DO: RSPAC-1287 Focus the apprise dialog
    					$("#signDocumentDialog").dialog('close');
    					return false;
    				}
    				
    				$('.userSignInput').prop('disabled',false);
    				$('#confirmationSection').slideDown('slow');
    				$('#confirmationSection .passSignInput').focus();
    				RS.disableJQueryDialogButtonWithLabel('Sign');
    				RS.enableJQueryDialogButtonWithLabel('Proceed');
    			}, 	
    			Proceed: function (){
    
    				var statementInputs = $('.statement');
    				var statement = statementInputs.filter(':checked').val();
    				var witnesses = [];
    				$(".witnesses:checked").each(function() {
    					witnesses.push($(this).val());
    				});
    				if (witnesses.length == 0) {
    					witnesses.push("NoWitnesses");
    				}
    				
    				var password = $(".passSignInput").val();
    				var rid = $(this).data('recordId');
    				var data = {
    						recordId:rid,
    						statement:statement,
    						witnesses:witnesses,
    						password:password
    				};
    				
    				RS.blockPage("Signing...");
    				var jqxhr= $.post(createURL("/workspace/editor/structuredDocument/ajax/proceedSigning"), data, function (result) { 
    					RS.unblockPage();
    					var msgx = result.data, 
    					    errorMsg = result.errorMsg;
    
    					if (errorMsg) {
    						apprise(errorMsg.errorMessages[0]);
    						// TO-DO: RSPAC-1287 Focus the apprise dialog
    						return false;
    					}
    					signatureSetFromJSON(msgx);
    					signatureRecalculateStatus();
    					$("#signDocument").hide();
    					$("#editEntry").hide();
    					RS.checkToolbarDividers(".toolbar-divider");
    					$("#signDocumentDialog").dialog('close');
    					RS.trackEvent('Document Signed');
    					if (witnesses.length > 0 && witnesses[0]!='NoWitnesses') {
    						RS.trackEvent('Document Witness Requested');
    					}
    					editable = "CAN_NEVER_EDIT";
    					isEditable = false;
    					$('.fieldHeaderEditButton').hide();
                        RS.confirm("The document has been signed and locked", "success", 2000, 
                                {}, function() { window.location.reload(); });
    				});
    				jqxhr.fail(function(){
    					RS.unblockPage();
    		            RS.ajaxFailed("Signing process",false,jqxhr);
    		        });
    			}
    		}	
    	});
    	RS.switchToJQueryUIButton();
    });
}

function initWitnessDialog(dialogTitle) {
    $(document).ready(function () {
        RS.switchToBootstrapButton();
    	$('#witnessDocumentDialog').dialog({
    		title: dialogTitle,
    		resizable: true, 
    		autoOpen: false,
    		height: 500,
    		width: 400,
    		modal: true,
    		open: function ( e ) {
    			$('#confirmationWitnessSection').hide();
    			$('.option').removeClass('ui-helper-hidden-accessible');
    			$('.option').prop('disabled',false);
    			$('.option[value=statement1]').prop('checked', true);
    			$('#inputOption2').prop('disabled',false);
    			RS.disableJQueryDialogButtonWithLabel('Proceed');
    			RS.enableJQueryDialogButtonWithLabel('Witness');
            },
            close: function ( e ) {
            	$(".passWitnessInput").val("");
            }, 
    		buttons: 
    		{
    			Cancel: function(){
    				$("#witnessDocumentDialog").dialog('close');
    			},
    			Witness: function (){
    				$('#confirmationWitnessSection').slideDown('slow');
    				$('#confirmationSection .passSignInput').focus();
    				RS.disableJQueryDialogButtonWithLabel('Witness');
    				RS.enableJQueryDialogButtonWithLabel('Proceed');
    			}, 	
    			Proceed: function (){
    
    				var optionInputs = $('.option');
    				var option = optionInputs.filter(':checked').val();
    				var password = $(".passWitnessInput").val();
    				var declineMsg = $("#declineMsgInput").val();
    				var rid = $(this).data('recordId');
    
    				var data = {
    						recordId:rid,
    						option:option,
    						password:password,
    						declineMsg:declineMsg
    				};
    				
    				RS.blockPage("Witnessing...");
    				var jqxhr= $.post(createURL("/workspace/editor/structuredDocument/ajax/proceedWitnessing"), data, function (result) { 
    					RS.unblockPage();
    					var signature = result.data,
    					    errorMsg = result.errorMsg;
    					
    	                if (errorMsg){
    						apprise(errorMsg.errorMessages[0]);
    						// TO-DO: RSPAC-1287 Focus the apprise dialog
    						return false;
    					} 
    					signatureSetFromJSON(signature);
    					signatureRecalculateStatus();
    					RS.trackEvent('Document Witness Completed');
    	                if (option === 'true') {
    						_closeWitnessingDlgAndConfirm("Document witnessed");
    					} else { // witnessing declined
    						_closeWitnessingDlgAndConfirm("Document witnessing declined");
    					}
    				});
    				jqxhr.fail(function(){
    					RS.unblockPage();
    		            RS.ajaxFailed("Witnessing process",false,jqxhr);
    		        });
    			}
    		}	
    	});
    	RS.switchToJQueryUIButton();
    });
}

function _closeWitnessingDlgAndConfirm (msg) {
	$("#witnessDocument").hide();
	RS.checkToolbarDividers(".toolbar-divider");
	$("#witnessDocumentDialog").dialog('close');
	RS.confirm(msg, "success", 3000);
}

/**
 * Ready block
 */
$(document).ready(function() {

	$(document).on("change", ".statement", function(e){
		var statementInputs = $('.statement');
		var statement = statementInputs.filter(':checked').val();
		
		if (statement === "statement1") {
			$('#witnessesSection').slideDown('slow');
			
		} else if(statement === "statement2") {
			$('#witnessesSection').slideUp('slow');
		}
	});

	//witnessing
	$(document).on("change", ".option", function(e){
		var statementInputs = $('.option');
		var statement = statementInputs.filter(':checked').val();
		
		if (statement === "true") {
			$('#declineMsgInput').slideUp('slow');
			$('.witnessConfirmMsg').show();
			$('.witnessDenyMsg').hide();
		} else if(statement === "false") {
			$('#declineMsgInput').slideDown('slow');
			$('.witnessConfirmMsg').hide();
			$('.witnessDenyMsg').show();
		}
	});
});

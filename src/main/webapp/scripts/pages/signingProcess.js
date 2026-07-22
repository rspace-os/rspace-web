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
    			RS.disableJQueryDialogButtonById('sign-dialog-proceed-btn');
    			RS.enableJQueryDialogButtonById('sign-dialog-sign-btn');
    			
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
    		[
    			{ id: 'sign-dialog-cancel-btn', text: RS.msg("legacyjs.workspace.signingProcess.cancelButton"), click: function(){
    				$("#signDocumentDialog").dialog('close');
    			}},
    			{ id: 'sign-dialog-sign-btn', text: RS.msg("legacyjs.workspace.signingProcess.signButton"), click: function (){
    				if (editable != 'VIEW_MODE'){
    					apprise(RS.msg("legacyjs.workspace.signingProcess.editedCannotSign"));
    					// TO-DO: RSPAC-1287 Focus the apprise dialog
    					$("#signDocumentDialog").dialog('close');
    					return false;
    				}

    				$('.userSignInput').prop('disabled',false);
    				$('#confirmationSection').slideDown('slow');
    				$('#confirmationSection .passSignInput').focus();
    				RS.disableJQueryDialogButtonById('sign-dialog-sign-btn');
    				RS.enableJQueryDialogButtonById('sign-dialog-proceed-btn');
    			}},
    			{ id: 'sign-dialog-proceed-btn', text: RS.msg("legacyjs.workspace.signingProcess.proceedButton"), click: function (){

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
    				
    				RS.blockPage(RS.msg("legacyjs.workspace.signingProcess.signingInProgress"));
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
                        RS.confirm(RS.msg("legacyjs.workspace.signingProcess.signedAndLocked"), "success", 2000,
                                {}, function() { window.location.reload(); });
    				});
    				jqxhr.fail(function(){
    					RS.unblockPage();
    		            RS.ajaxFailed(RS.msg("legacyjs.workspace.signingProcess.signingAction"),false,jqxhr);
    		        });
    			}}
    		]
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
    			RS.disableJQueryDialogButtonById('witness-dialog-proceed-btn');
    			RS.enableJQueryDialogButtonById('witness-dialog-witness-btn');
            },
            close: function ( e ) {
            	$(".passWitnessInput").val("");
            },
    		buttons:
    		[
    			{ id: 'witness-dialog-cancel-btn', text: RS.msg("legacyjs.workspace.signingProcess.cancelButton"), click: function(){
    				$("#witnessDocumentDialog").dialog('close');
    			}},
    			{ id: 'witness-dialog-witness-btn', text: RS.msg("legacyjs.workspace.signingProcess.witnessButton"), click: function (){
    				$('#confirmationWitnessSection').slideDown('slow');
    				$('#confirmationSection .passSignInput').focus();
    				RS.disableJQueryDialogButtonById('witness-dialog-witness-btn');
    				RS.enableJQueryDialogButtonById('witness-dialog-proceed-btn');
    			}},
    			{ id: 'witness-dialog-proceed-btn', text: RS.msg("legacyjs.workspace.signingProcess.proceedButton"), click: function (){

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
    				
    				RS.blockPage(RS.msg("legacyjs.workspace.signingProcess.witnessingInProgress"));
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
    						_closeWitnessingDlgAndConfirm(RS.msg("legacyjs.workspace.signingProcess.witnessed"));
    					} else { // witnessing declined
    						_closeWitnessingDlgAndConfirm(RS.msg("legacyjs.workspace.signingProcess.witnessingDeclined"));
    					}
    				});
    				jqxhr.fail(function(){
    					RS.unblockPage();
    		            RS.ajaxFailed(RS.msg("legacyjs.workspace.signingProcess.witnessingAction"),false,jqxhr);
    		        });
    			}}
    		]
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

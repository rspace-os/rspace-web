
$(document).ready(function () {
    $('.deleteFromCollbGrpLink').click(function (e) {
        e.preventDefault();
        $(this).prev().submit();
    });
    initialiseRequestDlg({
        targetFinderPolicy: 'ALL_PIS',
        availableMessageTypes: "REQUEST_JOIN_EXISTING_COLLAB_GROUP"
    });
    //create collab group
    initialiseRequestDlg({
        targetFinderPolicy: 'ALL_PIS',
        availableMessageTypes: "REQUEST_EXTERNAL_SHARE",
        dialogDivSelector: "#createCollabGroupDlg"
    });

    $('.createRequest').click(function (e) {
        e.preventDefault();
        var grpId = $(this).attr('id').split("_")[1];
        $('#createRequestDlg').data("groupId", grpId).dialog('open');
    });

    $('.createCollabGroup').click(function (e) {
        e.preventDefault();
        $('#createCollabGroupDlg').dialog('open');
    });

    $('.removeLink').click(function () {
        $(this).closest('form').submit();
    });

    $('#changePiLink').click(function (e) {
        e.preventDefault();
        if ($('.setNewPiRadioInput').size() === 0) {
            apprise('There is no-one in the group who could become a new PI.');
            RS.focusAppriseDialog(false);
        } else {
            $('#setNewPiDialog').dialog('open');
        }
    });

    $('#deleteGroup').click(function (e) {
        deleteGroup('selfServiceLabGroup')
    });
    $('#deleteProjectGroup').click(function (e) {
        deleteGroup('projectGroup')
    });
    const deleteGroup = (groupType) => {
        var callback = function () {
            var jxqr = $.post(createURL('/'+groupType+'/deleteGroup/' + groupId), function (xhr) {
                RS.confirm("Group removed", 'success', 5000);
                window.location.replace('/userform');
            });
            jxqr.fail(function () {
                RS.ajaxFailed("Removing group", true, jxqr);
            });
        }

        var event = new CustomEvent('confirm-action', {
            'detail': {
                title: "Confirm deletion",
                consequences: `Are you sure you want to delete the following group: <b>${displayName}</b>?`,
                variant: "warning",
                callback: callback
            }
        });
        document.dispatchEvent(event);
    }

  $(document).ready(function(){
  	$('#setNewPiDialog').dialog({
  		autoOpen:false,
  		title: "Change LabGroup's PI",
  		modal: true,
  		buttons :{
  			Cancel: function () {
  				$(this).dialog('close');
  			},
  			Submit: function () {
  				var newPiId = $("input[name='setNewPi']:checked").val();
  				if(!newPiId) {
  					apprise("Please select a new PI");
            // RSPAC-1287 can't give focus to the apprise dialog
            RS.focusAppriseDialog(true);
  					return;
  				}
  				RS.blockPage("Changing PI", false);
  				var jqxhr = $.post("/groups/ajax/admin/swapPi/" + groupId, {newPiId:newPiId}, function(resp) {
  					if (resp.data) {
  						RS.unblockPage();
  						RS.confirm("PI of group changed; reloading page");
  						window.location = "/groups/view/" + groupId;
  					} else {
  						apprise(RS.getValidationErrorString(resp.errorMsg));
  					}
  				});
  				jqxhr.fail (function (xhr) {
  			        RS.ajaxFailed("Changing the PI of the group", true, jqxhr);
  			    });
  			}
  		 },
  	});
  });

    $('.changeRole').click( function (e){
    	var role = $(this).data('role');
    	var groupId = $(this).data('groupid');
    	var userid = $(this).data('userid');
    	var adminViewAll = $(this).data('adminviewall');
    	$('#changeRoleDialog').data('role',role).data('groupId',groupId)
    	    .data('userid',userid)
    	    .data('adminviewall',adminViewAll)
    	    .dialog('open');
    });

    $('input[name=piCanEditAllWork]').change(function() {
        var data = { canPIEditAll: this.checked };
        var jqxhr = $.post("/groups/ajax/admin/changePiCanEditAll/" + groupId, data, function(data) {
            if (data.data) {
                RS.confirm("PI can edit all updated to: " + data.data);
                window.location="/groups/view/" +groupId;
            } else {
                apprise(RS.getValidationErrorString(data.errorMsg));
            }
        });
        jqxhr.fail (function (xhr) {
            RS.ajaxFailed("Updating PI can edit all", false, jqxhr);
        })
    });

    $('input[name=hideGroupProfile]').change(function() {
        var data = { hideProfile: this.checked };
        var jqxhr = $.post("/groups/ajax/admin/changeHideProfileSetting/" + groupId, data, function(data) {
            if (data.data) {
                RS.confirm("Profile visibility updated");
            } else {
                apprise(RS.getValidationErrorString(data.errorMsg));
            }
        });
        jqxhr.fail (function (xhr) {
            RS.ajaxFailed("Updating profile visibility", false, jqxhr);
        })
    });


    $(document).on('click','#roleOptionAdmin', function (e){
        $('#adminPermissions').show();
    });

    $(document).on('click', '#roleOptionUser', function (e){
        $('#adminPermissions').hide();
    });


    function setUpchangeRoleDialog(){
      $(document).ready(function() {
        $('#changeRoleDialog').dialog({
       		title: "Change  User's Role",
       		resizable: true,
       		autoOpen: false,
       		height:350,
       		width: 350,
       		modal: true,
       		open: function (){
     				var role = $(this).data("role");
     				if(role === "DEFAULT") {
     				$("#roleOptionUser").prop('checked', true); }
     				else { $("#roleOptionAdmin").prop('checked', true);
     						$('#adminPermissions').show();
     				}
     				var adminViewAll = $(this).data("adminviewall");
     				if(adminViewAll == false) {// allow conversion from string to boolean
     				$("#adminViewOptionPersonal").prop('checked', true); }
     				else { $("#adminViewOptionAll").prop('checked', true);
     				}
       		},
     		  buttons: {
         		Cancel: function (){
         			$(this).dialog('close');
         		},
         		OK: function (){
      				var groupId = $(this).data("groupId");
     				  var userid = $(this).data("userid");
         			var newRole = $('input[name=role]:checked').val();
         			var isAuthorized = $('input[name=isAuthorized]:checked').val();

         			var data = { role: newRole, isAuthorized:isAuthorized };
         			var jqxhr = $.post("/groups/ajax/admin/changeRole/"+groupId+"/"+userid, data, function (data){
       					if(data.data) {
       						console.log("UPDATED!!!" + data.data);
       						RS.confirm("Role updated to: " + data.data.roleText
       								 + " with read-all permissions =" + data.data.isAuthorized  );
       						window.location="/groups/view/" +groupId;
       					} else {
       						apprise(getValidationErrorString(data.errorMsg));
       				  }
         			});
         			jqxhr.fail (function (xhr) {
         				RS.ajaxFailed("Updating roles", false, jqxhr);
         			})
         			$(this).dialog('close');
     			  }
         	}, //end buttons
     	  });
      });
    }

    $('#editProfileLink').click (function (e){
 	    e.preventDefault();
 	    $('#editProfileDlg').dialog('open');
    });

    $(".groupDropdown dt a").click(function(e) {
      	e.preventDefault();
      	$(".groupDropdown dd ul").toggle();
    });

    $(document).on('click','#inviteNewMembersGrpLink', function (e){
   	    e.preventDefault();
   	    $('#inviteNewMembersDlg').dialog('open');
        $('#inviteNewMembersDlg .tagit-new input').first().focus();
    });

    $('#exportGroupRecord').on('click', function (e) {
		e.preventDefault();
        RS.getExportSelectionForExportDlg = function() {
            return getExportSelectionFromGroupId(groupId, displayName);
        }
        RS.exportModal.openWithExportSelection(RS.getExportSelectionForExportDlg());
    });

    $('.exportUsersWorkButton').on('click', function(e) {
        e.preventDefault();
        var username = $(this).data('username');
        RS.getExportSelectionForExportDlg = function() {
            return getExportSelectionFromUsername(username);
        }
        RS.exportModal.openWithExportSelection(RS.getExportSelectionForExportDlg());
    });

    $(document).on('click','#renameGrpLink', function (e){
    	$('#renameRecordDirect').dialog('open');
    });
    $(document).on('click','#removeMeFromGrpLink', function (e){
    	$('#removeMeFromGrp').dialog('open');
    });
    RS.emulateKeyboardClick('#renameGrpLink');

    RS.onEnterSubmitJQueryUIDialog('#renameRecordDirect');

    $(document).ready(function(){
			setUpRenameDialog();
			setUpLeaveGroupDialog();

      $('#inviteNewMembersDlg').dialog({
      	autoOpen: false,
      	title: "Invite new members",
      	modal: true,
      	height: 400,
      	width: 400,
      	open : function(event, ui) {
      		initInviteNewMembersDlg();
      	},
      	close : function(event, ui) {
      		$("#inviteNewMembersDlgContent").accordion("destroy");
      		$("#existingUsersTag").tagit("removeAll");
      		$("#nonExistingUsersTag").tagit("removeAll");
      	},
      	buttons :{
      		Cancel: function (){
      			$(this).dialog('close');
      		},
      		Invite: function (){

      			var url = "/cloud/inviteCloudUser";
      			var existingArr = $("#existingUsersTag").tagit("assignedTags");
      			var nonExistingArr = $("#nonExistingUsersTag").tagit("assignedTags");
      			var emails = [].concat(nonExistingArr, existingArr);

      			if(!emails.length) {
      				RS.confirm("No email found to send an invitation.", "notice", 3000);
      				return false;
      			}

      			var data = {
      				groupId : groupId,
      				emails : emails,
      			};

      			var jqxhr = $.post(url, data, function(result) {
      				var data = result.data;
      				if(data) {
      					var s = "<br>The following people have been invited to join the group : <ul>";
      					var emails = "";
      					$.each(data, function(index, obj) {
      						emails += "<li>"+obj+"</li>";
      					});
      					s += emails + "</ul><br> Invited users will be added to the group when they accept their invitation.";
      					RS.confirmAndNavigateTo(s, "notice", 2000, "/groups/view/"+groupId);
      				} else {
      					RS.confirm("Please check the email addresses.", "notice", 3000);
      				}
      			});

      			jqxhr.fail (function (xhr) {
        				RS.ajaxFailed("Inviting new users", false, jqxhr);
        			});
      		 }
      	 }, //end buttons
      }); //end dialog
    });

    setUpchangeRoleDialog();

	$(document).on("click", ".cancel", function(e){
		e.preventDefault();
		var requestId = $(this).data("requestid");
		var recipientId = $(this).data("recipientid");
		var data={
			requestId:requestId,
			recipientId:recipientId,
		};

		$.post(createURL('/dashboard/ajax/cancelRecipient'),
			data, function (xhr) {
		 		var msg = xhr.data.entity;
		 		$().toastmessage('showToast', {
		 			text     : msg,
					sticky   : false,
					position : 'top-right',
					type     : 'notice',
					stayTime : 1000,
					close : function() {
						if(xhr.data.succeeded) {
							window.location.href=createURL('/groups/view/'+groupId);
						}
					}
		 		});
		});
	});
});

function  setUpLeaveGroupDialog () {
	$('#removeMeFromGrp').dialog({
		autoOpen: false,
	  	title: "Confirm Leave Group",
	  	modal: true,
	  	buttons: {
	  		Cancel: function (){
	  			$(this).dialog('close');
	  		},
	  		"Leave Group": function () {
	  			$(this).dialog('close');
	  			var jxqr = $.post("/groups/admin/removeSelf/" + groupId,  function(result) {
      				var data = result.data;
      				if(data == true) {
      					RS.confirmAndNavigateTo("You have left the group", "notice", 3000, "/groups/view/"+groupId);
      				}else if(data == false) {
      					RS.confirm("You have left the group. This was a private group; you are no longer authorised to view its profile.", "notice", 6000, "/groups/view/"+groupId);
      				} else {
      					apprise(getValidationErrorString(result.errorMsg));
      				}
      			});

      			jxqr.fail (function (xhr) {
      				RS.ajaxFailed("Leaving group", false, xhr);
        		});
	  		}
	  	}
	});
}

function setUpRenameDialog() {
	$('#renameRecordDirect').dialog({
		autoOpen:false,
		title: "Rename Group",
		modal: true,
		buttons :{
			Cancel: function (){
				$(this).dialog('close');
			},
			Rename: function (){
				var newName=$('#nameFieldDirect').val();
				if(newName === ""){
					apprise("Please enter a name!");
					return;
				}
				$(this).dialog('close');

				$('.recordName').text(newName);
				var data = {
					newname:newName
				};

				$.post("/groups/rename/"+ groupId, data, function (data){
					if(data.errorMsg !== null) {
						apprise(getValidationErrorString(data.errorMsg));
					} else {
						$('.displayname').text(newName);
					}
				});
			}
		}, //end buttons
  }); // end dialog
}
function validateEmail(email){
	var re = /^([\w-]+(?:\.[\w-]+)*)@((?:[\w-]+\.)*\w[\w-]{0,66})\.([a-z]{2,6}(?:\.[a-z]{2})?)$/i;
    return re.test(email);
}



function initInviteNewMembersDlg(){

	$("#inviteNewMembersDlgContent").accordion({
		heightStyle: "content",
	});

	$("#existingUsersTag").tagit({
	    placeholderText: "Type and find users by name, username or email",
	    beforeTagAdded: function(event, ui) {
	        var isAutocompleteList = false;
	        $.each(autocompletePublicUserInfoSrcArray, function(i,obj) {
	        	if (obj.value === ui.tagLabel) {
	        		isAutocompleteList = true;
	        	}
	        });

	        if(isAutocompleteList === false){
	        	RS.confirm("Please check email. <br> Select an existing RSpace user email from the autocompleted list.", "notice", 2000);
	        	return false;
	        }
	    },
	    afterTagAdded: function(event, ui) {
        $('#existingUsersTag .tagit-choice').attr('tabindex', 0);
	    },
	    autocomplete: {
			minLength: 3,
			source: autocompletePublicUserInfoSource,
		}
	});
  RS.addOnKeyboardClickHandlerToDocument('#existingUsersTag .tagit-choice', function(e){
    $(this).find('.tagit-close').click();
  });

	$("#nonExistingUsersTag").tagit({
	    placeholderText: "Type and enter email",
	    beforeTagAdded: function(event, ui) {
	        console.log("beforeTagAdded \t"+ui.tagLabel);
	        if(! validateEmail(ui.tagLabel)) {
	        	RS.confirm("Please check email syntax", "error", 1000);
	        	return false;
	        }
	    },
      afterTagAdded: function(event, ui) {
        $('#nonExistingUsersTag .tagit-choice').attr('tabindex', 0);
      }
	});
  RS.addOnKeyboardClickHandlerToDocument('#nonExistingUsersTag .tagit-choice', function(e){
    $(this).find('.tagit-close').click();
  });
}

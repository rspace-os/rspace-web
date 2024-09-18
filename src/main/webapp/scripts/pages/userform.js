
function initUploadImageDialog(){
  $(document).ready(function(){
    $('#uploadImageDialog').dialog({
      title: "Upload Image",
      resizable: true, 
      autoOpen: false,
      height: 500,
      width: 600,
      modal: true,
      create: function() {
        $(this).parent().addClass('bootstrap-custom-flat');
      },
      open: function() {
        initPhotoTakingArea();
      },
      close: function() {
        stopVideoStream();
      },
      buttons: 
      {
        Cancel: function(){
          $("#uploadImageDialog").dialog('close');
          stopVideoStream();
        },
        Upload: function (e){

          var cfg = {
            postParamName:"imageFile",
            postURL:"/userform/profileImage/upload",
            "onloadSuccess":function (json){
                window.location.reload();
            }
          };
          
          var photoTabActive = $("#uploadImageTabs").tabs("option", "active") === 1;
          if (photoTabActive && photoSnapped) {
            var canvas = document.getElementById('profileImageVideoCanvas');
            canvas.toBlob(function(blob) {
              cfg.fileBlob = blob;
              cfg.filename = 'from_camera.png';
              RS.submitIconUpload(cfg);
            });
          } else {
            cfg.fileInputId = "#fileChooser";
            RS.submitIconUpload(cfg);
          }
        }
      }
    });    
  });
}

var photoTakingInitialised = false;
var photoSnapped = false;
var photoMaxDimension = 120; // will scale down to this dimension
var videoStream; 

function initPhotoTakingArea() {

  if (photoTakingInitialised) {
    return;
  }
  photoTakingInitialised = true;

  RS.onVideoInputAvailable(function() {

    $('#takePhotoTabLink').show();
    var videoArea = document.getElementById('profileImageVideo');
    
    // Trigger photo take
    $("#profileImageVideoSnap").on("click", function(e) {
      if (videoStream) {
        var canvas = document.getElementById('profileImageVideoCanvas');
        var scale = photoMaxDimension / Math.max(videoArea.videoWidth, videoArea.videoHeight); 
        var canvasWidth = videoArea.videoWidth * scale;
        var canvasHeight = videoArea.videoHeight * scale;
        canvas.width = canvasWidth;
        canvas.height = canvasHeight;

        // for non-square pictures, so they align to video
        $(canvas).css('margin-top', (photoMaxDimension - canvasHeight) / 2);
        $(canvas).css('margin-left', (photoMaxDimension - canvasWidth) / 2);

        var context = canvas.getContext('2d');
        context.drawImage(videoArea, 0, 0, canvasWidth, canvasHeight);
        photoSnapped = true;
      }
    });
    
    $("#takePhotoTabLink").on("click", function(e) {
      var videoCfg = {
        video: {
          width: {min: photoMaxDimension, ideal: photoMaxDimension},
          height: {min: photoMaxDimension, ideal: photoMaxDimension}
        }
      };
      navigator.mediaDevices.getUserMedia(videoCfg)
        .then(function(stream) {
          videoStream = stream;
          videoArea.srcObject = videoStream;
          videoArea.play();
        })
        .catch(function(err) {
          apprise('Sorry, there are some problems with accessing your camera');
          console.log('error: ' + err.name + " - " + err.message);
        });
    });
  
    $("#uploadImageTabLink").on("click", function(e) {
      stopVideoStream();
    });
  });

  $('#uploadImageTabs').tabs();
}

function stopVideoStream() {
  if (videoStream) {
    videoStream.getVideoTracks().forEach(function (track) {
      track.stop();
    });
  }
}

function initProfileDialog(){
  $(document).ready(function(){
    $('#editProfileDialog').dialog({
      title: "Edit Profile",
      resizable: true, 
      autoOpen: false,
      height: 500,
      width: 600,
      modal: true,
      create: function() {
        $(this).parent().addClass('bootstrap-custom-flat');
      },
      open: function( e ) {
        $("#firstNameInput").val($("#firstName").text().trim());
        $("#surnameInput").val($("#lastName").text().trim());
        $("#externalLinkInput").val($("#externalLink").text().trim());
        $("#linkDescriptionInput").val($("#linkDescription").text().trim());
        $("#additionalInfoArea").val($("#additionalInfo").text().trim());
        $("#newAffiliation").val($("#affiliation").text().trim());
      },
      close: function( e ) {
        $(".userFormInput").val('');
      }, 
      buttons: 
      {
        Cancel: function(){
          $("#editProfileDialog").dialog('close');
        },
        Save: function(){
          var data = $('#userProfileForm').serialize();
          
          RS.blockPage("Editing profile ...");
          var jqxhr= $.post(createURL("/userform/ajax/editProfile"), data, function (result) { 
            RS.unblockPage();
            if(result.data != null){
              RS.confirm("Profile updated", "success", 3000);
              $("#firstName").text(result.data.firstName);
              $("#lastName").text(result.data.lastName);
              $("#linkDescription").text(result.data.profile.externalLinkDisplay);
              $("#externalLink").attr("href",result.data.profile.externalLinkURL);
              $("#externalLink").text(result.data.profile.externalLinkURL);
              if ($("#externalLink").attr('href').length > 0) {
                $("#externalLink").show();
              } else {
                $("#externalLink").hide();
              }
              $("#additionalInfo").text(result.data.profile.profileText);
              $('#affiliation').text(result.data.affiliation);
              $('#currentInName').text(result.data.firstName + ' ' + result.data.lastName );
              $("#editProfileDialog").dialog('close');
            } else {
              $('#msgAreaProfile').text(getValidationErrorString (result.errorMsg));
              $('#msgAreaProfile').slideDown('slow');
              
            }
          });
          jqxhr.fail(function(){
            RS.unblockPage();
            RS.ajaxFailed("Edit profile process",false,jqxhr);
          });
        }
      }
    });
  });
  
  applyAffiliationAutocomplete(4, "#newAffiliation");
}

function initChangePasswordDialog(){
  $(document).ready(function(){
    $('#changePasswordDialog').dialog({
      title: "Change Password",
      resizable: true, 
      autoOpen: false,
      height: 500,
      width: 600,
      modal: true,
      create: function() {
        $(this).parent().addClass('bootstrap-custom-flat');
      },
      close: function( e ) {
        $(".userFormInput").val('');
        $(".msgArea").hide();
      }, 
      buttons: 
      {
        Cancel: function(){
          $("#changePasswordDialog").dialog('close');
        },
        Save: function (){
    
          var currentPassword = $("#currentPasswordInput").val();
          var newPassword   = $("#newPasswordInput").val();
          var confirmPassword = $("#newPasswordConfirm").val();
          var hintPassword  = $("#newPasswordHint").val();
          var data = { 
              currentPassword: currentPassword,
              newPassword: newPassword,
              confirmPassword: confirmPassword,
              hintPassword: hintPassword
          };
          
          RS.blockPage("Changing password...");
          var jqxhr= $.post(createURL("/userform/ajax/changePassword"), data, function (result) { 
            RS.unblockPage();
            var msgx = new String(result.data);
            
            if (msgx.indexOf("successfully") != -1) {
              RS.confirm(msgx, "success", 3000);
              $("#userPasswordHint").text(hintPassword);
              $("#changePasswordDialog").dialog('close');
            } else {
              $('#msgAreaPassword').text(msgx);
              $('#msgAreaPassword').slideDown('slow');
            }
          });
          jqxhr.fail(function(){
            RS.unblockPage();
            RS.ajaxFailed("Change password process", false, jqxhr);
          });
        }
      }
    });
  });
}

function initChangeVerificationPasswordDialog(){
  $(document).ready(function(){
	  $('#changeVerificationPasswordDialog').dialog({
	    title: "Change Verification Password",
	    resizable: true, 
	    autoOpen: false,
	    height: 500,
	    width: 600,
	    modal: true,
      create: function() {
        $(this).parent().addClass('bootstrap-custom-flat');
      },
      close: function( e ) {
        $(".userFormInput").val('');
        $(".msgArea").hide();
      }, 
	    buttons: 
	    {
	      Cancel: function(){
	        $("#changeVerificationPasswordDialog").dialog('close');
	      },
	      Change: function (){
	  
	        var currentVerificationPassword = $("#currentVerificationPasswordInput").val();
	        var newVerificationPassword   = $("#newVerificationPasswordInput").val();
	        var confirmVerificationPassword = $("#newVerificationPasswordConfirm").val();
	        var hintVerificationPassword  = $("#newVerificationPasswordHint").val();
	        var data = { 
	            currentVerificationPassword: currentVerificationPassword,
	            newVerificationPassword: newVerificationPassword,
	            confirmVerificationPassword: confirmVerificationPassword,
	            hintVerificationPassword: hintVerificationPassword
	        };
	        
	        RS.blockPage("Changing verification password...");
	        var jqxhr= $.post(createURL("/vfpwd/ajax/changeVerificationPassword"), data, function (result) { 
	          RS.unblockPage();
	          var msgx = new String(result.data);
	          
	          if (msgx.indexOf("successfully") != -1) {
	            RS.confirm(msgx, "success", 3000);
	            $("#userVerificationPasswordHint").text(hintVerificationPassword);
	            $("#changeVerificationPasswordDialog").dialog('close');
	          } else {
	            $('#msgAreaVerificationPassword').text(msgx);
	            $('#msgAreaVerificationPassword').slideDown('slow');
	          }
	        });
	        jqxhr.fail(function(){
	          RS.unblockPage();
              RS.ajaxFailed("Change verification password process",false,jqxhr);
            });
	      }
	    }
	  });
	});
}

function initSetVerificationPasswordDialog(){
  $(document).ready(function(){
	  $('#setVerificationPasswordDialog').dialog({
	    title: "Set Verification Password",
	    resizable: true, 
	    autoOpen: false,
	    height: 500,
	    width: 600,
	    modal: true,
      create: function() {
        $(this).parent().addClass('bootstrap-custom-flat');
      },
      close: function( e ) {
        $(".userFormInput").val('');
        $(".msgArea").hide();
      }, 
	    buttons: 
	    {
	      Cancel: function(){
	        $("#setVerificationPasswordDialog").dialog('close');
	      },
	      Set: function (){
	  
	        var newVerificationPassword   = $("#newSetVerificationPasswordInput").val();
	        var confirmVerificationPassword = $("#newSetVerificationPasswordConfirm").val();
	        var hintVerificationPassword  = $("#newSetVerificationPasswordHint").val();
	        var data = { 
	            newVerificationPassword: newVerificationPassword,
	            confirmVerificationPassword: confirmVerificationPassword,
	            hintVerificationPassword: hintVerificationPassword
	        };
	        
	        RS.blockPage("Setting verification password...");
	        var jqxhr= $.post(createURL("/vfpwd/ajax/setVerificationPassword"), data, function (result) { 
	          RS.unblockPage();
	          var msgx = new String(result.data);
	          
	          if (msgx.indexOf("successfully") != -1) {
	            RS.confirm(msgx, "success", 3000);
	            $("#userVerificationPasswordHint").text(hintVerificationPassword);
	            $("#setVerificationPasswordDialog").dialog('close');
	            $("#userSetVerificationPasswordButton").hide();
	          } else {
	            $('#msgAreaSetVerificationPassword').text(msgx);
	            $('#msgAreaSetVerificationPassword').slideDown('slow');
	          }
	        });
	        jqxhr.fail(function(){
	          RS.unblockPage();
              RS.ajaxFailed("Set verification password process",false,jqxhr);
            });
	      }
	    }
	  });
	});
}

function initPwdConfirmDlg () {
  $(document).ready(function(){
    $('#pwdConfirmDialog').dialog({
      title: "Confirm Password",
      resizable: true, 
      autoOpen: false,
      height: "auto",
      width: "auto",
      modal: true,
      create: function() {
        $(this).parent().addClass('bootstrap-custom-flat');
      },
      close: function ( e ) {
        $("#pwdConfirm").val('');
        $(".msgArea").hide();
      },     
      buttons: {
        Cancel: function() {
          $("#pwdConfirmDialog").dialog('close');
        },
        OK: function () {
          var pwd = $('#pwdConfirm').val();
          var jqxhr = $.post('/userform/ajax/apiKey', {"password":pwd})
                       .done(function(data) {
                           showRSApiKey = true;
                           renderApiKeyMenu(data);
                       })
                       .fail(function() {
                          RS.ajaxFailed("Creating a new API key", false, jqxhr);
                       });
          $("#pwdConfirmDialog").dialog('close');
        }
      }
    });
  });
  
  $("#pwdConfirmDialog").keypress(function(e){
    if (e.keyCode === 13){
      $(this).parent().find('.ui-dialog-buttonset button:eq(1)').click();
    }
  });
}

function initChangeEmailDialog(){
  $(document).ready(function(){
    $('#changeEmailDialog').dialog({
      title: "Change Email",
      resizable: true, 
      autoOpen: false,
      height: 500,
      width: 600,
      modal: true,
      create: function() {
        $(this).parent().addClass('bootstrap-custom-flat');
      },
      open: function ( e ) {
        $("#newEmailInput").val($("#userEmail").text());
        $("#newEmailConfirm").val($("#userEmail").text());
          },
          close: function ( e ) {
            $(".userFormInput").val('');
            $(".msgArea").hide();
          }, 
      buttons: 
      {
        Cancel: function(){
          $("#changeEmailDialog").dialog('close');
        },
        Save: function (){
    
          var newEmailInput = $("#newEmailInput").val();
          var newEmailConfirm = $("#newEmailConfirm").val();
          var emailPasswordInput  = $("#emailPasswordInput").val();
          var data = { 
              newEmailInput: newEmailInput,
              newEmailConfirm: newEmailConfirm,
              emailPasswordInput: emailPasswordInput,
          };
          
          RS.blockPage("Changing email ...");
          var jqxhr= $.post(createURL("/userform/ajax/changeEmail"), data, function (result) { 
            RS.unblockPage();
            if (result.data) {
                var msg = '';
                if ("SUCCESS" === result.data) {
                    msg = 'Email changed successfully';
                          $("#userEmail").text(newEmailInput);
                          $('.rs-info__email').text(newEmailInput);
                } else if ("VERIFICATION" === result.data) {
                    msg = 'Verification link has been sent to the new email address';
                }
              RS.confirm(msg, "success", 3000);
              $("#changeEmailDialog").dialog('close');
            } else{
              $('#msgAreaEmail').text(getValidationErrorString(result.errorMsg));
              $('#msgAreaEmail').slideDown('slow');
            }
          });
          jqxhr.fail(function(){
            RS.unblockPage();
            RS.ajaxFailed("Change email process",false,jqxhr);
          });
        }
      }
    });
  });
}

function displayOrcidIdSpan() {
  var orcidId = $('#orcidIdSpan').data('orcidid');
  var orcidIdSet = !!orcidId;
  if (orcidId) {
    var url = 'http://orcid.org/' + orcidId;
    $('#userOrcidIdLink').attr('href', url).text(url);
  }
  $('#orcidIdSpan, #deleteOrcidIdButton').toggle(orcidIdSet);
  $('#setOrcidIdButton').toggle(!orcidIdSet);
}

var showRSApiKey = false;

function computeDaysAgoLabel(days) {
  const rtf = new Intl.RelativeTimeFormat("en", {
    style: "long",
    numeric: "auto", // uses today rather than 0 days ago
  });

  if (days < 7) return rtf.format(days * -1, "days");
  if (days < 31) return rtf.format(Math.floor(days / 7) * -1, "weeks");
  if (days < 365) return rtf.format(Math.floor(days / 31) * -1, "months");
  return rtf.format(Math.floor(days / 365) * -1, "years");
}

function renderApiKeyMenu(serverResponse) {
  if (!serverResponse.data) {
    apprise(getValidationErrorString(serverResponse.errorMsg));
    return;
  }
  var $apiKeyInfo = $('#apiKeyInfo');
  var apiKeyInfoTemplate = $('#apiKeyDetailsTemplate').html();

  var keyInfoData = {
    ...serverResponse.data,
    ageLabel: computeDaysAgoLabel(serverResponse.data.age),
  };
  var htmlData = Mustache.render(apiKeyInfoTemplate, keyInfoData);
  $apiKeyInfo.html(htmlData);
  if (keyInfoData.key) {
    $('#api-menu__keyValue').text(keyInfoData.key);
  }
  $('#api-menu__keyValue, #api-menu__hideKey').toggle(showRSApiKey);
  $('#api-menu__showKey').toggle(!showRSApiKey);
}

function showApiKeyValue(serverResponse) {
  if (!serverResponse.data) {
    apprise(getValidationErrorString(serverResponse.errorMsg));
    return;
  }
  $('#api-menu__keyValue').text(serverResponse.data);
  $('#api-menu__keyValue, #api-menu__hideKey').toggle(showRSApiKey);
  $('#api-menu__showKey').toggle(!showRSApiKey);
}

function initApiKeyDisplay () {

  function updateApiKeyMenu() {
    $.get('/userform/ajax/apiKeyDisplayInfo')
     .then(renderApiKeyMenu);
  }

  $(document).on("click", "#apiKeyRegenerateBtn", function(e) {
    e.preventDefault();

	  $.get('/vfpwd/ajax/checkVerificationPasswordNeeded', function(response) {  
      	if (response.data) {
      		apprise("Please set your verification password before generating an api key.");
      	} else {
          $('#pwdConfirmDialog').dialog('open');
      	}
	  });
  });
  
  $(document).on("click", "#apiKeyRevokeBtn", function(e) {
    e.preventDefault();

    $.post('/userform/ajax/apiKey', {"_method":"DELETE"}, function (intDeleted){
      if (intDeleted > 0) {
        RS.defaultConfirm("Key deleted");
      } else {
        RS.confirm("Key was not deleted, please try again or ask support", "warning", 3000);
      }
      updateApiKeyMenu();
    });
  });
  
  $(document).on("click", "#api-menu__showKey, #api-menu__hideKey", function() {
    showRSApiKey = !showRSApiKey;
    if (showRSApiKey) {
      $.get('/userform/ajax/apiKeyValue')
        .then(showApiKeyValue);
    }
    $('#api-menu__keyValue, #api-menu__hideKey').toggle(showRSApiKey);
    $('#api-menu__showKey').toggle(!showRSApiKey);
  });

  updateApiKeyMenu();
}

$(document).ready(function (){
  initUploadImageDialog();
  initProfileDialog();
  initChangePasswordDialog();
  initChangeVerificationPasswordDialog();
  initSetVerificationPasswordDialog();
  initChangeEmailDialog();
  initApiKeyDisplay();
  initPwdConfirmDlg();
  
  $('#userUploadImageButton').click(function(e) {
    $('#uploadImageDialog').dialog('open');
    $('#uploadImageDialog').find("#uploadImageTabLink").focus();
  });
  
  $('#userChangePasswordButton').click(function(e) {
    $('#changePasswordDialog').dialog('open');
    });
  
  $('#userChangeVerificationPasswordButton').click(function(e) {
	$('#changeVerificationPasswordDialog').dialog('open');
	});
  
  $('#userSetVerificationPasswordButton').click(function(e) {
	$('#setVerificationPasswordDialog').dialog('open');
	});
  
  $('#userForgotVerificationPasswordButton').click(function(e) {
	$.post('/vfpwd/verificationPasswordResetRequest').done(function() {
		$().toastmessage('showSuccessToast', 'Password reset link sent.  Please check your email.');
	}).fail(function() {
		$().toastmessage('showErrorToast', 'An error occurred while sending email, please try again.');
	});
  });
  
  $('#userEditProfileButton').click(function(e) {
    $('#editProfileDialog').dialog('open');
    });
   
  $('#userChangeEmailButton').click(function(e) {
	  $.get('/vfpwd/ajax/checkVerificationPasswordNeeded', function(response) {  
      	if (response.data) {
      		apprise("Please set your verification password before changing your email.");
      	} else {
      		$('#changeEmailDialog').dialog('open');
      	}
	  });
  });
   
  $('#userChangeDetailsButton').click(function(e) {
    $('#changeNameAffiliationDialog').dialog('open');
  });
   
  $('.deleteFromCollbGrpLink').click(function (e){
    e.preventDefault();
    $(this).prev().submit();
  });
  
  initialiseRequestDlg({
    targetFinderPolicy:'ALL_PIS',
    availableMessageTypes:"REQUEST_JOIN_EXISTING_COLLAB_GROUP"
  });
   
  $('.createRequest').click(function(e) {
    e.preventDefault();
    var grpId= $(this).attr('id').split("_")[1];
    $('#createRequestDlg').data("groupId", grpId).dialog('open');
  });
  
  $(document).on("change","#fileChooser", function(e) {   
    e.preventDefault();
      var files = e.target.files; // FileList object      
      // This code generates a thumbnail in the dialog.
      // Loop through the FileList and render image files as thumb-nails.
      f = files[0];
      // Only process image files.
      if (f.type.match('image.*')) {
        var reader = new FileReader();
        // Closure to capture the file information.
        reader.onload = (function(theFile) {
          return function(e) {
            $('#msgAreaImage').hide();
            var span = document.createElement('span');
            span.innerHTML = ['<img class="thumb" src="', e.target.result,'" title="', escape(theFile.name), '" width="140"/>'].join('');
            $('#imagePreview').empty().append(span);
          };
        })(f);
        // Read in the image file as a data URL.
        reader.readAsDataURL(f);
      }
  });

  displayOrcidIdSpan();
  $('#setOrcidIdButton').click(function() {
    var orcidClientId = $(this).data('orcidclientid');
    var orcidRedirectUri = $(this).data('orcidredirecturi');
    var url = "https://orcid.org/oauth/authorize?client_id=" + orcidClientId + "&redirect_uri=" 
      + orcidRedirectUri + "&response_type=code&scope=/authenticate";

    RS.openOauthAuthorizationWindow(url, '/orcid/redirect_uri', '#orcidAPIconnectionSuccess', function(authWindow) {
            var orcidId = $(authWindow.document.body).find('#orcidId').text();
            var orcidOptionsId = $(authWindow.document.body).find('#orcidOptionsId').text();
            $('#orcidIdSpan').data('orcidid', orcidId);
            $('#deleteOrcidIdButton').data('orcidoptionsid', orcidOptionsId);
            displayOrcidIdSpan();
        });
  });
  $('#deleteOrcidIdButton').click(function() {
    var optionsId = $('#deleteOrcidIdButton').data('orcidoptionsid');
    console.log('deleting options', optionsId);
    var jqxhr = $.post('/integration/deleteAppOptions?appName=ORCID', { "optionsId" : optionsId });
    jqxhr.done(function() {
        console.log('... deleted');
        $('#orcidIdSpan').data('orcidid', '');
        $('#deleteOrcidIdButton').data('orcidoptionsid', '');
        displayOrcidIdSpan();
    });
  });

  function _redrawFilestoreConnectionSection() {
      $('#egnyteConnectedDiv').toggle(egnyteFilestoreConnectionOK);
      $('#egnyteDisonnectedDiv').toggle(!egnyteFilestoreConnectionOK);
  }
  
  if (typeof egnyteFilestoreEnabled !== 'undefined') {
      _redrawFilestoreConnectionSection();
      $('#egnyteDisconnectBtn').click(function() {
          var jqxhr = $.post('/egnyte/disconnectUserFromEgnyteFilestore');
          jqxhr.done(function(data) {
              if (data.data) {
                  egnyteFilestoreConnectionOK = false;
                  _redrawFilestoreConnectionSection();
              }
          });
          jqxhr.fail(function() {
              RS.ajaxFailed("Removing Egnyte token", false, jqxhr);
          });
      });
  }
});

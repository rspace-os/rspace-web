<%@ include file="/common/taglibs.jsp"%>
<head>
	<title>Connect to Egnyte</title>
</head>

<!-- Hide the top menu tabs -->
<style type="text/css">

    .rs-navbar .rs-navbar__item:not(#branding) {
      display: none !important;  
    }
    .engyteSetupInfoDiv {
      margin: 20px;
      font-size: 16px !important;
      text-align: center;
    }
    .egynteLoginDiv {
      width: 380px;
      margin: 30px auto 0px auto;
      box-shadow: 0px 1px 8px rgba(0, 0, 0, 0.28);
      border-radius: 3px;
    }
    .egynteLoginDiv__form {
      padding: 20px 20px 10px 20px;
    }
    .rs-field {
      position: relative;
      box-sizing: border-box;
    }
    .rs-field input {
      width: 100%;
    }
    #egnyteConnectBtn {
      width: 100%;
      margin-top: 20px;
    }
    .errorMsgDiv {
      padding: 0px 0px 20px 20px; 
      color: red; 
    }
    .goToWorkspaceLinkDiv {
      margin: 20px 0px 20px 380px;
      font-size: larger;
    }
}
</style>

<script>

    var isEgnyteConnectionSetupPage = true;
    
    $(document).ready(function() {
        
        var connectToEgnyteServer = function() {
            var egnyteTokenRequestData = {
                egnyteUsername: $('#engyteUsername').val(),
                egnytePassword: $('#egnytePassword').val(),
            };
            RS.blockPage("Connecting to Egnyte filestore...");
            var jqxhr = $.post("/egnyte/connectUserToEgnyteFilestore", egnyteTokenRequestData);
            
            jqxhr.done(function(data) {
                RS.unblockPage();
                if (data.data) {
                    RS.confirm("Egnyte connected successfully");
                    $('.errorMsgDiv').hide();
                    RS.blockPage("Egnyte connection details saved. Opening the Workspace...");
                    RS.navigateTo('/workspace');
                } else {
                    $('.errorMsgDiv').show().text(data.error.errorMessages.join(','));
                }
            });
            jqxhr.fail(function() {
                RS.ajaxFailed("Egnyte authentication", true, jqxhr);
            });
            return false;
        };
        
        RS.addOnEnterHandlerToDocument("#egnytePassword", connectToEgnyteServer);
        $('#egnyteConnectBtn').click(connectToEgnyteServer);
        

        $('#skipEgnyteConfigLink').click(function() {
            apprise("Are you sure you want to skip Egnyte account configuration? " +
                    "The files uploaded to RSpace will not be saved in Egnyte.", 
                    { textOk : "Skip"}, function() { RS.navigateTo('/workspace'); });
        });
    });

</script>


<div class="page"> 

  <div class="engyteSetupInfoDiv">Please provide your Egnyte credential so RSpace can access your account.</div>

  <div class="egynteLoginDiv bootstrap-custom-flat">
    <form class="egynteLoginDiv__form" autocomplete="off">
      <fieldset>
        <div class="form-group rs-field">
            <input type="text" id="engyteUsername" placeholder="Egnyte Username"></input>
        </div>
        <div class="form-group rs-field">
            <input type="password" id="egnytePassword" placeholder="Egnyte Password"></input>
        </div>
        <div class="form-group rs-field">
            <button id="egnyteConnectBtn" role="button" type="submit" class="btn btn-primary">Connect</button>
        </div>
      </fieldset>
    </form>
    
    <div class="errorMsgDiv" style="display:none"></div>
  </div>

  <div class="goToWorkspaceLinkDiv">
    <a id="skipEgnyteConfigLink" href="#">Skip Egnyte configuration, go to Workspace</a>
  </div>

</div>



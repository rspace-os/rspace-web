<%@ include file="/common/taglibs.jsp"%>
<head>
	<title><spring:message code="connect.egnyte.connectionSetup.title"/></title>
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
            RS.blockPage(RS.msg("legacyjs.egnyte.connectingToFilestore"));
            var jqxhr = $.post("/egnyte/connectUserToEgnyteFilestore", egnyteTokenRequestData);

            jqxhr.done(function(data) {
                RS.unblockPage();
                if (data.data) {
                    RS.confirm(RS.msg("legacyjs.egnyte.connectedSuccessfully"));
                    $('.errorMsgDiv').hide();
                    RS.blockPage(RS.msg("legacyjs.egnyte.connectionSavedOpeningWorkspace"));
                    RS.navigateTo('/workspace');
                } else {
                    $('.errorMsgDiv').show().text(data.error.errorMessages.join(','));
                }
            });
            jqxhr.fail(function() {
                RS.ajaxFailed(RS.msg("legacyjs.egnyte.authenticationAction"), true, jqxhr);
            });
            return false;
        };

        RS.addOnEnterHandlerToDocument("#egnytePassword", connectToEgnyteServer);
        $('#egnyteConnectBtn').click(connectToEgnyteServer);


        $('#skipEgnyteConfigLink').click(function() {
            apprise(RS.msg("legacyjs.egnyte.skipConfirm"),
                    { textOk : RS.msg("legacyjs.egnyte.skipConfirmOk")}, function() { RS.navigateTo('/workspace'); });
        });
    });

</script>


<div class="page"> 

  <div class="engyteSetupInfoDiv"><spring:message code="connect.egnyte.connectionSetup.credentialsPrompt"/></div>

  <div class="egynteLoginDiv bootstrap-custom-flat">
    <form class="egynteLoginDiv__form" autocomplete="off">
      <fieldset>
        <div class="form-group rs-field">
            <input type="text" id="engyteUsername" placeholder="<spring:message code='connect.egnyte.connectionSetup.usernamePlaceholder'/>"></input>
        </div>
        <div class="form-group rs-field">
            <input type="password" id="egnytePassword" placeholder="<spring:message code='connect.egnyte.connectionSetup.passwordPlaceholder'/>"></input>
        </div>
        <div class="form-group rs-field">
            <button id="egnyteConnectBtn" role="button" type="submit" class="btn btn-primary"><spring:message code="apps:actions.connect"/></button>
        </div>
      </fieldset>
    </form>

    <div class="errorMsgDiv" style="display:none"></div>
  </div>

  <div class="goToWorkspaceLinkDiv">
    <a id="skipEgnyteConfigLink" href="#"><spring:message code="connect.egnyte.connectionSetup.skipConfigLink"/></a>
  </div>

</div>


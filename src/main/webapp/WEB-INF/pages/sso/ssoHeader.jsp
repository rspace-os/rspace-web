<%@ include file="/common/taglibs.jsp" %>

<script >
    var ssoheader_ssoUsername = '${sessionScope.remoteUserUsername}' || '${remoteUserUsername}';
    var ssoheader_rspaceUsername = '${sessionScope.userInfo.username}';
    var ssoheader_rspaceUsernameAlias = '${sessionScope.userInfo.usernameAlias}';
    var ssoheader_operateAs =  "${sessionScope['rs.IS_RUN_AS']}" == 'true';

    $(document).ready(function() {
        
        var rspaceUserSameAsSsoUser = (ssoheader_ssoUsername == ssoheader_rspaceUsername) ||
            (ssoheader_rspaceUsernameAlias == ssoheader_ssoUsername);
        
        if (!rspaceUserSameAsSsoUser) {
            $('#ssoDifferentUserHeaderDiv').show();
            $('.ssoUsername').text(ssoheader_ssoUsername);
            
            if (ssoheader_rspaceUsername) {
              $('#ssoCurrentlyLoggedAsSpan').show();
              $('.rspaceUsername').text(ssoheader_rspaceUsername);
            }

            $('#ssoBackToSessionBtn').click(function() {
              window.location.href = "/adminLogin/backToSsoUserWorkspace";
            });

            /* don't show logout within RSpace session, it would just duplicate 'Account'->'Sign out' */
            $('#ssoLogoutBtn').toggle(ssoheader_rspaceUsername == '');
            $('#ssoLogoutBtn').click(function() {
              window.location.href = "/adminLogin/logoutFromSso"; 
            });
        }
        
    });
    
</script>

<style>
  #ssoDifferentUserHeaderDiv {
    padding: 10px 0px;
    margin-top: 10px;
    border: 2px solid #2196f3;
    text-align: center;
    font-size: 11pt;
  }

  #ssoDifferentUserHeaderDiv .usernameSpan {
    font-weight: bold
  }

  #ssoDifferentUserHeaderDiv .btn {
    border: 1px solid #ccc;
    padding: 6px 12px;
  }

</style>

<div id="ssoDifferentUserHeaderDiv" class="bootstrap-custom-flat" style="display:none">
    <div class="rs-field" style="display:inline-block; width: 50%; vertical-align:middle;">
        SSO&nbsp;user:&nbsp;<span class="ssoUsername usernameSpan"></span>
        <span id="ssoCurrentlyLoggedAsSpan" style="display:none"> |
            Current&nbsp;RSpace&nbsp;user:&nbsp;<span class="rspaceUsername usernameSpan"></span>
        </span>
    </div> 

    <div class="rs-field" style="display:inline-block; width: auto !important;" >
        <button id="ssoBackToSessionBtn" role="button" type="submit" class="btn btn-default">
            Back to <span class="ssoUsername usernameSpan"></span> Workspace
        </button>
     </div>
    <div class="rs-field" style="display:inline-block; width: auto !important;" >
        <button id="ssoLogoutBtn" role="button" type="submit" class="btn btn-default">
            Logout from SSO session
        </button>
    </div>
</div>

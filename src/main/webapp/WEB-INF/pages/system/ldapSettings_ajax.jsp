<%@ include file="/common/taglibs.jsp"%>

<script>
    var ldapAuthenticationEnabled = "${ldapAuthenticationEnabled}" === "true";
    var ldapSidVerificationEnabled = "${ldapSidVerificationEnabled}" === "true";
</script>

<div class="bootstrap-custom-flat">

  <div id="ldapConfigPanel">
    <div id="ldapEnabledMsg">
        <span><spring:message code="system.ldap.integrationEnabledMessage"/></span>
    </div>
    <div id="ldapAuthenticationEnabledMsg" style="display:none">
        <span id="ldapAuthenticationEnabledMsg"><spring:message code="system.ldap.authenticationEnabledMessage"/></span>
    </div>
    <div id="ldapSidVerificationEnabledMsg" style="display:none">
        <span><spring:message code="system.ldap.sidVerificationEnabledMessage"/></span>
    </div>
  </div>

  <div id="sidRetrievalPanel" style="display:none">
    <hr />
    <button id="runSidRetrieval" class="btn btn-default" style="width:20em;">
      <span class="ui-button-text"><spring:message code="system.ldap.button.runSidRetrievalLabel" /></span>
    </button>
    <button id="stopSidRetrieval" class="btn btn-default" style="width:10em;">
      <span class="ui-button-text"><spring:message code="system.ldap.button.runSidRetrievalStop" /></span>
    </button>

    <br />
    <br />
    <div id="sidRetrievalResults" />
  </div>

</div>


<%@ include file="/common/taglibs.jsp"%>

<script>
    var ldapAuthenticationEnabled = "${ldapAuthenticationEnabled}" === "true";
    var ldapSidVerificationEnabled = "${ldapSidVerificationEnabled}" === "true";
</script>

<div class="bootstrap-custom-flat">
    
  <div id="ldapConfigPanel">
    <div id="ldapEnabledMsg">
        <span>LDAP integration is enabled.</span>
    </div>
    <div id="ldapAuthenticationEnabledMsg" style="display:none">
        <span id="ldapAuthenticationEnabledMsg">LDAP authentication is enabled.</span>
    </div>
    <div id="ldapSidVerificationEnabledMsg" style="display:none">
        <span>SID Verification is enabled.</span>
    </div>
  </div>

  <div id="sidRetrievalPanel" style="display:none">
    <hr />
    <button id="runSidRetrieval" class="btn btn-default" style="width:20em;">
      <span class="ui-button-text"><spring:message code="system.ldap.button.run.sid.retrieval" /></span>
    </button>
    <button id="stopSidRetrieval" class="btn btn-default" style="width:10em;">
      <span class="ui-button-text"><spring:message code="system.ldap.button.run.sid.retrieval.stop" /></span>
    </button>
  
    <br />
    <br />
    <div id="sidRetrievalResults" />
  </div>

</div>


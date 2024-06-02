<%@ include file="/common/taglibs.jsp"%>

<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <meta charset="UTF-8">
  <title>
    <spring:message code="system.createAccount.button.label" />
  </title>

  <link rel="stylesheet" href="<c:url value='/styles/system.css'/>" />
  <script src="<c:url value='/scripts/pages/system/system.js'/>"></script>
  <script src="<c:url value='/scripts/pages/system/createAccount.js'/>"></script>
  <script src="<c:url value='/scripts/pages/system/batchUserRegistration.js'/>"></script>

  <rst:hasDeploymentProperty name="ldapEnabled" value="true">
    <script type="text/javascript">
      var ldapEnabled = true;
    </script>
  </rst:hasDeploymentProperty>
</head>

<div id="topSection" class="bootstrap-custom-flat">
  <jsp:include page="topBar.jsp"></jsp:include>
</div>

<div id="contentCreateAccount">
  <br>
  <br>
  <h2 ><spring:message code="system.createAccount.pageHeader" /></h2>
  <br>
	<br>
  <div id="pageTopButtons">
    <div id="selectAccountToCreateSection">
      <a href="#" class="pageTopButton" id="newUserAccountButton"
        style="padding:11px 7px 9px 55px;background-image:url('/images/icons/addUserIcon.png');">
        <spring:message code="system.createAccount.newUser.label" /></a>
      <a href="#" class="pageTopButton" id="newPiAccountButton"
        style="padding:11px 7px 9px 61px;background-image:url('/images/icons/addPiIcon.png');">
        <spring:message code="system.createAccount.newPi.label" /></a>
      <a href="#" class="pageTopButton" id="newAdminAccountButton"
        style="padding:11px 7px 9px 62px;background-image:url('/images/icons/addRSpaceAdminIcon.png');">
        <spring:message code="system.createAccount.newAdmin.label" /></a>
      <shiro:hasRole name="ROLE_SYSADMIN">
        <a href="#" class="pageTopButton" id="newSysAdminAccountButton"
          style="padding:11px 7px 9px 59px;background-image:url('/images/icons/addSysAdminIcon.png');">
          <spring:message code="system.createAccount.newSysAdmin.label" /></a>
      </shiro:hasRole>
    </div>
    <shiro:hasRole name="ROLE_SYSADMIN">
      <rst:hasDeploymentProperty name="ldapAuthenticationEnabled" value="false">
        <div id="toAmend"><a class="pageTopButton" id="batchUserRegistrationButton" href="#"
            style="padding:11px 7px 9px 73px;background-image:url('/images/icons/batchUploadIcon.png');">
            <spring:message code="system.batchRegistration.button.label" /></a></div>
      </rst:hasDeploymentProperty>
    </shiro:hasRole>
  </div>
  <br>
  <div id="buttonDescriptions">
    <table width="100%">
      <tr>
        <td width="150px" class="likeHeaders"><span style="font-size: 1.3em;line-height:1.3em;">
            <spring:message code="system.createAccount.newUser.label" /></span></td>
        <td>
          <spring:message code="system.createAccount.newUser.description" />
        </td>
      </tr>
      <tr>
        <td width="150px" class="likeHeaders"><span style="font-size: 1.3em;line-height:1.3em;">
            <spring:message code="system.createAccount.newPi.label" /></span></td>
        <td>
          <spring:message code="system.createAccount.newPi.description" />
        </td>
      </tr>
      <tr>
        <td width="150px" class="likeHeaders"><span style="font-size: 1.3em;line-height:1.3em;">
            <spring:message code="system.createAccount.newAdmin.label" /></span></td>
        <td>
          <spring:message code="system.createAccount.newAdmin.description" />
        </td>
      </tr>
      <shiro:hasRole name="ROLE_SYSADMIN">
        <tr>
          <td width="150px" class="likeHeaders"><span style="font-size: 1.3em;line-height:1.3em;">
              <spring:message code="system.createAccount.newSysAdmin.label" /></span></td>
          <td>
            <spring:message code="system.createAccount.newSysAdmin.description" />
          </td>
        </tr>
      </shiro:hasRole>
    </table>
  </div>
</div>

<%-- csv upload dialog initialised in createAccount.js --%>
<div id="batchUploadUserDlg" style="display: none; margin:10px">
  <rst:hasDeploymentProperty name="cloud" value="true">
    <fmt:message key="system.batchRegistration.upload.helpText.cloud" />
  </rst:hasDeploymentProperty>
  <rst:hasDeploymentProperty name="cloud" value="false">
    <rst:hasDeploymentProperty name="standalone" value="true">
      <fmt:message key="system.batchRegistration.upload.helpText.standalone" />
    </rst:hasDeploymentProperty>
    <rst:hasDeploymentProperty name="standalone" value="false">
      <fmt:message key="system.batchRegistration.upload.helpText.sso" />
    </rst:hasDeploymentProperty>
  </rst:hasDeploymentProperty>

  <br /><br />
  <fmt:message key="system.batchRegistration.helpText.exampleCSV.intro" />
  <a href="/resources/batch_registration_example.csv" style="outline:none; color: #1465b7">
    <fmt:message key="system.batchRegistration.helpText.exampleCSV.label" /></a>.

  <rst:hasDeploymentProperty name="ldapEnabled" value="true">
    <br /><br />
    <fmt:message key="system.batchRegistration.helpText.ldap.csv" />
  </rst:hasDeploymentProperty>

  <form id="csvUploadForm" style="margin-top: 10px;">
    <input id="csvFileInput" type="file" name="xfile" />
  </form>
  <div id="csvUploadProgress" style="text-align: center; margin-top:5px"></div>
</div>

<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="unknownUser.title"/></title>
</head>

<c:set var="showDefaultSSOInfo" value="true"/>

<rst:hasDeploymentProperty name="SSOInfoVariant" value="liege">
  <div class="container" style="max-width:960px;padding:0 5% 0 5%;">
    <div class="row">
        <axt:biggerLogo/>
        <div style="text-align:center; margin-top:46px;">
         <h3 class="form-signup-heading"><spring:message code="ssoinfo.liege.heading"/></h3>
          </div>
      </div>

      <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
          <spring:message code="ssoinfo.liege.unavailableNotice"/> <br />
          <spring:message code="ssoinfo.liege.contactPrompt"/>
      </div>
  </div>
  <c:set var="showDefaultSSOInfo" value="false"/>
</rst:hasDeploymentProperty>

<c:if test="${showDefaultSSOInfo}">
  <div class="container" style="max-width:960px;padding:0 5% 0 5%;">
  	<div class="row">
      	<axt:biggerLogo/>
      	<div style="text-align:center; margin-top:46px;">
      	 <h3 class="form-signup-heading"><spring:message code="unknownUser.heading"/></h3>
          </div>
      </div>

      <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
          <spring:message code="unknownUser.notRegisteredNotice" arguments="${sessionScope.userName}"/> <br />
          <spring:message code="ssoinfo.default.contactSupportPrompt" arguments="${applicationScope['RS_DEPLOY_PROPS']['SSOAdminEmail']}"/>
      </div>
  </div>
</c:if>


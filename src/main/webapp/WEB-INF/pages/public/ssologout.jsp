
<%@ include file="/common/taglibs.jsp"%>
<head>
    <title><spring:message code="ssologout.title"/></title>
</head>

<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
	<div class="row">
	<axt:biggerLogo/>
	<div style="text-align:center; margin-top:46px;">
	    <h2 class="form-signup-heading"><spring:message code="ssologout.heading"/></h2>
        </div>
    </div>
    <div style="max-width:550px;margin: 0 auto;margin-top:30px;text-align:center;">
		<spring:message code="ssologout.stillLoggedInNotice"/>

		<br />
		<c:set var="ssoIdpLogoutUrl" value="${applicationScope['RS_DEPLOY_PROPS']['SSOIdpLogoutUrl']}"/>
		<c:if test="${not empty ssoIdpLogoutUrl}">
			<spring:message code="ssologout.clickHereToLogOut" arguments="${ssoIdpLogoutUrl}"/>
		</c:if>
		<c:if test="${empty ssoIdpLogoutUrl}">
			<spring:message code="ssologout.pleaseLogOutReminder"/>
		</c:if>
	</div>
</div>
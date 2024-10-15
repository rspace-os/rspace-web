
<%@ include file="/common/taglibs.jsp"%>
<head>
    <title>Logged out</title>
</head>

<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
	<div class="row">
    	<axt:biggerLogo/>
    	<div style="text-align:center; margin-top:46px;">
    	    <h2 class="form-signup-heading">Logged out of RSpace</h2>
        </div>
    </div>
    <div style="max-width:550px;margin: 0 auto;margin-top:30px;text-align:center;">
		You are <b>still logged</b> into your <b>institutional Single Sign-On</b> account.

		<br />
		<c:set var="ssoIdpLogoutUrl" value="${applicationScope['RS_DEPLOY_PROPS']['SSOIdpLogoutUrl']}"/>
		<c:if test="${not empty ssoIdpLogoutUrl}">
			You can <a href="${ssoIdpLogoutUrl}">click here</a> to fully log out.
		</c:if>
		<c:if test="${empty ssoIdpLogoutUrl}">
			Please don't forget to fully log out.
		</c:if>
	</div>
</div>
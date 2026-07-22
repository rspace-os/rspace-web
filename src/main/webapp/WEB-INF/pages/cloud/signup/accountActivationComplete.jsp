<%@ include file="/common/taglibs.jsp"%>

<head>
<title><spring:message code="signup.community.activation.completeTitle"/></title>
</head>
<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
	<div class="row">
       <axt:biggerLogo/>
       <div style="text-align:center; margin-top:46px;">
	       <h2 class="form-signup-heading"><spring:message code="signup.community.activation.completeStatusHeading"/>!</h2>
       </div>
    </div>
    <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
		<spring:message code="signup.community.activation.completeHeading"/>!
		 <br/><spring:message code="signup.community.activation.loginPromptPrefix"/>&nbsp;
		 <a href="/workspace"><spring:message code="signup.community.activation.loginPromptLink"/></a>
	</div>
</div>
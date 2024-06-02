<%@ include file="/common/taglibs.jsp"%>
<head>
<title><spring:message code="signup.community.confirm.title"/></title>
</head>
<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
	<div class="row">
    	<axt:biggerLogo/>
    	<div style="text-align:center; margin-top:46px;">
    	 <h2 class="form-signup-heading"><spring:message code="signup.community.confirm.hdr"/></h2>
        </div>
    </div>
    <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
		<spring:message code="signup.community.confirm.text1"/>. <br/> <br/>
    	<spring:message code="signup.community.confirm.text2"/>
    	<form method="POST" action="/cloud/resendConfirmationEmail/resend" style="margin-top: 2em;">
            <input type="hidden" name="email" value="${email}">
            <button id="resend-email" role="button" type="submit" class="btn btn-primary rs-field__button">
                <spring:message code="resendEmail.awaitingEmailConfirmation.resendButtonText" />
            </button>
        </form>
    </div>
</div>
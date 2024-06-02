<%@ include file="/common/taglibs.jsp"%>

<head>
<title><spring:message code="signup.community.activation.fail.title"/></title>
</head>
<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
	<div class="row">
    	<axt:biggerLogo/>
    	<div style="text-align:center; margin-top:46px;">
    	 <h2 class="form-signup-heading"><spring:message code="signup.community.activation.fail.hdr"/></h2>
        </div>
    </div>
    <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
		<spring:message code="token.verification.fail.help1"/> <br />
	    <spring:message code="token.verification.fail.help2"/>
    </div>
</div>
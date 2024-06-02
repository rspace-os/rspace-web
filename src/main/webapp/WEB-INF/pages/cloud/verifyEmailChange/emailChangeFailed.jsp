<%@ include file="/common/taglibs.jsp"%>

<head>
<title>Email Change Failed</title>
</head>
<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
	<div class="row">
    	<axt:biggerLogo/>
    	<div style="text-align:center; margin-top:46px;">
    	 <h2 class="form-signup-heading">There is some problem with verification link</h2>
        </div>
    </div>
    <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
		<spring:message code="token.verification.fail.help1"/> <br />
    	<c:if test="${fn:length(errorMsg) > 0}">
			${errorMsg} <br />
		</c:if>
    </div>
</div>
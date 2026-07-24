<%@ include file="/common/taglibs.jsp"%>

<head>
<title><spring:message code="token.verification.email.changeFailTitle"/></title>
</head>
<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
	<div class="row">
	<axt:biggerLogo/>
	<div style="text-align:center; margin-top:46px;">
	 <h2 class="form-signup-heading"><spring:message code="token.verification.email.changeFailHeading"/></h2>
        </div>
    </div>
    <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
		<spring:message code="token.verification.fail.rejectedLink"/> <br />
	<c:if test="${fn:length(errorMsg) > 0}">
			<spring:message code="${errorMsg}"/> <br />
		</c:if>
    </div>
</div>

<%@ include file="/common/taglibs.jsp"%>
<head>
<title><spring:message code="verifyEmailChange.title"/></title>
</head>
<div class="container" style="max-width: 960px; padding: 0 5% 0 5%;">
	<div class="row">
	<axt:biggerLogo/>
	<div style="text-align:center; margin-top:46px;">
	   <h2 class="form-signup-heading"><spring:message code="verifyEmailChange.heading" arguments="${emailChangeToken.email}"/></h2>
        </div>
    </div>
	<form class="form-signup" method="POST" action="/cloud/verifyEmailChange">
		<fieldset>
			<input type="hidden" name="token" id="token" class="form-control" value="${emailChangeToken.token}">
			<button class="btn btn-lg btn-primary btn-block" type="submit"
				role="button" aria-disabled="false" name="submit" value="Reset">
				<span class="ui-button-text"><spring:message code="verifyEmailChange.submitButton"/></span>
			</button>
		</fieldset>
	</form>
</div>
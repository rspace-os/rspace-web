<%@ include file="/common/taglibs.jsp"%>
<head>
<title><spring:message code="signup.community.finalsignup.title"/></title>
</head>
<div class="container" style="max-width: 960px; padding: 0 5% 0 5%;">
	<div class="row">
    	<axt:biggerLogo/>
    	<div style="text-align:center; margin-top:46px;">
    	   <h2 class="form-signup-heading"><spring:message code="signup.community.finalsignup.label"/>...</h2>
        </div>
    </div>
	<form class="form-signup" method="POST" action="/cloud/verifysignup">
		<fieldset>
			<input type="hidden" name="token" id="token" class="form-control" value="${token}">
			<button class="btn btn-lg btn-primary btn-block" type="submit"
				role="button" aria-disabled="false" name="submit" value="Reset">
				<span class="ui-button-text"><spring:message code="signup.community.finalsignup.title"/>!</span>
			</button>
		</fieldset>
	</form>
</div>
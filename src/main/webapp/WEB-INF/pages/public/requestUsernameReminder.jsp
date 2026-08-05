<%@ include file="/common/taglibs.jsp" %>

<head>
    <title><spring:message code="requestUsernameReminder.title"/></title>
    <link href="<rst:assetUrl value='/styles/pages/public/passwordReset.css'/>" rel="stylesheet">
</head>

<div class="container passwordResetContainer">
    <div class="row">
        <axt:biggerLogo/>
        <div class="passwordResetInstructionDiv">
            <h2 class="form-signup-heading"><spring:message code="requestUsernameReminder.heading"/></h2>
            <p><spring:message code="requestUsernameReminder.instructions"/></p>
        </div>
    </div>
    <form class="form-signup" method="POST" action="/signup/usernameReminderRequest">
        <fieldset>
            <input type="text" name="email" id="email" class="form-control"
                   placeholder="<spring:message code='form.emailAddressPlaceholder'/>"
                   required="true" autofocus="true" pattern="[^ @]*@[^ @]*"
                   title="<spring:message code='form.emailAddressTitle'/>">
            <button class="btn btn-lg btn-primary btn-block" type="submit"
                    role="button" aria-disabled="false" name="submit" value="Reset">
                <span class="ui-button-text"><spring:message code="common:actions.submit"/></span>
            </button>
        </fieldset>
    </form>
</div>
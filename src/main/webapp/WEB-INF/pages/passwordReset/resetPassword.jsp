<%@ include file="/common/taglibs.jsp" %>
<%@ page import="com.researchspace.webapp.controller.PasswordType" %>

<head>
    <title>Reset ${passwordType.toString()}</title>
</head>
<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
    <div class="row">
        <axt:biggerLogo/>
        <div style="text-align:center; margin-top:46px;">
            <h2 class="form-signup-heading"><spring:message code="resetPassword.changeHeading" arguments="${passwordType.toString()}"/></h2>
        </div>
    </div>
    <spring:message code="resetPassword.passwordCharsTitle" var="resetPasswordCharsTitle" htmlEscape="true"/>
    <div style="max-width:450px; margin: 30px auto 0; display: flex; flex-direction: column;">
        <p><spring:message code="resetPassword.enterNewPasswordPrompt" arguments="${passwordType.toString()}"/></p>
        <form:form action="${(passwordType == PasswordType.VERIFICATION_PASSWORD) ?
        '/vfpwd/verificationPasswordResetReply' : '/signup/passwordResetReply'}"
                   modelAttribute="passwordResetCommand">
            <form:errors class="rs-tooltip error" path="password"></form:errors>
            <div>
                <label for="password"><spring:message code="resetPassword.newPasswordLabel" arguments="${passwordType.toString()}"/></label>
                <form:password path="password" pattern="[ -~]{8,50}" style="width: 100%" title="${resetPasswordCharsTitle}" />
                <p class="form-text"><spring:message code="resetPassword.passwordCharsHint"/></p>
                <label for="confirmPassword"><spring:message code="resetPassword.confirmPasswordLabel" arguments="${passwordType.toString()}"/></label>
                <form:password path="confirmPassword" style="width: 100%" pattern="[ -~]{8,50}" />
                <form:hidden path="token"/>
            </div>
            <div class="bootstrap-custom-flat" style="margin-top: 10px;">
                <button class="btn btn-primary" type="submit" role="button"
                        aria-disabled="false" name="reset" value="Reset">
                 <span><spring:message code="resetPassword.submitButton"/></span>
               </button>
             </div>
        </form:form>
    </div>
</div>
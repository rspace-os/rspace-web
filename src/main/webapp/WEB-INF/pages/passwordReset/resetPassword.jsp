<%@ include file="/common/taglibs.jsp" %>
<%@ page import="com.researchspace.webapp.controller.PasswordType" %>

<head>
    <title>Reset ${passwordType.toString()}</title>
</head>
<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
    <div class="row">
        <axt:biggerLogo/>
        <div style="text-align:center; margin-top:46px;">
            <h2 class="form-signup-heading">Change ${passwordType.toString()}</h2>
        </div>
    </div>
    <div style="max-width:450px; margin: 30px auto 0; display: flex; flex-direction: column;">
        <p>Please enter your new ${passwordType.toString()}:</p>
        <form:form action="${(passwordType == PasswordType.VERIFICATION_PASSWORD) ?
        '/vfpwd/verificationPasswordResetReply' : '/signup/passwordResetReply'}"
                   modelAttribute="passwordResetCommand">
            <form:errors class="rs-tooltip error" path="password"></form:errors>
            <div>
                <label for="password">New ${passwordType.toString()}</label>
                <form:password path="password" pattern="[ -~]{8,50}" style="width: 100%" title="8 - 50 characters. Numbers, letters, spaces and these special characters are allowed: !\"#$%&'()*+,-./:;<=>?@[\]^_`{|}~" />
                <p class="form-text">8 - 50 characters. Numbers, letters, spaces and special characters are allowed.</p>
                <label for="confirmPassword">Confirm ${passwordType.toString()}</label>
                <form:password path="confirmPassword" style="width: 100%" pattern="[ -~]{8,50}" />
                <form:hidden path="token"/>
            </div>
            <div class="bootstrap-custom-flat" style="margin-top: 10px;">
                <button class="btn btn-primary" type="submit" role="button"
                        aria-disabled="false" name="reset" value="Reset">
                 <span>Reset</span>
               </button>
             </div>
        </form:form>
    </div>
</div>
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
    <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
        Please enter your new ${passwordType.toString()}:
        <br/><br/>
        <form:form action="${(passwordType == PasswordType.VERIFICATION_PASSWORD) ?
        '/vfpwd/verificationPasswordResetReply' : '/signup/passwordResetReply'}"
                   modelAttribute="passwordResetCommand">
            <form:errors class="rs-tooltip error" path="password"></form:errors>
            <div style="text-align:right;padding-right:100px;">
                <label for="password">New ${passwordType.toString()}</label>
                <form:password path="password"/>
                <br/>
                <label for="confirmPassword">Confirm ${passwordType.toString()}</label>
                <form:password path="confirmPassword"/>
                <form:hidden path="token"/>
            </div>
            <br/>
            <span class="bootstrap-custom-flat">
                <button class="btn btn-primary" type="submit" role="button"
                        aria-disabled="false" name="reset" value="Reset">
                 <span>Reset</span>
               </button>
             </span>
        </form:form>
    </div>
</div>
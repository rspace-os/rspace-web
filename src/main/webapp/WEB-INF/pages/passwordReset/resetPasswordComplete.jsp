<%@ include file="/common/taglibs.jsp" %>
<%@ page import="com.researchspace.webapp.controller.PasswordType" %>

<head>
    <title>${passwordType.capitalise()} reset</title>
</head>

<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
    <div class="row">
        <axt:biggerLogo/>
        <div style="text-align:center; margin-top:46px;">
            <h2 class="form-signup-heading">${passwordType.capitalise()} changed</h2>
        </div>
    </div>
    <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
        Thank you, your ${passwordType.toString()} is successfully changed.
        <br/><br/>
        <c:choose>
            <c:when test="${passwordType == PasswordType.LOGIN_PASSWORD}">
                You can now <a href="/workspace">login</a> with your new password.
            </c:when>
            <c:when test="${passwordType == PasswordType.VERIFICATION_PASSWORD}">
                You can now use your new verification password in <a href="/workspace">RSpace</a>.
            </c:when>
        </c:choose>
    </div>
</div>

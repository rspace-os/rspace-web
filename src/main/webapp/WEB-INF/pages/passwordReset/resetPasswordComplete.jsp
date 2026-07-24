<%@ include file="/common/taglibs.jsp" %>
<%@ page import="com.researchspace.webapp.controller.PasswordType" %>
<%--@elvariable id="passwordType" type="com.researchspace.webapp.controller.PasswordType"--%>

<head>
    <title>${passwordType.capitalise()} reset</title>
</head>

<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
    <div class="row">
        <axt:biggerLogo/>
        <div style="text-align:center; margin-top:46px;">
            <h2 class="form-signup-heading"><spring:message code="resetPasswordComplete.title" arguments="${passwordType.capitalise()}"/></h2>
        </div>
    </div>
    <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
        <spring:message code="resetPasswordComplete.successNotice" arguments="${passwordType.toString()}"/>
        <br/><br/>
        <c:choose>
            <c:when test="${passwordType == PasswordType.LOGIN_PASSWORD}">
                <spring:message code="resetPasswordComplete.loginPasswordNotice"/>
            </c:when>
            <c:when test="${passwordType == PasswordType.VERIFICATION_PASSWORD}">
                <spring:message code="resetPasswordComplete.verificationPasswordNotice"/>
            </c:when>
        </c:choose>
    </div>
</div>

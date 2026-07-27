<%@ include file="/common/taglibs.jsp" %>
<head>
    <title><spring:message code="resetPasswordRequestSent.title"/></title>
    <link href="<rst:assetUrl value='/styles/pages/public/passwordReset.css'/>" rel="stylesheet">
</head>

<div class="container passwordResetContainer">
    <div class="row">
        <axt:biggerLogo/>
        <div class="passwordResetSentDiv">
            <h2 class="form-signup-heading"><spring:message code="verificationEmail.checkItsYouHeading"/></h2>
            <br/>
            <spring:message code="resetPasswordRequestSent.sentNotice" arguments="${email}"/>
            <br/>
            <spring:message code="resetPasswordRequestSent.instructions"/>
            <br/><br/>
            <spring:message code="verificationEmail.thankYou"/>
        </div>
    </div>
</div>
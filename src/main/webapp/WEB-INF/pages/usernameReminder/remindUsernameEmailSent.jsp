<%@ include file="/common/taglibs.jsp" %>
<head>
    <title><spring:message code="remindUsernameEmailSent.title"/></title>
    <link href="<rst:assetUrl value='/styles/pages/public/passwordReset.css'/>" rel="stylesheet">
</head>

<div class="container passwordResetContainer">
    <div class="row">
        <axt:biggerLogo/>
        <div class="passwordResetSentDiv">
            <h2 class="form-signup-heading"><spring:message code="verificationEmail.checkItsYouHeading"/></h2>
            <br/>
            <spring:message code="remindUsernameEmailSent.sentNotice" arguments="${email}"/>
            <br/>
            <spring:message code="verificationEmail.thankYou"/>
        </div>
    </div>
</div>
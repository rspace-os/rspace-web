<%@ include file="/common/taglibs.jsp"%>

<head>
    <spring:message code="publishIsDisabled.heading" var="publishIsDisabledHeading"/>
    <title>${publishIsDisabledHeading}</title>
    <meta name="robots" content="noindex, nofollow, noarchive">
</head>

<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
    <div class="row">
        <div class="biggerLogoDiv" style="text-align: center">
            <img src="/public/images/mainLogo3.png" alt="<spring:message code='logo.alt'/>">
        </div>
        <div style="text-align:center; margin-top:46px;">
            <h3 class="form-signup-heading">${publishIsDisabledHeading}</h3>
        </div>
    </div>

    <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
        <spring:message code="publishIsDisabled.disabledNotice"/>
    </div>
</div>
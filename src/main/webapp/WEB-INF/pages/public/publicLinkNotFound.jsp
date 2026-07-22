<%@ include file="/common/taglibs.jsp" %>

<head>
    <spring:message code="publicLinkNotFound.title" var="publicLinkNotFoundTitle"/>
    <title>${publicLinkNotFoundTitle}</title>
    <meta name="heading" content="${publicLinkNotFoundTitle}"/>
    <meta name="robots" content="noindex, nofollow, noarchive">
    <style>
        .errorBlock {
            width: 449px;
            padding: 15px;
            font-size: 20px;
            line-height: 18px;
        }
    </style>
</head>
<div class="container">
    <div class="row">
        <div class="biggerLogoDiv" style="text-align: center">
            <img src="/public/images/mainLogo3.png" alt="<spring:message code='logo.alt'/>">
        </div>
        <div>
            <h1 class="form-signup-heading" style="text-align: center"><spring:message code="publicLinkNotFound.heading"/></h1>
        </div>
        <spring:message code="publicLinkNotFound.wrongLinkNotice" var="publicLinkNotFoundWrongLinkNotice"/>
        <div style="text-align: center">
            <img src="<c:url value="/public/images/404.jpg"/>"
                 alt=" ${publicLinkNotFoundWrongLinkNotice}"/>
        </div>

        <div style="text-align: center;padding:25px;width:700px;margin:0px auto;"><h4>
            ${publicLinkNotFoundWrongLinkNotice}</h4> <H4><spring:message code="publicLinkNotFound.contactSourcePrompt"/></H4>
        </div>
    </div>
</div>

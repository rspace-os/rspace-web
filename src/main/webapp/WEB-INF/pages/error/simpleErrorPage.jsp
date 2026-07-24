<%@ include file="/common/taglibs.jsp"%>
<%-- Shared error page. Params: titleKey, messageKey, linkUrl. Used by /403.jsp and /404.jsp. --%>
<!DOCTYPE html>
<html>
<head>
    <title><spring:message code="${param.titleKey}"/></title>
    <meta name="heading" content="<spring:message code='${param.titleKey}'/>"/>
    <style>
    	.errorBlock {
    		max-width: 449px;
    		padding: 15px;
    		margin: 0 auto;
    		font-size: 14px;
    		line-height: 18px;
    		text-align: center;
    	}
    	.errorBlock img {
    		max-width: 200px;
    	}
    	.errorBlock h2 {
    		color: #333;
    		margin: 15px 0 10px;
    	}
    </style>
</head>
<body>
<div id="errorContainer" style="margin-top: 20px;">
<div class="errorBlock">
    <img src="<c:url value="/images/mainLogo3.svg"/>" alt="<spring:message code='webapp.name'/>" />
    <h2><spring:message code="${param.titleKey}"/></h2>
</div>
<div class="errorBlock">
    <c:url value="${param.linkUrl}" var="linkUrl"/>
    <spring:message code="${param.messageKey}" arguments="${linkUrl}"/>
</div>
</div>
</body>
</html>

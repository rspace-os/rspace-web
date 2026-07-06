<%@ include file="/common/taglibs.jsp"%>
<%-- Shared error page. Params: titleKey, messageKey, linkUrl. Used by /403.jsp and /404.jsp. --%>
<!DOCTYPE html>
<html>
<head>
    <title><fmt:message key="${param.titleKey}"/></title>
    <meta name="heading" content="<fmt:message key='${param.titleKey}'/>"/>
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
    <img src="<c:url value="/images/mainLogo3.svg"/>" alt="RSpace" />
    <h2><fmt:message key="${param.titleKey}"/></h2>
</div>
<div class="errorBlock">
    <fmt:message key="${param.messageKey}">
        <fmt:param><c:url value="${param.linkUrl}"/></fmt:param>
    </fmt:message>
</div>
</div>
</body>
</html>

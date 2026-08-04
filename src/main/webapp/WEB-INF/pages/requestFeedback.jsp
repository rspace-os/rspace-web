<%@ include file="/common/taglibs.jsp"%>

<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta charset="UTF-8">
    <title><spring:message code="requestFeedback.title"/></title>
</head>
<body>
<p style="font-size:18px">
    <c:choose>
        <c:when test="${requestStatus == 'declined'}">
            <spring:message code="requestFeedback.notJoiningGroupNotice">
                <spring:argument value="${comm.group.displayName}"/>
            </spring:message>
        </c:when>
        <c:otherwise>
            <spring:message code="requestFeedback.memberOfGroupNotice">
                <spring:argument value="${comm.group.displayName}"/>
            </spring:message>
        </c:otherwise>
    </c:choose>
    <img height="20" src="/images/tick-icon.png">
</p>
</p>
<br/><br/>
<a href="../workspace"> <spring:message code="requestFeedback.returnToWorkspaceLink"/> </a>
</body>

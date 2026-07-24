<%@ include file="/common/taglibs.jsp"%>
<head>
	<title><spring:message code="connect.authorizationError.title"/></title>
</head>

<body>
    <p>
        <spring:message code="connect.authorizationError.notAuthorizedNotice"><spring:argument value="${error.appName}"/><spring:argument value="${error.errorMsg}"/></spring:message><br/>

        <spring:message code="connect.authorizationError.retryPrompt"/>
    </p>
    <c:if test="${not empty  error.errorDetails}">
      <h4> <spring:message code="connect.authorizationError.detailsHeading"/></h4>
      <p> ${error.errorDetails}</p>
    </c:if>


    <p>
        <spring:message code="connect.authorizationError.closeWindowNotice"/>
    </p>
</body>
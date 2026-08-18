<%@ include file="/common/taglibs.jsp" %>

  <span class="version">
    ${applicationScope['RS_DEPLOY_PROPS']['webappVersion']}
  </span>
  <span>
      <c:forEach items="${applicationScope['RS_DEPLOY_PROPS']['uiFooterUrls']}" var="url" varStatus="loopStatus">
        <a href="${url.value}">${url.key}</a>
        <c:if test="${!loopStatus.last}">|</c:if>
      </c:forEach>
  </span>
  <span>
    <a href="<spring:message code='company.url'/>" target="_blank">
      <img src="<c:url value='/images/mainLogo3.svg'/>" style="vertical-align:middle; margin-top:-4px; width: 80px;"
        alt="RSpace" />
    </a>
    &copy;&nbsp;
    ${applicationScope['RS_DEPLOY_PROPS']['copyrightYear']}&nbsp;<a href="<spring:message code='company.url'/>" target="_blank">
      <spring:message code="company.name"/>
    </a>
  </span>
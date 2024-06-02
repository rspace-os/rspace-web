<%@ include file="/common/taglibs.jsp" %>

  <span class="version">
    <fmt:message key="webapp.version" />
  </span>
  <span>
      <c:forEach items="${applicationScope['RS_DEPLOY_PROPS']['uiFooterUrls']}" var="url" varStatus="loopStatus">
        <a href="${url.value}">${url.key}</a>
        <c:if test="${!loopStatus.last}">|</c:if>
      </c:forEach>
  </span>
  <span>
    <a href="<fmt:message key='company.url'/>" target="_blank">
      <img src="<c:url value='/images/footerLogoN.png'/>" style="vertical-align:middle; margin-top:-5px; width: 80px;"
        alt="RSpace" />
    </a>
    &copy;&nbsp;
    <fmt:message key="copyright.year" />&nbsp;<a href="<fmt:message key='company.url'/>" target="_blank">
      <fmt:message key="company.name" />
    </a>
  </span>
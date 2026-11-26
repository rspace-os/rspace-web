<%@ include file="/common/taglibs.jsp"%>

  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta charset="UTF-8">
    <title>
      <spring:message code="system.admin.pageTitle"/>
    </title>

    <!-- moved to default.jsp -->
    <!-- <axt:stylesheet href="/styles/bootstrap-custom-flat.css"/> -->

    <axt:stylesheet href="/styles/system.css"/>
    <script src="<rst:assetUrl value='/scripts/pages/system/system.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
  </head>

  <body>
    <div id="topSection" class="bootstrap-custom-flat">
      <jsp:include page="topBar.jsp">
        <jsp:param name="hasCommunity" value="${hasCommunity}" />
      </jsp:include>
    </div>

    <c:if test="${empty hasCommunity or hasCommunity eq 'true'}">
      <div class="tabularViewTop">
        <div id="sysadminUsers" style="width: 100%" /></div>
      </div>
    </c:if>

<div id="statusMsg" class="rs-error-text" style="margin-top:10px">
  <c:if test="${not empty errorList}">
    <spring:message code="general.operationFailed" /><br />

    <c:forEach items="${errorList.errorMessages}" var="err">
      <c:out value="${err}" /><br />
    </c:forEach>
  </c:if>
</div>

<!-- React Scripts -->
<div id="exportModal" style="display: inline-block;"></div>
<rst:bundle bundle="baseSearch" />
<rst:bundle bundle="sysadminUsers" />
<!--End React Scripts -->
</body>
</html>

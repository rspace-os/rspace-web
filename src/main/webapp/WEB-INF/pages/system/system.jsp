<%@ include file="/common/taglibs.jsp"%>
<fmt:bundle basename="bundles.system.system">

  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta charset="UTF-8">
    <title>
      <fmt:message key="system.admin.pageTitle" />
    </title>

    <!-- moved to default.jsp -->
    <!-- <link rel="stylesheet" href="<rst:assetUrl value='/styles/bootstrap-custom-flat.css'/>" /> -->

    <link rel="stylesheet" href="<rst:assetUrl value='/styles/system.css'/>" />
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
</fmt:bundle>

<div id="statusMsg" style="margin-top:10px;color:red">
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

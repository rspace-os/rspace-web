<%@ include file="/common/taglibs.jsp"%>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta charset="UTF-8">
<title><spring:message code="system.communityList.button.label"/></title>

<!-- moved to default.jsp -->
<!-- <link rel="stylesheet" href="<c:url value='/styles/bootstrap-custom-flat.css'/>" /> -->

<link rel="stylesheet" href="<c:url value='/styles/system.css'/>" />
<script src="<c:url value='/scripts/pages/system/system.js'/>"></script>
<script src="<c:url value='/scripts/pages/system/communityList.js'/>"></script>
</head>
<body>

<div id="topSection" class="bootstrap-custom-flat">
  <jsp:include page="topBar.jsp"></jsp:include>
</div>

<shiro:hasRole name="ROLE_SYSADMIN">
  <div class="crudopsTopPanel" style="margin-bottom: 10px">
    <ul>
      <a class="link" href="/system/createCommunity" id="createCommunityButton" >
        <span><spring:message code="system.createCommunity.button.label"/></span>
      </a>
      <a id="removeCommunity" class="link commcrudops" href="#">Remove community</a>
    </ul>
  </div>
</shiro:hasRole>

<div id="content">
  <c:choose>
    <c:when test="${empty communities.results}">
     <spring:message code="system.communityList.emptyMsg"/>
    </c:when>
    <c:otherwise>
      <div id="communityListContainer">
       <h2 id="directoryListTitle"> Communities </h2>
       <jsp:include page="communityList_ajax.jsp"></jsp:include>
      </div>
    </c:otherwise>
  </c:choose>
</div>
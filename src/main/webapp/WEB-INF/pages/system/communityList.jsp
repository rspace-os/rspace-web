<%@ include file="/common/taglibs.jsp"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta charset="UTF-8">
<title><spring:message code="system.communityList.button.label"/></title>

<!-- moved to default.jsp -->
<!-- <axt:stylesheet href="/styles/bootstrap-custom-flat.css"/> -->

<axt:stylesheet href="/styles/system.css"/>
<script src="<rst:assetUrl value='/scripts/pages/system/system.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/pages/system/communityList.js'/>"></script>
</head>
<body>

<div id="topSection" class="bootstrap-custom-flat">
  <jsp:include page="topBar.jsp"></jsp:include>
</div>

<shiro:hasRole name="ROLE_SYSADMIN">
  <div class="crudopsTopPanel" style="margin-bottom: 10px">
    <ul>
      <a class="link" href="/system/createCommunity" id="createCommunityButton" ><img class="rs-bg-icon rs-bg-icon--middle" src="/images/icons/createCommIcon.png" alt="" aria-hidden="true">
        <span><spring:message code="system.createCommunity.button.label"/></span>
      </a>
      <a id="removeCommunity" class="link commcrudops" href="#"><img class="rs-bg-icon rs-bg-icon--middle" src="/images/icons/removeCommIcon.png" alt="" aria-hidden="true"><spring:message code="system.communityList.removeCommunityLink"/></a>
    </ul>
  </div>
</shiro:hasRole>

<div id="content">
  <c:choose>
    <c:when test="${empty communities.results}">
     <spring:message code="system.communityList.empty"/>
    </c:when>
    <c:otherwise>
      <div id="communityListContainer">
       <h2 id="directoryListTitle"> <spring:message code="community.communities.title"/> </h2>
       <jsp:include page="communityList_ajax.jsp"></jsp:include>
      </div>
    </c:otherwise>
  </c:choose>
</div>
</body>
</html>
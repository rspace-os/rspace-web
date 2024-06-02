<%@ include file="/common/taglibs.jsp"%>

<head>
  <title>Lab Groups</title>
  <meta name="heading" content="<fmt:message key='groups.heading'/>" />
  <meta name="menu" content="MainMenu" />

  <!-- moved to default.jsp -->
  <!-- <link rel="stylesheet" href="<c:url value='/styles/bootstrap-custom-flat.css'/>" /> -->

  <link rel="stylesheet" href="<c:url value='/styles/system.css'/>" />
  <script src="<c:url value='/scripts/pages/system/groupList.js'/>"></script>
  <script src="<c:url value='/scripts/pages/utils/columnSortToggle.js'/>"></script>
</head>
<div id="topSection" class="bootstrap-custom-flat">
  <jsp:include page="/WEB-INF/pages/system/topBar.jsp"></jsp:include>
</div>
<shiro:hasAnyRoles name="ROLE_ADMIN,ROLE_SYSADMIN">
  <div class="crudopsTopPanel" style="margin-bottom: 10px;">
    <c:url value='/groups/admin/?new' var="newgroup" />
    <ul>
      <a id="createGroupButton" class="link" href="${newgroup}">
        <span>
          <spring:message code="system.createGroup.button.label" /></span>
      </a>
      <a id="showAll" class="link" href="/system/groups/list?sortOrder=ASC&orderBy=displayName&resultsPerPage=100000">
        <span>Show All</span>
      </a>
      <li id="deleteGroup" class="c_action">
        <a href="#">Delete group</a>
      </li>
      <li id="exportGroupRecord" class="c_action">
        <a href="#" class="exportGroup">
          <spring:message code="action.export" /></a>
      </li>
    </ul>
  </div>
</shiro:hasAnyRoles>

<div class="tabularViewTop">
  <h2 class="title">Groups</h2>
  <div
    class="base-search"
    data-variant="outlined"
    data-elid="searchGroupListInput"
    data-onsubmit="doSearch"
  ></div>
</div>
<div id="groupListContainer">
  <jsp:include page="group_list_ajax.jsp"></jsp:include>
</div>

<!-- React Scripts -->
<div id="exportModal" style="display: inline-block;"></div>
<script src="<c:url value='/ui/dist/exportModal.js'/>"></script>
<script src="<c:url value='/ui/dist/baseSearch.js'/>"></script>
<!--End React Scripts -->
<%@ include file="/common/taglibs.jsp"%>

<head>
  <title><spring:message code="system.groupList.pageTitle"/></title>
  <meta name="heading" content="<spring:message code='groups.heading'/>" />
  <meta name="menu" content="MainMenu" />

  <!-- moved to default.jsp -->
  <!-- <link rel="stylesheet" href="<rst:assetUrl value='/styles/bootstrap-custom-flat.css'/>" /> -->

  <link rel="stylesheet" href="<rst:assetUrl value='/styles/system.css'/>" />
  <script src="<rst:assetUrl value='/scripts/pages/system/groupList.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/utils/columnSortToggle.js'/>"></script>
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
        <span><spring:message code="system.groupList.showAllLink"/></span>
      </a>
      <li id="deleteGroup" class="c_action">
        <a href="#"><spring:message code="system.groupList.deleteLink"/></a>
      </li>
      <li id="exportGroupRecord" class="c_action">
        <a href="#" class="exportGroup">
          <spring:message code="common:actions.export" /></a>
      </li>
    </ul>
  </div>
</shiro:hasAnyRoles>

<div class="tabularViewTop">
  <h2 class="title"><spring:message code="system:usersPage.groupMembership.panelLabel"/></h2>
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
<rst:bundle bundle="exportModal" />
<rst:bundle bundle="baseSearch" />
<!--End React Scripts -->

<%@ include file="/common/taglibs.jsp"%>
<head>
	<title><spring:message code="directory.title"/></title>
	<script src="<c:url value='/scripts/pages/utils/columnSortToggle.js'/>"></script>   
</head>

<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />
<script type="text/javascript" src="<c:url value='/scripts/pages/rspace/directory.js'/>"></script>

<script type="text/javascript">
  var isCloud = false;
  <rst:hasDeploymentProperty name="cloud" value="true">
  var isCloud = true;
  </rst:hasDeploymentProperty>
</script>

<div id="selectDirectoryListSection" style="margin-top: 20px;"> 
	<a class="publicListButton" id="userListButton" style="padding:11px 7px 9px 67px;background-image:url('/images/icons/usersListIcon.png');" tabindex="0"> 
	 <spring:message code="directory.users.title"/>
  </a> 
	<a class="publicListButton" id="groupListButton" style="padding:11px 7px 9px 78px;background-image:url('/images/icons/groupsListIcon.png');" tabindex="0">
	  <spring:message code="userProfile.sectionLabel.labGroups"/>
  </a>
	<a class="publicListButton" id="communityListButton" style="padding:11px 7px 9px 83px;background-image:url('/images/icons/communitiesListIcon.png');" tabindex="0">
	 <spring:message code="community.communities.title"/>
  </a>
	<a class="publicListButton" id="projectGroupListButton" style="padding:11px 7px 9px 83px;background-image:url('/images/icons/projectGroupsListIcon.png');" tabindex="0">
	 <spring:message code="directory.projectgroups.title"/>
  </a>
</div>

<div class="tabularViewTop"> 
  <h2 class="title">Users</h2>
  <rst:hasDeploymentProperty name="cloud" value="true" match="false">
    <div 
      class="base-search" 
      data-elId="searchDirectoryListInput"
      data-onSubmit="handleSearchDirectoryQuery"
      data-variant="outlined"
    ></div>
  </rst:hasDeploymentProperty>
  <rst:hasDeploymentProperty name="cloud" value="true" match="true">
    <div 
      class="base-search"
      data-placeholder="By name, email or username"
      data-elId="searchDirectoryListInput" 
      data-onSubmit="handleSearchDirectoryQuery"
      data-variant="outlined"
    ></div>
  </rst:hasDeploymentProperty>
</div>

<div id="searchModePanel" style="display: none;">
  <span id="message"></span>
  <button id="resetSearch">Clear search</button>
</div>


<div id="directoryContainer" class="newTabularView bootstrap-custom-flat">
	<jsp:include page="user_list_ajax.jsp"></jsp:include>
</div>

<!-- Import React search -->
<script src="<c:url value='/ui/dist/baseSearch.js'/>"></script>

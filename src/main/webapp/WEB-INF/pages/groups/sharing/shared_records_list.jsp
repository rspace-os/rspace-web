<%@ include file="/common/taglibs.jsp"%>

<head>
    <c:choose>
        <c:when test="${publishedLinks_for_user_to_see || publishedLinks_for_sysadmin_to_manage}">
            <title>Published Documents</title>
        </c:when>
        <c:otherwise>
            <title><fmt:message key="groups.shared.title"/></title>
        </c:otherwise>
    </c:choose>
  <meta name="heading" content="<fmt:message key='groups.shared.title'/>"/>
  <meta name="menu" content="MainMenu"/>
  <link rel="stylesheet" media="all" href="<c:url value='/styles/simplicity/theme.css'/>" />
  <script src="<c:url value='/scripts/pages/rspace/sharedRecordsList.js'/>"></script>
	<script src="<c:url value='/scripts/pages/utils/columnSortToggle.js'/>"></script>   
</head>

<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />
<jsp:include page="/WEB-INF/pages/recordInfoPanel.jsp" />

<div class="tabularViewTop">
    <c:if test="${publishedLinks_for_sysadmin_to_manage}">
        <h2 class="title">Users' Published Documents</h2>
    </c:if>
    <c:if test="${publishedLinks_for_user_to_see &&! publishedLinks_for_sysadmin_to_manage}">
        <shiro:hasRole name="ROLE_PI">
            <h2 class="title">My Group's Published Documents</h2>
        </shiro:hasRole>
        <shiro:hasRole name="ROLE_USER">
            <shiro:lacksRole name="ROLE_PI">
                <h2 class="title">My Published Documents</h2>
            </shiro:lacksRole>
        </shiro:hasRole>
    </c:if>
    <c:if test="${!publishedLinks_for_user_to_see}">
        <h2 class="title">Shared Documents</h2>
    </c:if>
    <c:if test="${publishedLinks_for_user_to_see || publishedLinks_for_sysadmin_to_manage}">
    <div style="flex-grow:4;display: flex;margin: 10px 0 10px 0;" >
        <span class="req" >Copy all links on this page</span>
        <li alt="copy all links"  class="linkShare public-tooltip" id="copyAllLinks" style="display: contents;">
            <img src="/images/icons/copyIcon.png" style="width:35px;height:28px;"/>
            <div style="display:flex;" class="tooltiptext">Copy all links on this page</div>
            </li>
    </div>
    </c:if>
  <div
    class="base-search"
    data-placeholder="By document or user"
    data-onsubmit="handleSearchShared"
    data-variant="outlined"
    data-elid="searchSharedListInput"
  ></div>
</div>

<div id="searchModePanel" style="display: none;">
  <span id="message"></span>
  <button id="resetSearch">Clear search</button>
</div>

<div id="sharedRecordsListContainer" class="bootstrap-custom-flat newTabularView">
    <c:if test="${!publishedLinks_for_user_to_see}">
        <jsp:include page="shared_records_list_ajax.jsp" />
    </c:if>
    <c:if test="${publishedLinks_for_user_to_see}">
        <jsp:include page="published_records_list_ajax.jsp" />
    </c:if>
</div>

<rst:hasDeploymentProperty name="cloud" value="true">
	<h2> Shared Documents Requests</h2>
	<div>
	<jsp:include page="shared_record_requests_list_ajax.jsp" />
	</div>
</rst:hasDeploymentProperty>

<!-- Import React search -->
<script src="<c:url value='/ui/dist/baseSearch.js'/>"></script>
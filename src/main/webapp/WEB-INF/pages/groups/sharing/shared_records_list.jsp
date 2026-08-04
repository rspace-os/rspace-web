<%@ include file="/common/taglibs.jsp"%>

<head>
    <c:choose>
        <c:when test="${publishedLinks_for_user_to_see || publishedLinks_for_sysadmin_to_manage}">
            <title><spring:message code="groups.sharing.publishedDocumentsTitle"/></title>
        </c:when>
        <c:otherwise>
            <title><spring:message code="groups.shared.title"/></title>
        </c:otherwise>
    </c:choose>
  <meta name="heading" content="<spring:message code='groups.shared.title'/>"/>
  <meta name="menu" content="MainMenu"/>
  <link rel="stylesheet" media="all" href="<rst:assetUrl value='/styles/simplicity/theme.css'/>" />
  <script src="<rst:assetUrl value='/scripts/pages/rspace/sharedRecordsList.js'/>"></script>
	<script src="<rst:assetUrl value='/scripts/pages/utils/columnSortToggle.js'/>"></script>   
</head>

<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />
<jsp:include page="/WEB-INF/pages/recordInfoPanel.jsp" />

<div class="tabularViewTop">
    <c:if test="${publishedLinks_for_sysadmin_to_manage}">
        <h2 class="title"><spring:message code="groups.sharing.usersPublishedDocuments"/></h2>
    </c:if>
    <c:if test="${publishedLinks_for_user_to_see &&! publishedLinks_for_sysadmin_to_manage}">
        <shiro:hasRole name="ROLE_PI">
            <h2 class="title"><spring:message code="groups.sharing.myGroupsPublishedDocuments"/></h2>
        </shiro:hasRole>
        <shiro:hasRole name="ROLE_USER">
            <shiro:lacksRole name="ROLE_PI">
                <h2 class="title"><spring:message code="groups.sharing.myPublishedDocuments"/></h2>
            </shiro:lacksRole>
        </shiro:hasRole>
    </c:if>
    <c:if test="${!publishedLinks_for_user_to_see}">
        <h2 class="title"><spring:message code="groups.shared.title"/></h2>
    </c:if>
    <c:if test="${publishedLinks_for_user_to_see || publishedLinks_for_sysadmin_to_manage}">
    <spring:message code="groups.sharing.copyAllLinks" var="copyAllLinksLabel"/>
    <div style="flex-grow:4;display: flex;margin: 10px 0 10px 0;" >
        <span class="req" >${copyAllLinksLabel}</span>
        <li alt="<spring:message code='groups.sharing.copyAllLinksAlt'/>"  class="linkShare public-tooltip" id="copyAllLinks" style="display: contents;">
            <img src="/images/icons/copyIcon.png" style="width:35px;height:28px;"/>
            <div style="display:flex;" class="tooltiptext">${copyAllLinksLabel}</div>
            </li>
    </div>
    </c:if>
  <div
    class="base-search"
    data-placeholder="<spring:message code='groups.sharing.searchPlaceholder'/>"
    data-onsubmit="handleSearchShared"
    data-variant="outlined"
    data-elid="searchSharedListInput"
  ></div>
</div>

<div id="searchModePanel" style="display: none;">
  <span id="message"></span>
  <button id="resetSearch"><spring:message code="common:search.clearTooltip"/></button>
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
	<h2> <spring:message code="groups.sharing.sharedDocumentsRequestsHeading"/></h2>
	<div>
	<jsp:include page="shared_record_requests_list_ajax.jsp" />
	</div>
</rst:hasDeploymentProperty>

<!-- Import React search -->
<rst:bundle bundle="baseSearch" />

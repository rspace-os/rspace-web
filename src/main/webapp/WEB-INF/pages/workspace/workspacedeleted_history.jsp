<%@ include file="/common/taglibs.jsp"%>

<script src="<rst:assetUrl value='/scripts/pages/rspace/workspaceDeletedHistory.js' />"></script>
<title><spring:message code="deletedItems.pageTitle"/></title>
<head>
    <meta name="heading" content="<spring:message code='pageHeadings.history'/>"/>
</head>
<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />

<!-- <div id="messageToolbar" style="height: 15px;text-align: center;" >
	<span class="messagebox" id="noMessages" ></span>
</div> -->

<div class="tabularViewTop">
  <h3 class="title"><spring:message code="deletedItems.heading"/></h3>

  <div
    class="base-search"
    data-elid="searchDeletedListInput"
    data-placeholder="<spring:message code='deletedItems.searchPlaceholder'/>"
    data-onsubmit="searchDeleted"
    data-variant="outlined"
  ></div>
</div>

<div id="searchModePanel" style="display: none;">
  <span id="message"></span>
  <button id="resetSearch"><spring:message code="common:search.clearTooltip"/></button>
</div>

<div id="deletedItemsList" class="bootstrap-custom-flat newTabularView">
    <jsp:include page="workspaceDeletedHistory_ajax.jsp"></jsp:include>
</div>

<!-- Import React search -->
<rst:bundle bundle="baseSearch" />

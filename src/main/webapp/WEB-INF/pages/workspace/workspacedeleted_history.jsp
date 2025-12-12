<%@ include file="/common/taglibs.jsp"%>

<script src="<c:url value ='/scripts/pages/rspace/workspaceDeletedHistory.js'/>"></script>
<title>Deleted documents</title>
<head>
    <meta name="heading" content="History"/>
</head>
<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />

<!-- <div id="messageToolbar" style="height: 15px;text-align: center;" >
	<span class="messagebox" id="noMessages" ></span>
</div> -->

<div class="tabularViewTop">
  <h3 class="title">Deleted Items</h3>

  <div
    class="base-search"
    data-elid="searchDeletedListInput"
    data-placeholder="Search by name"
    data-onsubmit="searchDeleted"
    data-variant="outlined"
  ></div>
</div>

<div id="searchModePanel" style="display: none;">
  <span id="message"></span>
  <button id="resetSearch">Clear search</button>
</div>

<div id="deletedItemsList" class="bootstrap-custom-flat newTabularView">
    <jsp:include page="workspaceDeletedHistory_ajax.jsp"></jsp:include>
</div>

<!-- Import React search -->
<script src="<c:url value='/ui/dist/baseSearch.js'/>"></script>

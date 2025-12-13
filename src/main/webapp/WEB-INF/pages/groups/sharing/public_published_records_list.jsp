<%@ include file="/common/taglibs.jsp"%>
<%@ include file="/common/meta.jsp"%>

<head>
  <script src="<c:url value='/public/ui/dist/runtime.js'/>"></script>
  <link rel="stylesheet" href="<c:url value='/styles/bootstrap-custom-flat.css'/>" />
  <link media="all" href="<c:url value='/styles/simplicity/theme.css'/>" rel="stylesheet" />
  <link media="print" href="<c:url value='/styles/simplicity/print.css'/>" rel="stylesheet" />
  <link href="<c:url value='/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css'/>" rel="stylesheet" />
  <link href="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.css'/>" rel="stylesheet" />
  <link href="<c:url value='/styles/rs-global.css'/>" rel="stylesheet" />
  
  <script src="<c:url value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/mustache/mustache.min.js'/>"></script>
  <script src="<c:url value='/scripts/jquery.toastmessage.js'/>"></script>
  <script src="<c:url value='/scripts/global.js'/>"></script>
  <script src="<c:url value='/scripts/global.settingsStorage.js'/>"></script>
  <link rel="stylesheet" href="<c:url value='/styles/jquery.toastmessage.css'/>" />
  
  <script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>
  
  <script src="<c:url value='/scripts/pages/workspace/editor/public_documentView.js'/>"></script>
  <script src="<c:url value='/scripts/pages/rspace/sharedRecordsList.js'/>"></script>
  <script src="<c:url value='/scripts/pages/utils/columnSortToggle.js'/>"></script>

  <script>
    var workspaceSettings = {};
    workspaceSettings.currentUser = "${sessionScope.userInfo.username}";
    const sharedFolderID = "${sharedFolderID}";
    const sharingUser = "${sharingUser}";
    const documentName = "${documentName}"
  </script>

  <title>Published Documents</title>
  <meta name="heading" content="<fmt:message key='groups.shared.title'/>"/>
  <meta name="robots" content="noarchive, nosnippet"/>
  <meta name="menu" content="MainMenu"/>
  <link rel="stylesheet" media="all" href="<c:url value='/styles/simplicity/theme.css'/>" />

</head>

<body>

  <jsp:include page="/WEB-INF/pages/recordInfoPanel.jsp" />

  <jsp:include page="/common/externalPagesNavbar.jsp" />

  <div class="tabularViewTop">
    <h2 class="title">RSpace Users' Published Documents</h2>
    
    <div class="base-search"
        data-placeholder="By document or user"
        data-onsubmit="handleSearchShared"
        data-variant="outlined"
        data-elid="searchSharedListInput">
    </div>
  </div>

  <c:set var="isLoggedAsNonAnonymousUser" value="${sessionScope.userInfo.username != null}"/>
  <c:if test="${isLoggedAsNonAnonymousUser}">
        <p><img src="/images/notice.png" style="height: 14px; vertical-align: text-top;"/>
            <a href="/record/share/published/manage" target="_blank" style="font-size:12px;cursor: pointer;">View your own published documents</a></p>
  </c:if>

  <div id="searchModePanel" style="display: none;">
    <c:if test="${empty sharedRecords}">
        <p style="padding: 10px;">You have no published documents to manage.</p>
    </c:if>
  </div>

  <div id="sharedRecordsListContainer" class="bootstrap-custom-flat newTabularView">
    <jsp:include page="public_published_records_list_ajax.jsp" />
  </div>

  <!-- Import React search -->
  <script src="<c:url value='/ui/dist/baseSearch.js'/>"></script>

</body>
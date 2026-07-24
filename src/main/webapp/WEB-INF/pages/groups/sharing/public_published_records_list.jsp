<%@ include file="/common/taglibs.jsp"%>
<%@ include file="/common/meta.jsp"%>

<head>
  <link rel="stylesheet" href="<rst:assetUrl value='/styles/bootstrap-custom-flat.css'/>" />
  <link media="all" href="<rst:assetUrl value='/styles/simplicity/theme.css'/>" rel="stylesheet" />
  <link media="print" href="<rst:assetUrl value='/styles/simplicity/print.css'/>" rel="stylesheet" />
  <link href="<rst:assetUrl value='/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css'/>" rel="stylesheet" />
  <link href="<rst:assetUrl value='/scripts/bower_components/Apprise-v2/apprise-v2.css'/>" rel="stylesheet" />
  <link href="<rst:assetUrl value='/styles/rs-global.css'/>" rel="stylesheet" />
  
  <script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/Apprise-v2/apprise-v2.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/mustache/v420/mustache.min.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/jquery.toastmessage.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/global.settingsStorage.js'/>"></script>
  <link rel="stylesheet" href="<rst:assetUrl value='/styles/jquery.toastmessage.css'/>" />
  
  <script src="<rst:assetUrl value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>
  
  <script src="<rst:assetUrl value='/scripts/pages/workspace/editor/public_documentView.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/rspace/sharedRecordsList.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/utils/columnSortToggle.js'/>"></script>

  <script>
    var workspaceSettings = {};
    workspaceSettings.currentUser = "${sessionScope.userInfo.username}";
    const sharedFolderID = "${sharedFolderID}";
    const sharingUser = "${sharingUser}";
    const documentName = "${documentName}"
  </script>

  <title><spring:message code="groups.sharing.publishedDocumentsTitle"/></title>
  <meta name="heading" content="<spring:message code='groups.shared.title'/>"/>
  <meta name="robots" content="noarchive, nosnippet"/>
  <meta name="menu" content="MainMenu"/>
  <link rel="stylesheet" media="all" href="<rst:assetUrl value='/styles/simplicity/theme.css'/>" />

</head>

<body>

  <jsp:include page="/WEB-INF/pages/recordInfoPanel.jsp" />

  <jsp:include page="/common/externalPagesNavbar.jsp" />

  <div class="tabularViewTop">
    <h2 class="title"><spring:message code="groups.sharing.rspaceUsersPublishedDocuments"/></h2>

    <div class="base-search"
        data-placeholder="<spring:message code='groups.sharing.searchPlaceholder'/>"
        data-onsubmit="handleSearchShared"
        data-variant="outlined"
        data-elid="searchSharedListInput">
    </div>
  </div>

  <c:set var="isLoggedAsNonAnonymousUser" value="${sessionScope.userInfo.username != null}"/>
  <c:if test="${isLoggedAsNonAnonymousUser}">
        <p><img src="/images/notice.png" style="height: 14px; vertical-align: text-top;"/>
            <a href="/record/share/published/manage" target="_blank" style="font-size:12px;cursor: pointer;"><spring:message code="groups.sharing.viewOwnPublishedDocuments"/></a></p>
  </c:if>

  <div id="searchModePanel" style="display: none;">
    <c:if test="${empty sharedRecords}">
        <p style="padding: 10px;"><spring:message code="groups.sharing.noPublishedDocuments"/></p>
    </c:if>
  </div>

  <div id="sharedRecordsListContainer" class="bootstrap-custom-flat newTabularView">
    <jsp:include page="public_published_records_list_ajax.jsp" />
  </div>

  <!-- Import React search -->
  <rst:bundle bundle="baseSearch" />

</body>
<%@ include file="/common/taglibs.jsp"%>

<head>
  <title>MS Teams Tab config</title>

  <link href="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.css'/>" rel="stylesheet" />
  <link href="/styles/pages/connect/msteams/msTeamsThemes.css" rel="stylesheet" />
  <link href="/styles/pages/searchableRecordPicker.css" rel="stylesheet" />
  <link href="<c:url value='/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css'/>" rel="stylesheet" />

  <script src="<c:url value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
  <script src="/scripts/pages/searchableRecordPicker.js"></script>
  <script src="/scripts/pages/connect/msteams/MicrosoftTeams_sdk_v1.0.min.js"></script>
  <script src="<c:url value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/mustache/mustache.min.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>

  <script src="/scripts/global.js"></script>
  <script src="/scripts/pages/connect/msteams/msTeamsInitialiser.js"></script>
  <script src="/scripts/pages/connect/msteams/msTeamsTabConfig.js"></script>

  <script>
    var clientUISettingsPref = "${clientUISettingsPref}";
  </script>
  <script src="/scripts/pages/workspace/clientUISettings.js"></script>

</head>

<jsp:include page="../../searchableRecordPicker.jsp">
  <jsp:param name="onlyDocuments" value="true"/>
</jsp:include>

<hr id="footerHr"/>
<div id="showServerUrl">
  <span>Current RSpace Server: </span> <span id="currentServerUrl"></span>
</div>
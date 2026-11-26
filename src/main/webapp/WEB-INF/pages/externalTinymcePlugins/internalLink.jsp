<%@ include file="/common/taglibs.jsp"%>

<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <axt:stylesheet href="/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css"/>
    <axt:stylesheet href="/scripts/bower_components/bootstrap/dist/css/bootstrap.min.css"/>
    <axt:stylesheet href="/styles/rs-global.css"/>
    <axt:stylesheet href="/scripts/externalTinymcePlugins/internallink/css/links.css"/>
    <axt:stylesheet href="/scripts/bower_components/Apprise-v2/apprise-v2.css"/>
    <axt:stylesheet href="/styles/simplicity/tools.css"/>
    <axt:stylesheet href="/styles/simplicity/layout.css"/>
    <axt:stylesheet href="/styles/public.css"/>
    <axt:stylesheet href="/styles/pages/searchableRecordPicker.css"/>

    <script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>
    <script src='<rst:assetUrl value='/scripts/tinymceDialogUtils.js'/>'></script>
    <script src="<rst:assetUrl value='/scripts/bower_components/mustache/v420/mustache.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/bower_components/Apprise-v2/apprise-v2.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/searchableRecordPicker.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/workspace/clientUISettings.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/externalTinymcePlugins/internallink/main.js'/>"></script>
    <style>
        body {
            padding: 5px;
        }
    </style>
    <%@ include file="/common/legacyDarkMode.jsp"%>
</head>
<jsp:include page="/scripts/templates/blockUI.html"/>

<jsp:include page="../searchableRecordPicker.jsp">
  <jsp:param name="onlyDocuments" value="false"/>
</jsp:include>

<%@ include file="/common/taglibs.jsp"%>

<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="<rst:assetUrl value='/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css'/>" rel="stylesheet" />
    <link href="<rst:assetUrl value='/scripts/bower_components/bootstrap/dist/css/bootstrap.min.css'/>" rel="stylesheet" />
    <link href='<rst:assetUrl value='/styles/rs-global.css'/>' rel="stylesheet"/>
    <link href='<rst:assetUrl value='/scripts/externalTinymcePlugins/internallink/css/links.css'/>' rel="stylesheet"/>
    <link rel="stylesheet" href="<rst:assetUrl value='/scripts/bower_components/Apprise-v2/apprise-v2.css'/>"/>
    <link media="all" href="<rst:assetUrl value='/styles/simplicity/tools.css'/>" rel="stylesheet" />
    <link media="all" href="<rst:assetUrl value='/styles/simplicity/layout.css'/>" rel="stylesheet" />
    <link media="all" href="<rst:assetUrl value='/styles/public.css'/>" rel="stylesheet" />
    <link href="<rst:assetUrl value='/styles/pages/searchableRecordPicker.css'/>" rel="stylesheet"/>

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
</head>
<jsp:include page="/scripts/templates/blockUI.html"/>

<jsp:include page="../searchableRecordPicker.jsp">
  <jsp:param name="onlyDocuments" value="false"/>
</jsp:include>

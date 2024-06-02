<%@ include file="/common/taglibs.jsp"%>

<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="<c:url value='/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css'/>" rel="stylesheet" />
    <link href="<c:url value='/scripts/bower_components/bootstrap/dist/css/bootstrap.min.css'/>" rel="stylesheet" />
    <link href='/styles/rs-global.css' rel="stylesheet"/>
    <link href='/scripts/externalTinymcePlugins/internallink/css/links.css' rel="stylesheet"/>
    <link rel="stylesheet" href="/scripts/bower_components/Apprise-v2/apprise-v2.css"/>
    <link media="all" href="<c:url value='/styles/simplicity/tools.css'/>" rel="stylesheet" />
    <link media="all" href="<c:url value='/styles/simplicity/layout.css'/>" rel="stylesheet" />
    <link media="all" href="<c:url value='/styles/public.css'/>" rel="stylesheet" />
    <link href="/styles/pages/searchableRecordPicker.css" rel="stylesheet"/>

    <script src="<c:url value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
    <script src="<c:url value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
    <script src="/scripts/bower_components/blockui/jquery.blockUI.js"></script>
    <script src="/scripts/bower_components/bootstrap/dist/js/bootstrap.js"></script>
    <script src='/scripts/tinymceDialogUtils.js'></script>
    <script src="/scripts/bower_components/mustache/mustache.min.js"></script>
    <script src="/scripts/global.js"></script>
    <script src="/scripts/bower_components/Apprise-v2/apprise-v2.js"></script>
    <script src="/scripts/pages/searchableRecordPicker.js"></script>
    <script src="/scripts/pages/workspace/clientUISettings.js"></script>
    <script src="/scripts/externalTinymcePlugins/internallink/main.js"></script>
    <style>
        body {
            padding: 5px;
        }
    </style>
</head>

<jsp:include page="../searchableRecordPicker.jsp">
  <jsp:param name="onlyDocuments" value="false"/>
</jsp:include>

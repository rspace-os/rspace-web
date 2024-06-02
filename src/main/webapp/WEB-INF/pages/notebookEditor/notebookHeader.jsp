<%@ include file="/common/taglibs.jsp"%>
<head>
    <title><spring:message code="notebook.title"/></title>
    <link rel="canonical" href="${applicationScope['RS_DEPLOY_PROPS']['serverUrl']}${requestScope['javax.servlet.forward.servlet_path']}" />

    <link href="<c:url value='/scripts/bower_components/jquery-tagit/css/tagit.ui-zendesk.css'/>" rel="stylesheet" />

    <link rel="stylesheet" href="<c:url value='/styles/pages/workspace/workspace.css'/>" />
    <link rel="stylesheet" href="<c:url value='/styles/pages/workspace/workspace-widgets.css'/>" />
    <link rel="stylesheet" href="<c:url value='/styles/pages/workspace/workspace-toolbar.css'/>" />
    <link rel="stylesheet" href="<c:url value='/styles/pages/workspace/workspace-extra-icons.css'/>" />
    <link rel="stylesheet" href="<c:url value='/styles/pages/workspace/workspace-dialogs.css'/>" />

    <link rel="stylesheet" href="<c:url value='/scripts/bower_components/photoswipe/dist/photoswipe.css'/>" />
    <link rel="stylesheet" href="<c:url value='/scripts/bower_components/photoswipe/dist/default-skin/default-skin.css'/>" />
    <link rel="stylesheet" href="<c:url value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.css'/>" />
    <link rel="stylesheet" href="<c:url value='/scripts/bower_components/font-awesome/css/font-awesome.min.css'/>" />
    <link rel="stylesheet" href="<c:url value='/scripts/tinymce/tinymce516/plugins/codesample/css/prism.css'/>" />

    <link rel="stylesheet" href="<c:url value='/styles/journal.css'/>" />
    <link rel="stylesheet" href="<c:url value='/styles/messages.css'/>" />
    <link rel="stylesheet" href="<c:url value='/styles/mediaGallery.css'/>" />
    <link rel="stylesheet" href="<c:url value='/styles/rs-widgets.css'/>" />
    <script src="<c:url value='/scripts/bower_components/jquery-tagit/js/tag-it.min.js'/>"></script>
    <script src="<c:url value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
    <script src="<c:url value='/scripts/tinymce/tinymce516/plugins/codesample/js/prism.js'/>"></script>
    <script src="<c:url value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>
    <script	src="<c:url value='/scripts/pages/workspace/clientUISettings.js'/>"></script>
    <script type="text/javascript">
        var notebookId = ${selectedNotebookId};
        var notebookPath = "<c:url value='/'/>";
        var isEditable = ${canEdit};
        // maybe user can only see selected entry
        var canSeeNotebook = ${canSeeNotebook};
        // where to navigate user after closing the notebook view
        var workspaceFolderId = '${workspaceFolderId}';
        // Static is always editable until you can share it
        var editable = "VIEW_MODE";
        // this var is escaped by JSP, so does not need to be ecaped again
        var notebookName = '${selectedNotebookName}';
        const publicationSummary= '${publicationSummary}';
        const publishOnInternet = "${publishOnInternet}";
        const contactDetails = '${contactDetails}';
        var settingsKey = '${settingsKey}';


        <c:if test="${not empty sessionScope.get(settingsKey)}">
        var settingsObject = <c:out value="${sessionScope.get(settingsKey).toJson()}" escapeXml="false" />;
        </c:if>

        var clientUISettingsPref = "${clientUISettingsPref}";
        var isDocumentEditor = false;
        <c:choose>
        <c:when test="${ not empty initialRecordToDisplay}">
        var initialRecordToDisplay="journalEntry${initialRecordToDisplay}";
        let tagMetaData="";//this global will be set when a notebook entry loads in journal.js loadEntry. It will later be used by coreEditor for tagging
        </c:when>
        <c:otherwise>
        var initialRecordToDisplay=null;
        </c:otherwise>
        </c:choose>
        var entryCount = ${entryCount};
    </script>
    <script src="<c:url value='/scripts/tags/shareDlg.js'/>"></script>
    <script	src="<c:url value='/scripts/pages/coreEditor.js'/>"></script>
    <script src="<c:url value='/scripts/pages/notebookEditor.js'/>"></script>
</head>

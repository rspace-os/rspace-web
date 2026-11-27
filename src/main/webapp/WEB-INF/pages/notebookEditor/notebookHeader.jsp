<%@ include file="/common/taglibs.jsp"%>
<%-- Head-content fragment for SiteMesh 3: emits NO <head> wrapper of its own, so it can sit
     inside a caller's single <head> without producing a nested <head> (SiteMesh 3 keeps only
     the inner head and silently drops the outer head's content). Callers (notebookEditor.jsp,
     public_notebookView.jsp) are responsible for the surrounding <head>...</head>. --%>
    <title><spring:message code="notebook.title"/></title>
    <link rel="canonical" href="${applicationScope['RS_DEPLOY_PROPS']['serverUrl']}${requestScope['jakarta.servlet.forward.servlet_path']}" />

    <link href="<rst:assetUrl value='/scripts/bower_components/jquery-tagit/css/tagit.ui-zendesk.css'/>" rel="stylesheet" />

    <link rel="stylesheet" href="<rst:assetUrl value='/styles/pages/workspace/workspace.css'/>" />
    <link rel="stylesheet" href="<rst:assetUrl value='/styles/pages/workspace/workspace-widgets.css'/>" />
    <link rel="stylesheet" href="<rst:assetUrl value='/styles/pages/workspace/workspace-toolbar.css'/>" />
    <link rel="stylesheet" href="<rst:assetUrl value='/styles/pages/workspace/workspace-extra-icons.css'/>" />
    <link rel="stylesheet" href="<rst:assetUrl value='/styles/pages/workspace/workspace-dialogs.css'/>" />

    <link rel="stylesheet" href="<rst:assetUrl value='/scripts/bower_components/photoswipe/dist/photoswipe.css'/>" />
    <link rel="stylesheet" href="<rst:assetUrl value='/scripts/bower_components/photoswipe/dist/default-skin/default-skin.css'/>" />
    <link rel="stylesheet" href="<rst:assetUrl value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.css'/>" />
    <link rel="stylesheet" href="<rst:assetUrl value='/scripts/bower_components/font-awesome/css/font-awesome.min.css'/>" />
    <link rel="stylesheet" href="<rst:assetUrl value='/scripts/tinymce/tinymce5109/plugins/codesample/css/prism.css'/>" />

    <link rel="stylesheet" href="<rst:assetUrl value='/styles/journal.css'/>" />
    <link rel="stylesheet" href="<rst:assetUrl value='/styles/messages.css'/>" />
    <script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/bower_components/jquery-tagit/js/tag-it.min.js'/>"></script>
    <script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
    <script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/tinymce/tinymce5109/plugins/codesample/js/prism.js'/>"></script>
    <script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>
    <script nonce="${requestScope.cspNonce}"	src="<rst:assetUrl value='/scripts/pages/workspace/clientUISettings.js'/>"></script>
    <script nonce="${requestScope.cspNonce}" type="text/javascript">
        var notebookId = ${selectedNotebookId};
        var notebookPath = "<c:url value='/'/>";
        var isEditable = ${canEdit};
        // maybe user can only see selected entry
        var canSeeNotebook = ${canSeeNotebook};
        // where to navigate user after closing the notebook view
        var workspaceFolderId = '${f:escapeJavaScript(workspaceFolderId)}';
        // Static is always editable until you can share it
        var editable = "VIEW_MODE";
        // this var is escaped by JSP, so does not need to be ecaped again
        var notebookName = '${f:escapeJavaScript(selectedNotebookName)}';
        const publicationSummary= '${f:escapeJavaScript(publicationSummary)}';
        const publishOnInternet = "${f:escapeJavaScript(publishOnInternet)}";
        const contactDetails = '${f:escapeJavaScript(contactDetails)}';
        var settingsKey = '${f:escapeJavaScript(settingsKey)}';


        <c:if test="${not empty sessionScope.get(settingsKey)}">
        var settingsObject = JSON.parse('${f:escapeJavaScript(sessionScope.get(settingsKey).toJson())}');
        </c:if>

        var clientUISettingsPref = "${f:escapeJavaScript(clientUISettingsPref)}";
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
    <script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/tags/publishDlg.js'/>"></script>
    <rst:bundle bundle="tinymceGalleryUtils" />
    <script nonce="${requestScope.cspNonce}"	src="<rst:assetUrl value='/scripts/pages/coreEditor.js'/>"></script>
    <script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/pages/notebookEditor.js'/>"></script>

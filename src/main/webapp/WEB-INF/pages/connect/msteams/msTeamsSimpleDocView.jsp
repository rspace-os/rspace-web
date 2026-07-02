<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>Simple document view for MS Teams</title>

    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/MicrosoftTeams_sdk_v1.0.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/msTeamsInitialiser.js'/>"></script>

    <link href="<rst:assetUrl value='/styles/pages/connect/msteams/msTeamsThemes.css'/>" rel="stylesheet" />
    <link href="<rst:assetUrl value='/styles/pages/connect/msteams/msTeamsDocView.css'/>" rel="stylesheet" />
</head>

<body>
<%-- Flags this as the simplified editor view for coreEditor.js (which reads it via the
     [data-simple-editor-view] attribute selector to skip the file-tree browser). Lives in the
     body content, which SiteMesh 3 writes verbatim, rather than as a head-script global. --%>
<div class="bootstrap-custom-flat" data-simple-editor-view="true">
    <div id="loggedUserSimpleDocPanel">
        Logged in as: ${sessionScope.userInfo.username}
        <div id="simpleDocLogoutBtn" class="btn btn-primary">Sign out</div>
    </div>
</div>

<jsp:include page="/WEB-INF/pages/workspace/editor/simpleStructuredDocument.jsp" />

<script src="<rst:assetUrl value='/scripts/pages/connect/msteams/msTeamsSimpleDocView.js'/>"></script>
</body>

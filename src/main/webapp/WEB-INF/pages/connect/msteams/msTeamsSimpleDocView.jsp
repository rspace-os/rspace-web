<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>Simple document view for MS Teams</title>
    
    <script src="/scripts/pages/connect/msteams/MicrosoftTeams_sdk_v1.0.min.js"></script>
    <script src="/scripts/pages/connect/msteams/msTeamsInitialiser.js"></script>

</head>

<div class="bootstrap-custom-flat">
    <div id="loggedUserSimpleDocPanel">
        Logged in as: ${sessionScope.userInfo.username}
        <div id="simpleDocLogoutBtn" class="btn btn-primary">Sign out</div>
    </div>
</div>

<jsp:include page="/WEB-INF/pages/workspace/editor/simpleStructuredDocument.jsp" />

<script src="/scripts/pages/connect/msteams/msTeamsSimpleDocView.js"></script>

<link href="/styles/pages/connect/msteams/msTeamsThemes.css" rel="stylesheet" />
<link href="/styles/pages/connect/msteams/msTeamsDocView.css" rel="stylesheet" />
    


<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>RSpace authentication screen for MS Teams</title>
    
    <link href="/styles/pages/connect/msteams/msTeamsThemes.css" rel="stylesheet" />
    <link href="/styles/pages/connect/msteams/msTeamsRSpaceAuthentication.css" rel="stylesheet" />

    <script src="/scripts/bower_components/jquery/dist/jquery.min.js"></script>
    <script src="/scripts/global.js"></script>

    <script src="/scripts/pages/connect/msteams/MicrosoftTeams_sdk_v1.0.min.js"></script>
    <script src="/scripts/pages/connect/msteams/msTeamsInitialiser.js"></script>
    <script src="/scripts/pages/connect/msteams/msTeamsRSpaceAuthentication.js"></script>

</head>

<div id="msTeamsAuthenticationDiv">
    <iframe id="workspaceIframe" class="maxHeight400" style="display:none"></iframe>
</div>
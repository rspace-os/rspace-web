<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="connect.msteams.rspaceAuthentication.title"/></title>
    
    <link href="<rst:assetUrl value='/styles/pages/connect/msteams/msTeamsThemes.css'/>" rel="stylesheet" />
    <link href="<rst:assetUrl value='/styles/pages/connect/msteams/msTeamsRSpaceAuthentication.css'/>" rel="stylesheet" />

    <script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>

    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/MicrosoftTeams_sdk_v1.0.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/msTeamsInitialiser.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/msTeamsRSpaceAuthentication.js'/>"></script>

</head>

<div id="msTeamsAuthenticationDiv">
    <iframe id="workspaceIframe" class="maxHeight400" style="display:none"></iframe>
</div>
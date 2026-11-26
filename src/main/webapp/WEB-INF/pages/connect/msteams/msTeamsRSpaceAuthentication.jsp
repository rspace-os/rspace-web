<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="connect.msteams.rspaceAuthentication.title"/></title>
    
    <axt:stylesheet href="/styles/pages/connect/msteams/msTeamsThemes.css"/>
    <axt:stylesheet href="/styles/pages/connect/msteams/msTeamsRSpaceAuthentication.css"/>

    <script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>

    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/MicrosoftTeams_sdk_v1.0.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/msTeamsInitialiser.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/msTeamsRSpaceAuthentication.js'/>"></script>

    <%@ include file="/common/legacyDarkMode.jsp"%>
</head>

<div id="msTeamsAuthenticationDiv">
    <iframe id="workspaceIframe" class="maxHeight400" style="display:none"></iframe>
</div>
<%@ include file="/common/taglibs.jsp" %>

    <head>
        <title>${docName} (RSpace)</title>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no">
        <link rel="shortcut icon" href="${favIcon}" />

        <script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
        <script src="<rst:assetUrl value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
        <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
        <script src="<rst:assetUrl value='/scripts/pages/connect/wopi/collabora/collaboraView.js'/>"></script>

        <axt:stylesheet href="/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css"/>
        <axt:stylesheet href="/styles/bootstrap-custom-flat.css"/>
        <axt:stylesheet href="/styles/pages/connect/wopi/collabora/collaboraView.css"/>

        <%@ include file="/common/legacyDarkMode.jsp"%>
    </head>

    <div id="collaboraIframeDiv">
        <form id="collabora_form" name="collabora_form" target="collabora_frame" enctype="multipart/form-data" action="${actionUrl}" method="post">
            <input name="access_token" value="${collaboraAccessToken}" type="hidden" />
        </form>
        <div id="frameholder"></div>
    </div>
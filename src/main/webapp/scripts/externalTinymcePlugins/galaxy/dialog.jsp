<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html lang="${fn:escapeXml(empty requestScope.rsResolvedLocaleTag ? 'en-US' : requestScope.rsResolvedLocaleTag)}">
<head>
  <title><spring:message code="apps.dialogTitles.galaxy"/></title>
  <meta property="csp-nonce" nonce="${requestScope.cspNonce}">
  <rst:viteClient />
  <script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
  <script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
  <script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
  <script nonce="${requestScope.cspNonce}" defer src="<rst:assetUrl value='/scripts/global.settingsStorage.js'/>"></script>
  <script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/global.js'/>"></script>
  <script nonce="${requestScope.cspNonce}" defer src="<rst:assetUrl value='/scripts/segment.js'/>"></script>
  <rst:bundle bundle="tinymceGalaxy" />
  <link rel="stylesheet" type="text/css" href="<rst:assetUrl value='/styles/bootstrap-custom-flat.css'/>" />
</head>

<body>
<div>
  <div id="tinymce-galaxy"></div>
</div>
</body>
</html>

<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>

<html lang="en">
<head>
  <title>Use a Galaxy Workflow</title>
  <rst:viteClient />
  <script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
  <script defer src="<rst:assetUrl value='/scripts/global.settingsStorage.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
  <script defer src="<rst:assetUrl value='/scripts/segment.js'/>"></script>
  <rst:bundle bundle="tinymceGalaxy" />
  <link rel="stylesheet" type="text/css" href="<rst:assetUrl value='/styles/bootstrap-custom-flat.css'/>" />
</head>

<body>
<div>
  <div id="tinymce-galaxy"></div>
</div>
</body>
</html>


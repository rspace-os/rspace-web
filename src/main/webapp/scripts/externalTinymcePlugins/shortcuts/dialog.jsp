<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html>

<head>
  <title>Configure keyboard shortcuts</title>
  <rst:viteClient />
  <rst:bundle bundle="tinymceShortcuts" />
  <link rel="stylesheet" type="text/css" href="<rst:assetUrl value='/styles/bootstrap-custom-flat.css'/>" />
  <link rel="stylesheet" type="text/css" href="<rst:assetUrl value='/scripts/externalTinymcePlugins/shortcuts/styles/style.css'/>" />
  <script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
</head>

<body class="bootstrap-custom-flat bootstrap-namespace">
  <div class="container">
    <div id="tinymce-shortcuts"></div>
  </div>
</body>

</html>


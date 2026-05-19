<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html lang="en">
<head>
  <title>Insert from Ketcher</title>
  <rst:viteClient />
  <rst:bundle bundle="tinymceKetcher" />
  <rst:bundle bundle="ketcherViewer" />
  <script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
</head>
<body>
<input type="hidden" class="fieldId" data-id=""/>
<input type="hidden" class="chemId" data-id=""/>
</body>
</html>


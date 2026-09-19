<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html lang="${fn:escapeXml(empty requestScope.rsResolvedLocaleTag ? 'en-US' : requestScope.rsResolvedLocaleTag)}">
<head>
  <title><spring:message code="apps.dialogTitles.dbrepo"/></title>
  <rst:viteClient />
  <rst:bundle bundle="tinymceDBRepo" />
</head>
<body>
  <div id="tinymce-dbrepo"></div>
</body>
</html>

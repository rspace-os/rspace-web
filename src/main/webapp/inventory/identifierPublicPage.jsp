<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html lang="${fn:escapeXml(empty requestScope.rsResolvedLocaleTag ? 'en-US' : requestScope.rsResolvedLocaleTag)}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <title><spring:message code="inventory:publicIdentifierPage.pageTitle"/></title>
  <rst:viteClient />
  <rst:bundle bundle="inventoryRecordIdentifierPublicPage" />
</head>
<body>
  <noscript><spring:message code="common:javascriptRequired"/></noscript>
  <div id="identifierPublicPage"></div>
</body>
</html>

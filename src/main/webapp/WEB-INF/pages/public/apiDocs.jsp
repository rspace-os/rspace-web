<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html lang="${fn:escapeXml(empty requestScope.rsResolvedLocaleTag ? 'en-US' : requestScope.rsResolvedLocaleTag)}">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title><spring:message code="apiDocs.title"/></title>
    <rst:viteClient />
    <rst:bundle bundle="apiDocs" />
    <style>
      html, body { margin: 0; height: 100%; }
    </style>
  </head>
  <body>
    <noscript><spring:message code="apiDocs.noJsWarning"/></noscript>
    <div id="app"></div>
  </body>
</html>

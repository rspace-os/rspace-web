<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html lang="${fn:escapeXml(empty requestScope.rsResolvedLocaleTag ? 'en-US' : requestScope.rsResolvedLocaleTag)}" style="-webkit-text-size-adjust: none;">
  <head>
    <meta charset="UTF-8" />
    <meta
      name="viewport"
      content="width=device-width, initial-scale=1.0, maximum-scale=2.0"
    />
    <title><spring:message code="apps:page.title"/></title>
    <rst:viteClient />
    <rst:bundle bundle="apps" />

  </head>

  <body>
    <noscript><spring:message code="common:javascriptRequired"/></noscript>
    <div id="app"></div>
  </body>
</html>


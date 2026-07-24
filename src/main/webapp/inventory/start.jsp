<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html lang="${fn:escapeXml(empty requestScope.rsResolvedLocaleTag ? 'en-US' : requestScope.rsResolvedLocaleTag)}" style="-webkit-text-size-adjust: none;">
  <head>
    <meta charset="UTF-8" />
    <meta
      name="viewport"
      content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"
    />
    <title><spring:message code="inventory:pageTitle"/></title>
    <rst:viteClient />
    <rst:bundle bundle="inventoryEntry" />
    <style>
      html, body, #app, #app > div, #app > div > div {
        height: 100%;
        overscroll-behavior: none;
      }
      #app {
        overflow: hidden;
      }
    </style>
  </head>

  <body>
    <noscript><spring:message code="common:javascriptRequired"/></noscript>
    <div id="app"></div>
  </body>
</html>

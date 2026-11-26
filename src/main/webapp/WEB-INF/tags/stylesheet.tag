<%--
  Links a stylesheet and, unless the user's colour mode is light, its dark
  overrides from styles/dark/generated/ straight after it. Rendered by the
  server, the overrides block rendering like the stylesheet itself and keep
  its place in the cascade, so a dark page never paints light first.

  The mode comes from the `rs-color-mode` cookie, which scripts/darkMode.js
  and ui/src/colorScheme.ts keep in step with the stored mode. Without the
  cookie (system mode) the overrides apply when the system is dark. They only
  take effect under <html class="dark">, which darkMode.js sets.
--%>
<%@ tag body-content="empty" trimDirectiveWhitespaces="true" %>
<%@ attribute name="href" required="true" description="Webapp-relative path, starting with /" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags" %>
<link rel="stylesheet" href="<rst:assetUrl value='${href}'/>" />
<c:set var="colorMode" value="${cookie['rs-color-mode'].value}" />
<c:if test="${colorMode ne 'light'}">
  <c:set var="darkHref" value="/styles/dark/generated${href}" />
  <%
    String darkHref = (String) jspContext.getAttribute("darkHref");
    jspContext.setAttribute("hasDark", application.getResource(darkHref) != null);
  %>
  <c:if test="${hasDark}">
    <link rel="stylesheet" data-dark-mode-sheet
      media="${colorMode eq 'dark' ? 'screen' : 'screen and (prefers-color-scheme: dark)'}"
      href="<rst:assetUrl value='${darkHref}'/>" />
  </c:if>
</c:if>

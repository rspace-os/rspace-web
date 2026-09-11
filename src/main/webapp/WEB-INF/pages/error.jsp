<%-- Not isErrorPage: this is only ever rendered as a Spring view, and the
     directive made Jasper force a 500 over the status the handler set.
     The container error pages use the separate /error.jsp at the web root. --%>
<%@ page language="java" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>
<%@ taglib uri="http://researchspace.com/tags" prefix="rst" %>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<html lang="${fn:escapeXml(empty requestScope.rsResolvedLocaleTag ? 'en-US' : requestScope.rsResolvedLocaleTag)}">
<head>
    <title><spring:message code="errorPage.title"/></title>
    <link rel="stylesheet" media="all" href="<rst:assetUrl value='/styles/simplicity/theme.css'/>" />
</head>

<body id="error">
    <div id="page">
        <div id="content" class="clearfix">
            <div id="main">
                <h1><spring:message code="errorPage.heading"/></h1>
                <c:if test="${not empty exceptionMessage}">
				<spring:message code="errors.page.reasonsIntro"/>
				<ul>
					<li><spring:message code="errors.page.insufficientPermissions"/></li>
					<li><spring:message code="errors.page.unauthorizedOperation"/></li>
					<li><spring:message code="errors.page.genericServerError"/></li>
				</ul>
				<spring:message code="errors.page.contactSupportNotice"/>
                        <p><spring:message code="errors.page.idAtTimestamp" arguments="${errorId},${tstamp}"/></p>
                        <%-- EscapeXmlELResolver escapes every String an EL expression
                             resolves, so c:out here would escape it a second time --%>
                        <pre class="message"> ${exceptionMessage}</pre>
                </c:if>
                <c:if test="${empty exceptionMessage and not empty requestScope['jakarta.servlet.error.exception']}">
                    <pre class="message"><spring:message code="errors.page.unhandledExceptionNotice"/></pre>
                </c:if>
            </div>
        </div>
    </div>
</body>
</html>

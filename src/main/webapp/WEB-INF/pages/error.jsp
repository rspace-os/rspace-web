<%@ page language="java" isErrorPage="true" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
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
                <% if (exception != null) { %>
				<spring:message code="errors.page.reasonsIntro"/>
				<ul>
					<li><spring:message code="errors.page.insufficientPermissions"/></li>
					<li><spring:message code="errors.page.unauthorizedOperation"/></li>
					<li><spring:message code="errors.page.genericServerError"/></li>
				</ul>
				<spring:message code="errors.page.contactSupportNotice"/>
                        <p><spring:message code="errors.page.idAtTimestamp" arguments="${errorId},${tstamp}"/></p>
                        <pre class="message"> ${exceptionMessage}</pre>
                 <% } else if ((Exception)request.getAttribute("javax.servlet.error.exception") != null) { %>
                    <pre class="message"><spring:message code="errors.page.unhandledExceptionNotice"/></pre>
                 <% } %>
            </div>
        </div>
    </div>
</body>
</html>

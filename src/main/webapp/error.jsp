<%@ page language="java" isErrorPage="true"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring"%>
<%@ taglib uri="http://researchspace.com/tags" prefix="rst"%>
<%@ page import="com.axiope.webapp.jsp.EscapeXml,java.util.*,org.apache.commons.lang3.exception.ExceptionUtils,
   com.researchspace.core.util.*" %>
<!DOCTYPE html>
<%-- Default error page for errors in filters/ jsp etc outside spring  --%>
<html lang="${fn:escapeXml(empty requestScope.rsResolvedLocaleTag ? 'en-US' : requestScope.rsResolvedLocaleTag)}">
<head>
<title><spring:message code="errorPage.title" /></title>
<link rel="stylesheet" media="all"
	href="<rst:assetUrl value='/styles/simplicity/theme.css'/>" />
	<link href="<rst:assetUrl value='/styles/rs-global.css'/>" rel="stylesheet">
</head>

<body id="error">
	<div id="page">
		<div id="content" class="clearfix">
			<div id="main">
				<h1>
					<spring:message code="errorPage.heading" />
				</h1>
				<% if (exception != null) { %>
				<spring:message code="errorPage.explanation.message" />
				 <pre class="message">Message: <%out.write(EscapeXml.escape(exception.getMessage()));%>
				 <br/>
					 at: <% out.write(
								DateUtil.convertDateToISOFormat(new Date(), TimeZone.getDefault())); %>
				<br/>
					 with ID:  <% out.write(
								LoggingUtils.logException(exception)); %> </pre>
				<rst:hasDeploymentProperty name="showStackTraceInErrorPageEnabled" value="true">
					 <% out.write(
							 EscapeXml.escape(
								ExceptionUtils.getStackTrace(exception))); %>
					 
					 
				</rst:hasDeploymentProperty>

				<% } else if ((Exception)request.getAttribute("javax.servlet.error.exception") != null) { %>
				<pre></pre>
				<% } %>
			</div>
		</div>
	</div>
</body>
</html>

<%@ page language="java" isErrorPage="true"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib uri="http://researchspace.com/tags" prefix="rst"%>
<%@ page import="com.axiope.webapp.jsp.EscapeXml,java.util.*,org.apache.commons.lang3.exception.ExceptionUtils,
   com.researchspace.core.util.*" %>
<!DOCTYPE html>
<%-- Default error page for errors in filters/ jsp etc outside spring  --%>
<html>
<head>
<title><fmt:message key="errorPage.title" /></title>
<link rel="stylesheet" media="all"
	href="<c:url value='/styles/simplicity/theme.css'/>" />
	<link href="/styles/rs-global.css" rel="stylesheet">
</head>

<body id="error">
	<div id="page">
		<div id="content" class="clearfix">
			<div id="main">
				<h1>
					<fmt:message key="errorPage.heading" />
				</h1>
				<% if (exception != null) { %>
				<fmt:message key="errorPage.explanation.message" />
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

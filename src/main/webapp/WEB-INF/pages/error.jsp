<%@ page language="java" isErrorPage="true" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<html>
<head>
    <title><fmt:message key="errorPage.title"/></title>
    <link rel="stylesheet" media="all" href="<c:url value='/styles/simplicity/theme.css'/>" />
</head>

<body id="error">
    <div id="page">
        <div id="content" class="clearfix">
            <div id="main">
                <h1><fmt:message key="errorPage.heading"/></h1>
                <% if (exception != null) { %>
    				This is usually because
    				<ul>
    					<li>You have insufficient permissions to view the resource, or you requested an unauthorized or non-existent resource.</li>
    					<li>You attempted to do an operation which is unauthorized or not available to you.</li>
    					<li>An error occurred on the server.</li>
    				</ul>
    				Please inspect the message detail below &ndash; if you believe this to be a server error,
                          please contact  ResearchSpace support. You might also try asking the resource owner to share the resource with you.
                        <p>Id - ${errorId}  at ${tstamp}</p>
                        <pre class="message"> ${exceptionMessage}</pre>
                 <% } else if ((Exception)request.getAttribute("javax.servlet.error.exception") != null) { %>
                    <pre class="message">An exception occurred on the server. If it persists, please contact  ResearchSpace
                     support.</pre>
                 <% } %>
            </div>
        </div>
    </div>
</body>
</html>

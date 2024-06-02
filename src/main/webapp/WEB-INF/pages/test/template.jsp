<%@ include file="/common/taglibs.jsp" %>
<%@page import="java.util.*,org.apache.velocity.app.*" %>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta charset="UTF-8">
<title>Template</title>
</head>
Some HTML
<p/>
<% out.print(new Date());  
VelocityEngine vel = new VelocityEngine();
vel.setProperty("resource.loader","classpath");
/* vel.setProperty() */


%>
<p/>
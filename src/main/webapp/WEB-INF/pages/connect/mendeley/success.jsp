<%@ include file="/common/taglibs.jsp"%>
<%@ taglib prefix="social"  uri="http://www.springframework.org/spring-social/social/tags" %>
<head>
	<title>Success</title>

</head>
<div id="mainBlock">
<h2>Mendeley information</h2>
<div id="mendeleyStatus" class="bootstrap-custom-flat">
  <social:connected provider="mendeley">
   You are  connected to Mendeley.
  
  <h2>Docs</h2>
  <c:forEach items="${documents}" var ="doc">
   <p>${doc.title} </p>
   <hr/>
  </c:forEach>
  </social:connected>
  </div>	
</div>
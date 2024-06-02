<%@ include file="/common/taglibs.jsp"%>
<%@ taglib prefix="social"  uri="http://www.springframework.org/spring-social/social/tags" %>
<head>
	<title>Success</title>

</head>
<div id="mainBlock">
<h2>Mendeley profile</h2>
<div id="mendeleyStatus">
  <social:connected provider="mendeley">
   Profile:
   You are logged into account for  <a href="${profile.link}">${profile.displayName}</a> with email ${profile.email}
  </social:connected>
<hr/>  </div>	
</div>
<%@ include file="/common/taglibs.jsp"%>
<%@ taglib prefix="social"  uri="http://www.springframework.org/spring-social/social/tags" %>
<head>
<title>You are now connected to Mendeley!</title>
</head>


<social:connected provider="mendeley">
<form id="disconnect" method="post" action="/connect/mendeley">
	<div class="formInfo">
		<p>RSpace is already
			connected to your Mendeley account. Click the button if you wish to
			disconnect.</p>
	</div>
	<button type="submit">Disconnect</button>
	<input type="hidden" name="_method" value="delete" />
</form>
</social:connected>
<c:forEach items="${connections}" var="connections">
 You have the following connections:
 <p/>
 ${connection}
</c:forEach>
<div id="mainBlock">
Possible actions:
<p>
See your <a href="/mendeley/actions">documents</a>
<p>
See your <a href="/mendeley/profile">profile</a>
</div>
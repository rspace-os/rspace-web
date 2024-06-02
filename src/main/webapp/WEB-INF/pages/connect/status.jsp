<%@ include file="/common/taglibs.jsp"%>
<%@ taglib prefix="social"
	uri="http://www.springframework.org/spring-social/social/tags"%>
<head>
<title>Integrations</title>

</head>



<div id="mainBlock">
	<h2>Your connections</h2>
	<div id="mendeleyStatus">

		<social:notConnected provider="mendeley">
   You are not connected to Mendeley. Click <a href="/connect/mendeley">
				here</a> to connect.
  </social:notConnected>
  
 <social:connected provider="mendeley">
   You are  connected to Mendeley.
  </social:connected>
	</div>
	<div>
		<social:notConnected provider="figshare">
   You are not connected to Figshare. Click <a href="/connect/figshare">
				here</a> to connect.
  </social:notConnected>
		<social:connected provider="figshare">
   You are  connected to Figshare. Click the button if you wish to
			disconnect.<p />
			<form id="disconnect" method="post" action="/connect/figshare">
				<div class="formInfo">
					<p>RSpace is already connected to your Figshare account.
				</div>
				<button type="submit">Disconnect</button>
				<input type="hidden" name="_method" value="delete" />
				</form>
		</social:connected>
	</div>
</div>
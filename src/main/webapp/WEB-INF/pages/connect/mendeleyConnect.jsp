<%@ include file="/common/taglibs.jsp"%>
<head>

<title>Connect to Mendeley</title>
<script src="/scripts/bower_components/jquery/dist/jquery.js"></script>
<script src="/scripts/bower_components/mendeley-javascript-sdk/dist/standalone.js"></script>
<!-- <script src = "/scripts/pages/connect/mendeleyConnectCallback.js"></script -->>
<script type="text/javascript">
var mendeleyAccessTokenName="mendeleyAccessToken";
 $(document).ready(function (){
	 var propertiesJxqr = $.get("/deploymentproperties/ajax/properties", function(properties) {
  	   var id = properties['mendeley.id'];
  	   var callback = properties['baseURL'];
  	   var url = callback+"/app/connect/mendeley";
	   var options = { clientId: id,
			         redirectUrl:url,
			         accessTokenCookieName:mendeleyAccessTokenName
			        };
	   var auth = MendeleySDK.Auth.implicitGrantFlow(options);
	   MendeleySDK.API.setAuthFlow(auth);
	   MendeleySDK.API.documents.list().done(function(docs) {
		    console.log('Success!');
		    var token = RS.getCookieValueByName (mendeleyAccessTokenName); 
		    document.cookie = mendeleyAccessTokenName+"=; expires=Thu, 01 Jan 1970 00:00:00 UTC";
		    setAccessTokenCookie(token, 1);
		    $('#onImplicitFlowSuccess').show();
		}).fail(function(request, response) {
		    console.log('Failed!');
		    console.log('URL:', request.url);
		    console.log('Status:', response.status);
		});
	 });
 });
 
 function setAccessTokenCookie(accessToken, expireHours) {
     var d = new Date();
     d.setTime(d.getTime() + ((expireHours || 1)*60*60*1000));
     var expires = 'expires=' + d.toUTCString();
     var path="path=/";
     document.cookie = mendeleyAccessTokenName + '=' + accessToken + '; ' + expires + '; ' + path;
 }
 function createURL (id, baseURL) {
	 return "https://api.mendeley.com/oauth/authorize?client_id="
			  +id+"&redirect_uri="+baseURL
			  +"/app/connect/mendeley&scope=all&response_type=token";
 }
</script>


</head>
<div id="mainBlock">

<div id = "onImplicitFlowSuccess" style="display:none">
	You have successfully authenticated. Please close this window.
</div>

<div id="onServerFlow" style="display:none">
<%-- <form action="<c:url value="/connect/mendeley" />" method="POST" style="width:80%">
    <input type="hidden" name="scope" value="all" />
    <p>You haven't created any connections with Mendeley yet. Click the button to create
       a connection between your account and your Mendeley profile.
       <p/>
       (You'll be redirected to Mendeley where you'll be asked to authorize the connection.)</p>
    <p><button type="submit" value ="Connect">Connect
    </button></p>
    <p>Or click <a href="#" id="implicit">here</a>
</form> --%>
Sorry, there was a problem obtaining the access token from Mendeley:
<br/>
<div/>

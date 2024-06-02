<%@ include file="/common/taglibs.jsp"%>
<head>

<title>Connect to Omero</title>
<script src="/scripts/bower_components/jquery/dist/jquery.js"></script>
<script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>

</head>
<div id="mainBlock" class="bootstrap-custom-flat">



 <form  action="<c:url value="/apps/omero/connect" />" method="POST" style="width:80%">
    <input type="hidden" name="scope" value="all" />
    <p>You are disconnected from  Omero. You can either reconnect here, configure <a href="/apps"> more Apps</a> or close this window.
       <p/>
    <p><button class="btn btn-primary" type="submit" value ="Connect" id="rs-app-clustermarket-button">Connect
    </button></p>
</form> 

<div/>

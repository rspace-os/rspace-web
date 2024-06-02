<%@ include file="/common/taglibs.jsp"%>


<head>
    <meta name="heading" content="History"/>
    <title>Document History</title>

    <!-- moved to default.jsp -->
    <!-- <link rel="stylesheet" href="<c:url value='/styles/bootstrap-custom-flat.css'/>" /> -->
    
	<script src="<c:url value='/scripts/bower_components/jqui-multi-dates-picker/jquery-ui.multidatespicker.js'/>"></script>
	<script src="<c:url value='/scripts/pages/workspace/revisionHistory.js'/>"></script>
	<script type="text/javascript">
		var recordId=${currentDoc.id};
	</script>
</head>

<div id="messageToolbar" style="height: 15px;text-align: center;" >
	<span class="messagebox" id="noMessages" ></span>
</div>

<div id="revisionListContainer">
    <jsp:include page="revisionHistory_ajax.jsp"></jsp:include>
</div>
<%@ include file="/common/taglibs.jsp"%>
<head>
	<title>Authorization Error</title>
</head>

<body> 
    <p>
        RSpace was not authorized to use ${error.appName}: ${error.errorMsg}.<br/>
        
        Please try again, or contact your System Admin.
    </p>
    <c:if test="${not empty  error.errorDetails}">
      <h4> Details</h4>
      <p> ${error.errorDetails}</p>
    </c:if>
    
    
    <p>
        You can close this window.
    </p>
</body>
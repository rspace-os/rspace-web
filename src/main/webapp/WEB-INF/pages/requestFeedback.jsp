<%@ page import="com.researchspace.model.comms.GroupMessageOrRequest" %>
<%@ include file="/common/taglibs.jsp"%>

<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta charset="UTF-8">
    <title>Request Declined</title>
</head>
<body>
<p style="font-size:18px">Request <b>${requestStatus}</b>! -
    <%
    GroupMessageOrRequest communication = ((GroupMessageOrRequest) request.getAttribute("comm"));
    if(request.getAttribute("requestStatus") != null && request.getAttribute("requestStatus").equals("declined") ){
        out.print("you're not joining the group \"" + communication.getGroup().getDisplayName() + "\" ");
    }
    else {
        out.print("you're now a member of \"" + communication.getGroup().getDisplayName() + "\" group ");
    } %>
    <img height="20" src="/images/tick-icon.png">
</p>
</p>
<br/><br/>
<a href="../workspace"> Return to workspace </a>
</body>
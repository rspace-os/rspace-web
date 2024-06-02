<%@ include file="/common/taglibs.jsp"%>

<page:applyDecorator name="default">

<head>
    <title><fmt:message key="404.title"/></title>
    <meta name="heading" content="<fmt:message key='404.title'/>"/>
    <style>
    	.errorBlock {
    		width:449px;
    		padding:15px;
    		font-size:14px;
    		line-height:18px;
    	}
    </style>
</head>
<div id="errorContainer" style="margin-top:20px;">
<div class="errorBlock" style="margin:0px auto;">
    <img  src="<c:url value="/images/notFoundPageN.jpg"/>" alt="RSpace can't open the page" />
</div>
<div class="errorBlock" style="padding:25px;width:429px;margin:0px auto;">
    <fmt:message key="404.message">
        <fmt:param><c:url value="/workspace"/></fmt:param>
    </fmt:message>
</div>
</div>
</page:applyDecorator>
<%@ include file="/common/taglibs.jsp"%>

<page:applyDecorator name="default">

<head>
    <title><fmt:message key="404.title"/></title>
    <meta name="heading" content="<fmt:message key='404.title'/>"/>
    <style>
    	.errorBlock {
    		max-width: 449px;
    		padding: 15px;
    		margin: 0 auto;
    		font-size: 14px;
    		line-height: 18px;
    		text-align: center;
    	}
    	.errorBlock img {
    		max-width: 200px;
    	}
    	.errorBlock h2 {
    		color: #333;
    		margin: 15px 0 10px;
    	}
    </style>
</head>
<div id="errorContainer" style="margin-top:20px;">
<div class="errorBlock">
    <img src="<c:url value="/images/mainLogo3.svg"/>" alt="RSpace" />
    <h2><fmt:message key="404.title"/></h2>
</div>
<div class="errorBlock">
    <fmt:message key="404.message">
        <fmt:param><c:url value="/workspace"/></fmt:param>
    </fmt:message>
</div>
</div>
</page:applyDecorator>
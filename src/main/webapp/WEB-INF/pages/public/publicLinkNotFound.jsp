<%@ include file="/common/taglibs.jsp" %>

<head>
    <title>Published Link Not Found</title>
    <meta name="heading" content="Published Link Not Found"/>
    <meta name="robots" content="noindex, nofollow, noarchive">
    <style>
        .errorBlock {
            width: 449px;
            padding: 15px;
            font-size: 20px;
            line-height: 18px;
        }
    </style>
</head>
<div class="container">
    <div class="row">
        <div class="biggerLogoDiv" style="text-align: center">
            <img src="/public/images/mainLogoEnterpriseN2.png">
        </div>
        <div>
            <h1 class="form-signup-heading" style="text-align: center">Link not found</h1>
        </div>
        <div style="text-align: center">
            <img src="<c:url value="/public/images/404.jpg"/>"
                 alt=" We are sorry, it appears you have the wrong link or the link you have is to a document which is no longer published."/>
        </div>

        <div style="text-align: center;padding:25px;width:700px;margin:0px auto;"><h4>
            We are sorry, it appears you have the wrong link or the link you have is to a document which is no longer
            published.</h4> <H4>Please contact your source for the link.</H4>
        </div>
    </div>
</div>

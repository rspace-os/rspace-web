<!DOCTYPE html>

<%@ include file="/common/taglibs.jsp"%>

<html>
<head>
    <script>
      let currentUser = "${sessionScope.userInfo.username}";

      window.onload = function () {
        var loadTime = window.performance.timing.domContentLoadedEventEnd-window.performance.timing.navigationStart; 
        console.log('Page load time is '+ loadTime);
      }
    </script>
    <%@ include file="/common/meta.jsp"%>
    <title>
        <decorator:title /> | <fmt:message key="webapp.name" />
    </title>
    <link rel="stylesheet" href="<c:url value='/styles/bootstrap-custom-flat.css'/>" />
    <link media="all" href="<c:url value='/styles/simplicity/theme.css'/>" rel="stylesheet" />
    <link media="print" href="<c:url value='/styles/simplicity/print.css'/>" rel="stylesheet" />
    <link href="<c:url value='/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css'/>" rel="stylesheet" />
    <link href="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.css'/>" rel="stylesheet" />
    <link href="<c:url value='/styles/rs-global.css'/>" rel="stylesheet" />

    <script src="<c:url value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
    <script src="<c:url value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
    <script src="<c:url value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
    <script src="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.js'/>"></script>
    <script src="<c:url value='/scripts/bower_components/mustache/mustache.min.js'/>"></script>
    <script src="<c:url value='/scripts/jquery.toastmessage.js'/>"></script>
    <script src="<c:url value='/scripts/global.js'/>"></script>
    <script src="<c:url value='/scripts/global.settingsStorage.js'/>"></script>
    <jsp:include page="/scripts/templates/blockUI.html"/>
    <link rel="stylesheet" href="<c:url value='/styles/jquery.toastmessage.css'/>" />
    
    <decorator:head />

    <script defer src="<c:url value='/scripts/segment.js'/>"></script>

    <script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>
    <script>
        $(document).ready(function() {
            $("#page").fadeIn();
            $.fn.bootstrapButton = $.fn.button.noConflict();
            var viewRequest = $.get("/deploymentproperties/ajax/property?name=inventory.available");
            viewRequest.done(function (resp) {
                 if (resp !== 'ALLOWED') {
                        $('.inventory-tab').hide();
                 }
            });
        });
    </script>
    <script>

        </script>

</head>
<body
    <decorator:getProperty property="body.id" writeEntireProperty="true"/>
    <decorator:getProperty property="body.class" writeEntireProperty="true"/>>

    <div id="page" style="display:none">
        <jsp:include page="/common/header.jsp" />
        <div id="content" class="clearfix">
            <div id="main">
                <decorator:body />
            </div>

        </div>
        <div id="footer" class="clearfix">
            <jsp:include page="/common/footer.jsp" />
        </div>
    </div>
    
    <div id="fade" class="black_overlay"></div>
</body>
</html>

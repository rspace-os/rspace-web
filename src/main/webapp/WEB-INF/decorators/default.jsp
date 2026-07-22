<!DOCTYPE html>

<%@ include file="/common/taglibs.jsp"%>
<html lang="${fn:escapeXml(empty requestScope.rsResolvedLocaleTag ? 'en-US' : requestScope.rsResolvedLocaleTag)}">
<head>
    <script>
      let currentUser = "${sessionScope.userInfo.username}";

      window.onload = function () {
        var loadTime = window.performance.timing.domContentLoadedEventEnd-window.performance.timing.navigationStart;
        console.log('Page load time is '+ loadTime);
      }
    </script>
    <%@ include file="/common/meta.jsp"%>
    <rst:viteClient />
    <title>
        <sitemesh:write property='title'/> | <spring:message code="webapp.name" />
    </title>
    <link rel="stylesheet" href="<rst:assetUrl value='/styles/bootstrap-custom-flat.css'/>" />
    <link media="all" href="<rst:assetUrl value='/styles/simplicity/theme.css'/>" rel="stylesheet" />
    <link media="print" href="<rst:assetUrl value='/styles/simplicity/print.css'/>" rel="stylesheet" />
    <link href="<rst:assetUrl value='/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css'/>" rel="stylesheet" />
    <link href="<rst:assetUrl value='/scripts/bower_components/Apprise-v2/apprise-v2.css'/>" rel="stylesheet" />
    <link href="<rst:assetUrl value='/styles/rs-global.css'/>" rel="stylesheet" />

    <script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/bower_components/Apprise-v2/apprise-v2.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/bower_components/mustache/v420/mustache.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/jquery.toastmessage.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
    <script>RS.cacheVersion = '<rst:cacheVersion/>';</script>
    <rst:i18nMessages />
    <script src="<rst:assetUrl value='/scripts/global.settingsStorage.js'/>"></script>
    <jsp:include page="/scripts/templates/blockUI.html"/>
    <link rel="stylesheet" href="<rst:assetUrl value='/styles/jquery.toastmessage.css'/>" />

    <sitemesh:write property='head'/>

    <script defer src="<rst:assetUrl value='/scripts/segment.js'/>"></script>

    <script src="<rst:assetUrl value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>
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
<body id="<sitemesh:write property='body.id'/>" class="<sitemesh:write property='body.class'/>">

    <div id="page" style="display:none">
        <jsp:include page="/common/header.jsp" />
        <div id="content" class="clearfix">
            <div id="main">
                <sitemesh:write property='body'/>
            </div>

        </div>
        <div id="footer" class="clearfix">
            <jsp:include page="/common/footer.jsp" />
        </div>
    </div>

    <div id="fade" class="black_overlay"></div>
</body>
</html>

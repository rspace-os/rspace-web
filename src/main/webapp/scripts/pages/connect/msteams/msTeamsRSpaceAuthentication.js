
/* injecting ms theme to generic login/error page. teams config / simple document view pages are theming themselves */
function injectMsThemeIntoIframeBody() {

    var $iframe = $('#workspaceIframe');
    if ($iframe.size() > 0) {
        //console.log('injecting ms theme into iframe');
        var $iframeBody = $iframe.contents().find('body');
        if ($iframeBody.length) {
            var currentBodyClass = $iframeBody.attr('class');
            if (!currentBodyClass) {
                $iframeBody.append('<link href="/styles/pages/connect/msteams/msTeamsThemes.css" rel="stylesheet" />');
                applyMSThemeToBody($iframeBody);
                setTimeout(function() { $iframe.show(); }, 500);
            } 
            // the theme has changed
            if (currentBodyClass && !currentBodyClass.endsWith(currentMSTheme)) {
                applyMSThemeToBody($iframeBody);
            }
        }
    }

}

var iframeDisplayed = false;

$(document).ready(function() {

    var params = RS.getJsonParamsFromUrl(window.location.href);
    console.log('params retrieved', params);

    var targetUrl = '';
    var contentUrl = params.contentUrl;
    if (contentUrl === 'config') {
        targetUrl = '/msteams/tabConfig';
    } else {
        targetUrl = "/workspace/editor/structuredDocument/" + contentUrl + "?msTeamsDocView=true"
    };
    $('#workspaceIframe').attr('src', targetUrl);
    

    var switchIframeToFullPage = function() {
        $iframe = $('#workspaceIframe');
        $iframe.removeClass("maxHeight400").show();
    }

    var checkIfRSpaceAuthenticated = function () {
        var iframe = document.getElementById('workspaceIframe');
        var iframeUrl = iframe.contentWindow.location.href;
        var $iframeBody = $(iframe).contents().find('body');
        
        // it takes a moment to load the body into DOM
        if ($iframeBody.length) {
            var isLoginPageForm = $('.rs-sign-in-form', iframe.contentWindow.document).size() > 0; 
            var isErrorPage = $('body#error', iframe.contentWindow.document).size() > 0;
            var isTabConfigPage = $('body.msTeamsTabConfigPage, div#recordPickerTabConfigDiv', iframe.contentWindow.document).size() > 0;
            var isTargetPage = iframeUrl && iframeUrl.endsWith(targetUrl);

            if (isLoginPageForm || isErrorPage) {
                //console.log('showing login or error page');
                if (currentMSTheme) {
                    injectMsThemeIntoIframeBody();
                }
            } else if (isTabConfigPage) {
                // tab config page needs full redirect, as otherwise there is a problem with saving config
                window.location.href = targetUrl;
                
            } else if (isTargetPage) {
                // stay on the page
                console.log('showing target page: ' + iframeUrl);
                switchIframeToFullPage();
                clearInterval(authCheckInterval);
            } else {
                console.log('unknown page to show: ' + iframeUrl);
            }
        }
    };

    var authCheckInterval = setInterval(checkIfRSpaceAuthenticated, 200);

});
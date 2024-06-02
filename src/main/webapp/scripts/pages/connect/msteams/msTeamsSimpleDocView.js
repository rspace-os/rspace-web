
$(document).ready(function() {

    $('.intercom-launcher-frame,.intercom-launcher-discovery-frame').hide();

    // change breadcrumb links into normal spans to stop navigation
    $('.breadcrumbLink').each(function() {
        var $link = $(this); 
        $link.replaceWith('<span>' + $link.text() + '</span>');
    });

    // links should open in new window tabs to stop navigation
    $('a').each(function() {
       var href = $(this).attr('href');
       if (href !== '#') {
           console.log('marked link to ' + href + ' as target=_blank');
           $(this).attr('target','_blank');
       }
    });

    // add logout button that calls logout url and refreshes the page
    $('#simpleDocLogoutBtn').click(function() {
        $.get('/logout').fail(function(response) {
            if (response.status !== 403) {
                console.log('unexpected logout action result: ' + response.status);
                return true;
            }
            console.log('logged out, now reloading parent frame: ' + window.parent.location.href)
            window.parent.location.reload();
        });
        // also call google sign-out, if it's configured
        if (typeof gapi !== 'undefined' && gapi.auth2){
            var auth2 = gapi.auth2.getAuthInstance();
            if (auth2) {
                auth2.signOut();
            }
        }
    });

});
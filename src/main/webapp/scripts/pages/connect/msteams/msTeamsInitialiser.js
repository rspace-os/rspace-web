/**
 * Initialises teams app and loads styling for the theme
 */

var currentMSTheme;

var applyMSThemeToBody = function($body) {
    console.log('showing theme: ' + currentMSTheme);

    var $docBody = $body || $('body');
    $docBody.removeClass(
            'ms_theme_default ms_theme_dark ms_theme_contrast')
            .addClass('ms_theme_' + currentMSTheme);
};

var getMSTeamsContentUrlForDoc = function(docId) {
    return RS.createAbsoluteUrl() + '/msteams/rspaceAuthentication?contentUrl=' + docId
}

$(document).ready(function() {

    microsoftTeams.initialize();

    microsoftTeams.getContext(function(context) {
        if (context) {
            currentMSTheme = context.theme;
        }
        applyMSThemeToBody();
    });

    // set default theme for testing outside MS Teams iframe
    setTimeout(function() {
        // if theme not set yet, use default
        if (currentMSTheme === undefined) {
            currentMSTheme = 'default';
            applyMSThemeToBody();
        }
    }, 2000);

    microsoftTeams.registerOnThemeChangeHandler(function(theme) { 
        currentMSTheme = theme;
        applyMSThemeToBody();
    });

});
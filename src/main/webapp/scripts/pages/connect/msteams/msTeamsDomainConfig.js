/**
 * redirect functionality here
 */

$(document).ready(function() {

    var connectToAnotherServer = function() {
        var newServerUrl = $('#serverUrlInput').val();
        var configPageUrl = newServerUrl + '/msteams/rspaceAuthentication?contentUrl=config';
        console.log('redirecting through MS Teams to: ' + configPageUrl);
        microsoftTeams.navigateCrossDomain(configPageUrl);
        return false;
    };
    
    var currentServerUrl = RS.createAbsoluteUrl();
    $('#serverUrlInput').val(currentServerUrl);
    
    RS.addOnEnterHandlerToDocument("#serverUrlInput", connectToAnotherServer);
    $('#changeServerBtn').click(connectToAnotherServer);

});
var usersWithoutSid = [];
var sidRetrievalStopped = false;

function displayLdapSettings() {
    $('#mainArea').empty();
    $('#mainArea').text(RS.msg("legacyjs.system.ldap.loadingSettings"));

    var jqxhr = $.get('/system/ldap/ajax/ldapSettingsView');
    
    jqxhr.done(function (resp) {
        $('#mainArea').html(resp);

        if (ldapAuthenticationEnabled) {
            $('#ldapAuthenticationEnabledMsg').show();
        }
        if (ldapSidVerificationEnabled) {
            $('#ldapSidVerificationEnabledMsg').show();
            $('#sidRetrievalPanel').show();
            $('#runSidRetrieval').click(findUsersForSidRetrieval);
            $('#stopSidRetrieval').click(stopSidRetrieval).hide();
            usersWithoutSid = [];
            sidRetrievalStopped = false;
        }
    });
}

function findUsersForSidRetrieval() {
    $('#sidRetrievalResults').empty();
    
    $('#sidRetrievalResults').append(RS.msg("legacyjs.system.ldap.findingUsers"));
    var jqxhr = $.get('/system/ldap/ajax/ldapUsersWithoutSID');
    
    jqxhr.done(function(result) {
        if (result.data) {
            usersWithoutSid = result.data;
            $('#runSidRetrieval').attr("disabled", true);
            if (usersWithoutSid.length == 0) {
                $('#sidRetrievalResults').append(RS.msg("legacyjs.system.ldap.allUsersHaveSid"));
            } else {
                $('#stopSidRetrieval').show();
                $('#sidRetrievalResults').append(RS.msg("legacyjs.system.ldap.foundUsers", usersWithoutSid.length) + ' <br/><br/> ');
                runSidRetrievalForNextUser();
            }
        }
    });
    jqxhr.fail(function () {
        RS.ajaxFailed(RS.msg("legacyjs.system.ldap.usersQueryAction"), false, jqxhr);
   });
}

function runSidRetrievalForNextUser() {
    if (usersWithoutSid.length == 0) {
        $('#stopSidRetrieval').hide();
        $('#sidRetrievalResults').append(RS.msg("legacyjs.system.ldap.sidRetrievalComplete") + " <br/>");
        return;
    }
    if (sidRetrievalStopped) {
        $('#sidRetrievalResults').append("... " + RS.msg("legacyjs.system.ldap.sidRetrievalStopped") + " <br/>");
        return;
    }
    
    var nextUsername = usersWithoutSid.shift(); 
    var data = { username : nextUsername };
    $('#sidRetrievalResults').append(RS.msg("legacyjs.system.ldap.processingUser", RS.escapeHtml(nextUsername)));
    
    var jqxhr = $.post('/system/ldap/retrieveSidForLdapUser', data);
    
    jqxhr.done(function(result) {
        if (result.data) {
            $('#sidRetrievalResults').append(RS.msg("legacyjs.system.ldap.sidFoundAndSaved", RS.escapeHtml(result.data)) + " <br/>");
        } else {
            $('#sidRetrievalResults').append("<strong>" + RS.msg("legacyjs.system.ldap.userNotFoundOrSidEmpty") + "</strong><br/>");
        }
    });
    jqxhr.fail(function () {
        $('#sidRetrievalResults').append("<strong>" + RS.msg("legacyjs.system.ldap.sidRetrievalRequestFailed") + "</strong><br/>");
    });
    jqxhr.always(function () {
        runSidRetrievalForNextUser();
    });
}

function stopSidRetrieval() {
    sidRetrievalStopped = true;
}

$(document).ready(function() {
    $(document).on('click', '#ldapSettingsLink', displayLdapSettings);
});

define(function() {

    var usersWithoutSid = [];
    var sidRetrievalStopped = false;
    
    function displayLdapSettings() {
        $('#mainArea').empty();
        $('#mainArea').text("loading LDAP settings...");

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
        
        $('#sidRetrievalResults').append("Finding users who authenticate through LDAP but don't have SID saved in RSpace yet... ");
        var jqxhr = $.get('/system/ldap/ajax/ldapUsersWithoutSID');
        
        jqxhr.done(function(result) {
            if (result.data) {
                usersWithoutSid = result.data;
                $('#runSidRetrieval').attr("disabled", true);
                if (usersWithoutSid.length == 0) {
                    $('#sidRetrievalResults').append('all users have non-empty SID assigned.');
                } else {
                    $('#stopSidRetrieval').show();
                    $('#sidRetrievalResults').append('found ' + usersWithoutSid.length + ' user(s). <br/><br/> ');
                    runSidRetrievalForNextUser();
                }
            }
        });
        jqxhr.fail(function () {
            RS.ajaxFailed("Users query failed", false, jqxhr);
       });
    }
    
    function runSidRetrievalForNextUser() {
        if (usersWithoutSid.length == 0) {
            $('#stopSidRetrieval').hide();
            $('#sidRetrievalResults').append("SID retrieval complete. <br/>");
            return;
        }
        if (sidRetrievalStopped) {
            $('#sidRetrievalResults').append("... SID retrieval stopped. <br/>");
            return;
        }
        
        var nextUsername = usersWithoutSid.shift(); 
        var data = { username : nextUsername };
        $('#sidRetrievalResults').append("Processing user '" + RS.escapeHtml(nextUsername) + "': ");
        
        var jqxhr = $.post('/system/ldap/retrieveSidForLdapUser', data);
        
        jqxhr.done(function(result) {
            if (result.data) {
                $('#sidRetrievalResults').append("SID found and saved in RSpace (" + RS.escapeHtml(result.data) + "). <br/>");
            } else {
                $('#sidRetrievalResults').append("<strong>user not found in LDAP, or found but their SID in LDAP is empty.</strong><br/>");
            }
        });
        jqxhr.fail(function () {
            $('#sidRetrievalResults').append("<strong>SID retrieval request failed.</strong><br/>");
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
    
});
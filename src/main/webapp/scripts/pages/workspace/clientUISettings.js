/*
Contains utility functions for saving client side settings to server, parsing them, setting default ones.
Used for things like saving the ordering settings for tree view.

To use this load clientUISettingsPref when document is ready, and then call parseClientUISettingsPref
*/
var clientUISettingsPref = ''; // Loaded from fileTreeBrowser.js
var clientUISettings = { /* default UI settings */
    showTreeInEditor : 'y',
    showRibbon : 'y',
    showRibbonWithTree : 'n',
    treeSort : 'modificationdate.DESC'
 };

function parseClientUISettingsPref() {
    if (typeof(clientUISettingsPref) == 'undefined' || clientUISettingsPref === '') {
      console.warn("Client UI settings weren't loaded from the server – which is OK if the user hadn't set them yet");
      return;
    }
    $.each(clientUISettingsPref.split(','), function(i, nameValPair) {
        var nameAndVal = nameValPair.split('=');
        clientUISettings[nameAndVal[0]] = nameAndVal[1];
    });
}

function updateClientUISetting(name, value) {
    if (clientUISettings[name] === value) {
        console.log('skipping update action, as ' + name + ' is already ' + value);
        return;
    }
    clientUISettings[name] = value;

    /* send to the server */
    var nameValuePairs = [];
    $.each(clientUISettings, function(name, val) {
        nameValuePairs.push(name + '=' + val);
    });
    var newPrefValue = nameValuePairs.join(',');

    var data = {
        preference: 'UI_CLIENT_SETTINGS',
        value: newPrefValue
    };
    var jqxhr = $.post("/userform/ajax/preference", data);
    jqxhr.done(function(data) {
        if (data.errorMsg) {
            apprise(data.errorMsg);
        } else {
            console.log('client UI settings saved, new value: ' +  data.data);
        }
    });
    jqxhr.fail(function() {
        RS.ajaxFailed("Saving UI settings", false, jqxhr);
    });
}

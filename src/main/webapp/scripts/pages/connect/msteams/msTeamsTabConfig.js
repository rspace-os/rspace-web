var saveButtonEnabled = false;
var selectedFileGlobalId;
var selectedFileId;
var selectedFileName;

$(document).ready(function() {

    // tabs page must be top level, otherwise save action doesn't work. don't even show if it's not top level
    if (RS.isPageEmbedded()) {
        try {
            parent.document; // may throw exception
            console.log("parent accessible, assuming displayed inside RSpace authentication page, hiding");
            $('body').empty();
            $('body').addClass('msTeamsTabConfigPage'); // but add marker class so we can recognise it's there
            return;
        } catch (e) {
            console.log("parent not accessible, assuming top frame inside MS Teams");
        }
    }

    microsoftTeams.settings.registerOnSaveHandler(function(saveEvent) {

        microsoftTeams.settings.setSettings({
            entityId : "" + selectedFileGlobalId,
            contentUrl :  getMSTeamsContentUrlForDoc(selectedFileId),
            suggestedDisplayName : "RSpace " + selectedFileGlobalId + ": " + selectedFileName,
            websiteUrl : RS.createAbsoluteUrl() + "/globalId/" + selectedFileGlobalId
        });
        saveEvent.notifySuccess();
    });

/* footer code */
    $('#currentServerUrl').text(window.location.host);

});

function fileSelected(globalId, name) {
    selectedFileGlobalId = globalId;
    selectedFileId = globalId.substring(2);
    selectedFileName = name;
    if (!saveButtonEnabled) {
        saveButtonEnabled = true;
        microsoftTeams.settings.setValidityState(true);
    }
};

function fileUnselected() {
    if (saveButtonEnabled) {
        saveButtonEnabled = false;
        microsoftTeams.settings.setValidityState(false);
    }
};
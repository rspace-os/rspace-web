// export options defined on dialog.open()
var exportDialogOptions;

function _convertObjectToArray(mustacheFormattedData, options) {
	 for (var prop in options){
         if (options.hasOwnProperty(prop)){
           mustacheFormattedData['rows'].push({
             'key' : prop,
             'value' : options[prop]
            });
         }
       }
}

function getExportSelectionFromSelectedDocuments(selectedDocuments) {
    var selectedDocuments = getSelectedIdsNamesAndTypes();
    var exportSelection = {
        'type': 'selection',
        'exportTypes': selectedDocuments.types,
        'exportNames': selectedDocuments.names,
        'exportIds': selectedDocuments.ids
    };
    return exportSelection;
}

function getExportSelectionFromUsername(username) {
    return {
        'type': 'user',
        'username': username
    };
}

function getExportSelectionFromGroupId(groupId, groupName) {
    return {
        'type': 'group',
        'groupId': groupId,
        'groupName': groupName
    };
}



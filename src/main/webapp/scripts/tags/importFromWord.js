function initWordChooserDlg() {
    
    var isNotebook = _isNotebook();
    if (!isNotebook) {
        initFolderChooser('-wordimport');
    }
    
    $('#wordDocChooserDlg').dialog({
        modal: true,
        autoOpen: false,
        title: "Import",
        width: 400,
        open : function() {
            if (!isNotebook) {
                _toggleWordFolderChooser($(this).data('config').listNotebooks);
            }
            $(this).find('.importfileType').text($(this).data('config').fileType);
            $(this).find('#wordDocImportOptions').toggle($(this).data('config').showImportOptions)
        },
        buttons: {
            Cancel: function() {$(this).dialog('close');},
            Import: function() {
              var fileType = $(this).data('config').fileType;
              var formSubmitted = _submitWordImportForm(fileType);
              if (formSubmitted) {
                  RS.blockingProgressBar.show({msg: "Importing files...", progressType:"rs-wordImporter"});
                  $(this).dialog('close');
              }
              RS.trackEvent("user:import:from_doc:workspace", { fileType });
            }
        }
    });
    
    $(".wordDocImportSelect").change(function(e, data) {
        _toggleWordFolderChooser();
    });
    
    
    _toggleWordOptionSelect();
}

var wordImportSelectedIdsNamesTypes = null;

function openWordChooserDlg(idsNamesTypesGetter, config) {
    wordImportSelectedIdsNamesTypes = idsNamesTypesGetter();
    config = config || {};
    $('#wordDocChooserDlg').dialog({title:config.title}).data("config",config).dialog('open');
}

function _isNotebook() {
    return typeof notebookId !== 'undefined';
}

function _toggleWordFolderChooser(listNotebooks) {
    if (_isNotebook()) {
        return;
    }
    //RSPAC-1761: evernote import generates folder, can't shoose notebook
    if(!listNotebooks) {
    	setFolderChooserDirListingParams('-wordimport', "showNotebooks=false");
    }
    $('#folderChooser-wordimport').toggle(!_isWordReplace());
   
    $('#folderChooserDesc-wordimport').html('to put the imported documents. Otherwise, they will be put in the current folder.');
}

function _toggleWordOptionSelect() {
    var isNotebook = _isNotebook();
    $('#wordDocImportEntrySelect').toggle(isNotebook);
    $('#wordDocImportRecordSelect').toggle(!isNotebook);
}

function _isWordReplace() {
    var $select = $("#wordDocImportRecordSelect");
    if (_isNotebook()) {
        $select = $("#wordDocImportEntrySelect");
    }
    return $select.val() === 'REPLACE';
}

function _isFormValid(fileType) {
    
    var isReplace = _isWordReplace();
    if (isReplace) {
        if (!wordImportSelectedIdsNamesTypes || wordImportSelectedIdsNamesTypes.ids.length === 0) {
            if (_isNotebook()) {
                apprise("The Notebook is empty, cannot use 'save into the current entry' option.");
            } else {
                apprise("A target document should be selected in Workspace for 'replace selected document' option.");
            }
            return false;
        } 
        if (wordImportSelectedIdsNamesTypes.ids.length > 1) {
            apprise("Just one target document should be selected in Workspace for 'replace selected document' option.");
            return false;
        }
        var selectedRecordType = wordImportSelectedIdsNamesTypes.types[0];
        if (!selectedRecordType || selectedRecordType.indexOf('NORMAL') < 0) {
            apprise("The record selected in Workspace is not a Basic Document and can't be used with 'replace selected document' option.");
            return false;
        }
        if ($('#wordImportFormFileInput').get(0).files.length > 1) {
            apprise("Only one " +fileType+" file may be imported with 'replace selected document' option.");
            return false;
        }
    }

    if ($('#wordImportFormFileInput').get(0).files.length === 0) {
        apprise("Please choose some "+fileType+" files to upload.");
        return false;
    }

    return true;
}

function _submitWordImportForm(fileType) {
    
    if (!_isFormValid(fileType)) {
        return false;
    }
    
    var $form = $("form#wordImportForm");
    var targetFolderId = $form.data("parentid");
    var val = $('#folderChooser-id-wordimport').val();
    if (val && val.length > 0) {
        targetFolderId = val.trim();
    }
    
    var recordToReplaceId = "";
    if (_isWordReplace()) {
        recordToReplaceId = wordImportSelectedIdsNamesTypes.ids[0];
    }
    $('#wordImportFormRecordToReplace').val(recordToReplaceId);

    var formData = new FormData($form[0]);
    var jqxhr = $.ajax({
       url: '/workspace/editor/structuredDocument/ajax/createFromWord/' + targetFolderId,
       type: 'POST',
       data: formData,
       cache: false,
       contentType: false,
       processData: false 
    });

    jqxhr.always(function () {
        RS.blockingProgressBar.hide();
    });

    jqxhr.done(function (aro) {
        var report = "";
        var names = [];
        if (aro.data != null) {
            $.each(aro.data, function(i, val) {
                names.push(val.name);
            });
            if (names.length > 0) {
                report = report + " These documents were converted: " + names.join(", ");
            }
        }
        if (aro.errorMsg != null && aro.errorMsg.errorMessages.length > 0) {
            report = report + "<br>These documents were not converted: "
                    + getValidationErrorString(aro.errorMsg);
        }
        if (aro.errorMsg == null || aro.errorMsg.errorMessages.length === 0) {
            RS.confirmAndNavigateTo("All documents imported, reloading page...",
                    'success', 3000, createURL('/workspace/' + targetFolderId));
        } else {
            apprise(report);
        }
    });

    jqxhr.fail(function (jqXHR) {
        RS.ajaxFailed("Importing Word Documents", true, jqXHR);
    });
    
    return true;
}

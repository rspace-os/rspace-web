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

}

function openWordChooserDlg(config) {
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
    $('#folderChooser-wordimport').show();

    $('#folderChooserDesc-wordimport').html('to put the imported documents. Otherwise, they will be put in the current folder.');
}

function _isFormValid(fileType) {
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
    var formData = new FormData($form[0]);
    formData.append("grandParentId", getGrandParentFolderId());
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

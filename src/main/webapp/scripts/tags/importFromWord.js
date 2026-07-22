function initWordChooserDlg() {

    var isNotebook = _isNotebook();
    if (!isNotebook) {
        initFolderChooser('-wordimport');
    }

    $('#wordDocChooserDlg').dialog({
        modal: true,
        autoOpen: false,
        title: RS.msg("legacyjs.core.word.importTitle"),
        width: 400,
        open : function() {
            if (!isNotebook) {
                _toggleWordFolderChooser($(this).data('config').listNotebooks);
            }
            $(this).find('.importfileType').text($(this).data('config').fileType);
        },
        buttons: {
            [RS.msg("legacyjs.common.cancel")]: function() {$(this).dialog('close');},
            [RS.msg("legacyjs.core.word.importTitle")]: function() {
              var fileType = $(this).data('config').fileType;
              var formSubmitted = _submitWordImportForm(fileType);
              if (formSubmitted) {
                  RS.blockingProgressBar.show({
                      msg: RS.msg("legacyjs.core.word.importing"),
                      progressType:"rs-wordImporter"
                  });
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
    //RSPAC-1761: evernote import generates folder, can't choose notebook
    if(!listNotebooks) {
        setFolderChooserDirListingParams('-wordimport', "showNotebooks=false");
    }
    $('#folderChooser-wordimport').show();

    $('#folderChooserDesc-wordimport').html(RS.msg("legacyjs.core.word.folderChooserDescription"));
}

function _isFormValid(fileType) {
    if ($('#wordImportFormFileInput').get(0).files.length === 0) {
        apprise(RS.msg("legacyjs.core.word.chooseFiles", fileType));
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
                report = report + RS.msg("legacyjs.core.word.converted", names.join(", "));
            }
        }
        if (aro.errorMsg != null && aro.errorMsg.errorMessages.length > 0) {
            report = report + RS.msg("legacyjs.core.word.notConverted",
                    getValidationErrorString(aro.errorMsg));
        }
        if (aro.errorMsg == null || aro.errorMsg.errorMessages.length === 0) {
            RS.confirmAndNavigateTo(RS.msg("legacyjs.core.word.allImported"),
                    'success', 3000, createURL('/workspace/' + targetFolderId));
        } else {
            apprise(report);
        }
    });

    jqxhr.fail(function (jqXHR) {
        RS.ajaxFailed(RS.msg("legacyjs.core.word.importAction"), true, jqXHR);
    });

    return true;
}

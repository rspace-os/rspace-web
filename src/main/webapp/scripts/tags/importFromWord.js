function initWordChooserDlg() {

    var isNotebook = _isNotebook();
    $(".wordDocImportSelect").off("change.wordImport").on("change.wordImport", _toggleWordFolderChooser);
    $("#wordDocImportEntrySelect").toggle(isNotebook);
    $("#wordDocImportRecordSelect").toggle(!isNotebook);
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
                _toggleWordFolderChooser();
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

var wordImportSelection = null;

function openWordChooserDlg(selectionGetter, config) {
    wordImportSelection = selectionGetter();
    config = config || {};
    $("#wordImportForm")[0].reset();
    $(".wordDocImportSelect").val("NEW");
    $('#wordDocChooserDlg').dialog({title:config.title}).data("config",config).dialog('open');
}

function _isNotebook() {
    return typeof notebookId !== 'undefined';
}

function _toggleWordFolderChooser() {
    if (_isNotebook()) {
        return;
    }
    $('#folderChooser-wordimport').toggle(!_isWordReplace());

    setFolderChooserPrompt('-wordimport', "legacyjs.core.word.folderChooserPrompt");
}

function _isWordReplace() {
    return $(_isNotebook() ? "#wordDocImportEntrySelect" : "#wordDocImportRecordSelect").val() === "REPLACE";
}

function _isFormValid(fileType) {
    if (_isWordReplace()) {
        if (!wordImportSelection || wordImportSelection.ids.length !== 1) {
            apprise(RS.msg("legacyjs.core.word.selectOneTarget"));
            return false;
        }
        if (!wordImportSelection.types[0] || wordImportSelection.types[0].indexOf("NORMAL") < 0) {
            apprise(RS.msg("legacyjs.core.word.basicDocumentRequired"));
            return false;
        }
        if ($('#wordImportFormFileInput')[0].files.length > 1) {
            apprise(RS.msg("legacyjs.core.word.oneFileRequired"));
            return false;
        }
    }
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
    if (!_isWordReplace() && val && val.length > 0) {
        targetFolderId = val.trim();
    }
    var formData = new FormData($form[0]);
    if (_isWordReplace()) {
        formData.append("recordToReplaceId", wordImportSelection.ids[0]);
    }
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
                report = report + RS.msg("legacyjs.core.word.converted", RS.formatList(names));
            }
        }
        if (aro.errorMsg != null && aro.errorMsg.errorMessages.length > 0) {
            report = report + RS.msg("legacyjs.core.word.notConverted",
                    getValidationErrorString(aro.errorMsg, null, true));
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

var LISTFORMS_BY_MODFICATIONDATE_DESC = "/workspace/editor/form/list?orderBy=modificationDate&sortOrder=DESC&userFormsOnly=true";

/* Set before the page navigates on purpose, so leaving does not ask about unsaved fields. */
var leavingFormEditor = false;

function initChangeFormIconDialog() {
  $('#changeFormIconDialog').dialog({
    title: RS.msg("legacyjs.workspace.form.changeIconDialogTitle"),
    resizable: true,
    autoOpen: false,
    height: 250,
    width: 350,
    modal: true,
    buttons: {
      [RS.msg("legacyjs.workspace.form.cancelButton")]: function () {
        $("#changeFormIconDialog").dialog('close');
      },
      [RS.msg("legacyjs.workspace.form.submitButton")]: function () {
        var cfg = {
          fileInputId: "#newIconImage",
          postParamName: "filex",
          postURL: "/workspace/editor/form/ajax/saveImage/" + recordId,
          "onloadSuccess": function (json) {
            $("#changeFormIconDialog").dialog('close');
            $('#iconImgSrc').attr('src', createURL('/image/getIconImage/' + json.data));
            RS.confirm(RS.msg("legacyjs.workspace.form.iconChanged"), "success", 3000);
          }
        };
        RS.submitIconUpload(cfg);
      }
    }
  });
}

$(document).ready(function () {

  initChangeFormIconDialog();

  $('#changeFormIconButton').click(function () {
    $('#changeFormIconDialog').dialog('open');
  });

  $('body').on('click', 'a.templateSharingForm_help', function () {
    $(this).next('p').show();
  });

  $('body').on('click', 'a.templateSharingForm_help_hide', function () {
    $(this).closest('p').hide();
  });

  $('#publishShareDlg').dialog({
    modal: true,
    autoOpen: false,
    width: 350,
    title: RS.msg("legacyjs.workspace.form.publishShareDialogTitle"),
    buttons: {
      [RS.msg("legacyjs.workspace.form.cancelButton")]: function () {
        $(this).dialog('close');
      },
      [RS.msg("legacyjs.workspace.form.okButton")]: function () {
        $(this).dialog('close');
        var $form = $('#rsFormSharingForm');
        var data = $form.serialize();
        $.ajax({
          url: $form.attr('action'),
          type: $form.attr('method'),
          dataType: 'json',
          data: data,
          success: function (response) {
            $('#publishingStatus').html(response.data);
            $('#publish').hide();
            $('#unpublish').show();
            stylePublishStatus(response.data);
            if (window.formFieldEditor) {
              window.formFieldEditor.markPublished();
            }
          },
          error: function (xhr, err) { alert(RS.msg("legacyjs.workspace.form.genericError")); }
        });
      }
    },
  });

  $(document).on("change", "#newIconImage", function (e) {
    e.preventDefault();
    var files = e.target.files; // FileList object      
    // This code generates a thumb-nail in the dialog.
    // Loop through the FileList and render image files as thumb-nails.
    for (var i = 0, f; f = files[i]; i++) {
      // Only process image files.
      if (!f.type.match('image.*')) {
        continue;
      }

      var reader = new FileReader();
      // Closure to capture the file information.
      reader.onload = (function (theFile) {
        return function (e) {
          // $('#msgAreaImage').hide();
          // Render thumbnail.
          var span = document.createElement('span');
          span.innerHTML = ['<img class="thumb" height="32" width="32" src="', e.target.result, '" title="', RS.escapeHtml(theFile.name), '"/>'].join('');
          document.getElementById('imagePreview').insertBefore(span, null);
        };
      })(f);
      // Read in the image file as a data URL.
      reader.readAsDataURL(f);
    }

  });

  if (editable != 'EDIT_MODE') {
    $('#save').hide();
    $('#unpublish').hide();
  } else {
    $('#tmp_tag').prop('disabled', false);

    $('#save').bootstrapButton().click(function () {
      saveForm();
    });

    $('#update').click(function () {
      updateForm();
    });

    $('#abandon').click(function () {
      abandonUpdateForm();
    });

    setUpPublishButtons();

    // The form field editor (formFieldEditor bundle) reports its field count, so Publish is offered
    // once the form has a field, saved or not: publishing saves the fields first.
    window.addEventListener('formFieldEditor:change', function (e) {
      if ($('#unpublish').is(':visible')) {
        return;
      }
      $('#publish').toggle(e.detail.fieldCount > 0);
    });

    $(window).on('beforeunload', function (e) {
      if (!leavingFormEditor && window.formFieldEditor && window.formFieldEditor.isDirty()) {
        var message = RS.msg("legacyjs.workspace.documentEdit.exitWithoutSavingConfirm");
        e.preventDefault();
        e.originalEvent.returnValue = message;
        return message;
      }
    });

    // release the edit lock whenever the page is left, however it is left
    $(window).on('pagehide', function () {
      unlockform();
    });
  }
});

function unlockform() {
  var url = createURL('/workspace/editor/form/unlockform');
  var data = new URLSearchParams({ id: recordId });
  if (navigator.sendBeacon && navigator.sendBeacon(url, data)) {
    return;
  }
  fetch(url, { method: 'POST', body: data, keepalive: true });
}

/*
 * Saves the form field editor's changes. With onlyIfDirty, a clean editor is not asked.
 * Resolves true when there is nothing left to save; false when the save failed, in which
 * case the editor stays open and dirty and shows why.
 */
function saveFieldEditor(onlyIfDirty) {
  var fieldEditor = window.formFieldEditor;
  if (!fieldEditor || (onlyIfDirty && !fieldEditor.isDirty())) {
    return Promise.resolve(true);
  }
  return fieldEditor.save().then(function () {
    return true;
  }, function () {
    return false;
  });
}

/**
 * Handles Ajax post of form publishing, and toggles display of publish / unpublish buttons.
 */
function setUpPublishButtons() {
  stylePublishStatus($('#publishingStatus').html());

  $('.publishAction').click(function (e) {
    e.preventDefault();
    var $button = $(this);
    var publishing = $button.text() === 'Publish';
    var data = {
      publish: publishing,
      templateId: recordId
    };
    if (publishing) {
      saveFieldEditor(true).then(function (saved) {
        if (!saved) {
          return;
        }
        //get the access control dialog
        $.get(createURL("/workspace/editor/form/ajax/publishAndShare?templateId=" + recordId), function (data) {
          $('#publishShareDlgContent').html(data);
          $('#publishShareDlg').dialog('open');
        });
      });
    } else {
      // unpublishing
      var jqxhr = $.post(createURL("/workspace/editor/form/ajax/publish"), data, function (response) {
        $('#publishingStatus').html(response.data);
        $('#unpublish').hide();
        $('#publish').show();
        stylePublishStatus(response.data);
      });
      jqxhr.fail(function () {
        RS.ajaxFailed(RS.msg("legacyjs.workspace.form.unpublishingFormAction"), false, jqxhr);
      });
    }
  });
}

function stylePublishStatus(statusString) {
  if (statusString == 'PUBLISHED') {
    $('#publishingStatus').css('color', 'green');
  } else if (statusString == 'UNPUBLISHED') {
    $('#publishingStatus').css('color', 'red');
  }
}

function getValidationErrorString(errorList) {
  var rc = "";
  for (var i = 0; i < errorList.errorMessages.length; i++) {
    rc += errorList.errorMessages[i];
    if (i != errorList.errorMessages.length - 1) {
      rc += "; ";
    }
  }
  return rc;
}

/*
 * Saves the fields, then the form, and goes to the list of forms
 */
function saveForm() {
  RS.blockPage(RS.msg("legacyjs.workspace.form.savingFormBlock"));
  saveFieldEditor(false).then(function (saved) {
    if (!saved) {
      RS.unblockPage();
      return;
    }
    saveFormTag().always(function () {
      var jqxhr = $.post(createURL("/workspace/editor/form/ajax/saveForm"), {
        templateId: recordId
      }, function (success) {
        if (success.data !== null) {
          leavingFormEditor = true;
          RS.unblockPage();
          window.location.assign(createURL(LISTFORMS_BY_MODFICATIONDATE_DESC));
        } else if (success.errorMsg !== null) {
          apprise(RS.msg("legacyjs.workspace.form.fieldErrors", getValidationErrorString(success.errorMsg)));
          RS.unblockPage();
        } else {
          RS.unblockPage();
        }
      }, "json");

      jqxhr.fail(function () {
        RS.unblockPage();
        RS.ajaxFailed(RS.msg("legacyjs.workspace.form.savingFormAction"), true, jqxhr);
      });
    });
  });
}

/*
 * Saves the fields if they changed, then makes the draft the new version of the form
 */
function updateForm() {
  RS.blockPage(RS.msg("legacyjs.workspace.form.updatingFormBlock"));
  saveFieldEditor(true).then(function (saved) {
    if (!saved) {
      RS.unblockPage();
      return;
    }
    var jqxhr = $.post(createURL("/workspace/editor/form/ajax/updateForm"), {
      templateId: recordId
    }, function (data) {
      RS.unblockPage();
      if (data.data !== null) {
        leavingFormEditor = true;
        $('#noMessages').html(RS.msg("legacyjs.workspace.form.formUpdatedMessage"));
        setTimeout(function () {
          window.location = createURL(LISTFORMS_BY_MODFICATIONDATE_DESC);
        }, 1000);
      } else if (data.errorMsg !== null) {
        apprise(RS.msg("legacyjs.workspace.form.fieldErrors", getValidationErrorString(data.errorMsg)));
      }
    }, "json");

    jqxhr.fail(function () {
      RS.unblockPage();
      RS.ajaxFailed(RS.msg("legacyjs.workspace.form.updatingFormAction"), true, jqxhr);
    });
  });
}

/*
 * Asks, then discards the draft and every change made since the form was last published
 */
function abandonUpdateForm() {
  var confirmed = window.formFieldEditor ? window.formFieldEditor.confirmAbandon() : Promise.resolve(true);
  confirmed.then(function (abandon) {
    if (!abandon) {
      return;
    }
    RS.blockPage(RS.msg("legacyjs.workspace.form.abandoningFormUpdatesBlock"));
    var jqxhr = $.post(createURL("/workspace/editor/form/ajax/abandonUpdateForm"), {
      templateId: recordId
    }, function (data) {
      RS.unblockPage();
      if (data.data !== null) {
        leavingFormEditor = true;
        window.location = createURL(LISTFORMS_BY_MODFICATIONDATE_DESC);
      } else if (data.errorMsg !== null) {
        apprise(RS.msg("legacyjs.workspace.form.fieldErrors", getValidationErrorString(data.errorMsg)));
      }
    }, "json");

    jqxhr.fail(function () {
      RS.unblockPage();
      RS.ajaxFailed(RS.msg("legacyjs.workspace.form.revertingFormAction"), true, jqxhr);
    });
  });
}

//using function expression
var saveFormTag = function saveTmpTag() {
  var tagTxt = $('#tmp_tag').val();
  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/savetag"), { recordId: recordId, tagtext: tagTxt }, function (data) {
    if (!data.data) {
      apprise(getValidationErrorString(data.error));
    }
  });
  jqxhr.fail(function () {
    RS.ajaxFailed(RS.msg("legacyjs.workspace.form.savingFormTagsAction"), false, jqxhr);
  });
  return jqxhr;
};


/* jshint maxerr: 200 */

/* Script file with methods used when editing a document. */

var fieldsSynchronized = false;
var wasAutosaved = false;
var autoSaveIntervalId;
var autoSaveLastErrorStatus = -1;
var autoSaveFailureCount = 0;
var autoSaveSkipCount = 0;
var autosaveRequestTimeout = 30000; // will increase on failed autosaves
var chemistryAvailable = false;

var unchangedContentCounter = 0;
var isWorkspacePage = true;

function registerDblClickHandlersOnDocumentFields() {
  /* Handles double click around the text field area and others fields by switching them to edit mode.
   * Dblclick is ignored on some specific elements, and the ones marked with 'ignoreDblClick' class */
  $(document).on("dblclick", ".field-value-inner > div", function (e) {
    var $target = $(e.target);
    var targetClass = $target.attr("class");
    var $clickHandlerDiv = $(this);
    _removeSelections();

    if ($target.hasClass("ignoreDblClick")) {
      // ignore
    } else if (e.target == "[object HTMLImageElement]") {
      if (
        targetClass == "commentIcon" ||
        targetClass == "videoDropped" ||
        targetClass == "AudioDropped"
      ) {
        // do nothing
      } else {
        _editTextFieldAfterDblClickEvent(e, $clickHandlerDiv);
      }
    } else if (
      e.target == "[object HTMLObjectElement]" ||
      targetClass == "linkedRecord"
    ) {
      // do nothing
    } else if ($clickHandlerDiv.hasClass("textFieldViewModeDiv")) {
      _editTextFieldAfterDblClickEvent(e, $clickHandlerDiv);
    } else {
      console.log("dblclick on unrecognized element, ignoring", this);
    }
  });

  // handler for string, date and number fields
  $(document).on("dblclick", ".field-value-inner .plainTextField", function () {
    _removeSelections();
    var id = $(this).closest("tr.field").attr("id").split("_")[1];
    editInputField(id);
  });

  // handler for checkbox / radio buttons
  $(document).on("dblclick", ".field-value-inner .checkboxText", function () {
    _removeSelections();
    var type = $(this).closest("tr.field").attr("name");
    var id = $(this).closest("tr.field").attr("id").split("_")[1];
    if (type == "choice") {
      editFieldByClassChoice(id);
    } else {
      editFieldByClassRadio(id);
    }
  });
}

/* opens the editor and scrolls it to dbl-clicked fragment */
function _editTextFieldAfterDblClickEvent(e, $clickHandlerDiv) {
  var textFieldId = $clickHandlerDiv
    .closest("tr.field")
    .attr("id")
    .split("_")[1];
  var scrollToCallback = RS.tinymceScrollHandler.getScrollToCallback(
    $(e.target),
    textFieldId
  );
  var fieldInitPromise = editTextField(textFieldId);
  fieldInitPromise.done(scrollToCallback);
}

/* gets called when field is opened for edit  */
function _removeSelections() {
  if (document.selection && document.selection.empty) {
    document.selection.empty();
  } else if (window.getSelection) {
    window.getSelection().removeAllRanges();
  }
}

/* returns a promise of edit lock */
function requestEditStatus(editable, recordId) {
  var deferred = $.Deferred();
  var promise = deferred.promise();

  if (editable == "EDIT_MODE") {
    hasEditLock = true;
    deferred.resolve();
  } else if (
    editable == "VIEW_MODE" ||
    editable == "CANNOT_EDIT_OTHER_EDITING"
  ) {
    RS.blockPage("Requesting edit lock");
    var jqxhr = $.post(
      createURL("/workspace/editor/structuredDocument/ajax/requestEdit"),
      { recordId: recordId },
      function (data) {
        if (data) {
          window.editable = data;
          displayStatus(window.editable);
        }
        if (data == "EDIT_MODE") {
          hasEditLock = true;
          deferred.resolve();
        } else {
          var cantEditMsg = "Sorry, you can't edit the document";
          if (data == "CANNOT_EDIT_OTHER_EDITING") {
            cantEditMsg +=
              " now - it is currently being edited by " +
              (editor ? editor : "someone else");
          }
          apprise(cantEditMsg);
          deferred.reject();
        }
      }
    );
    jqxhr.always(function () {
      RS.unblockPage();
    });
    jqxhr.fail(function () {
      RS.ajaxFailed("Request for edit lock", false, jqxhr);
    });
  }
  return promise;
}

/* Sets a simple field (date, number, string) to be editable */
function editInputField(fieldId) {
  var editStatusPromise = requestEditStatus(editable, recordId);
  editStatusPromise.done(function () {
    updateFieldsContentFromServer().done(function () {
      disableLastFields();

      $("#" + fieldId).attr("disabled", false);
      $(".inputField." + fieldId).show();
      $(".plainTextField." + fieldId).hide();
      updateMenuUponEditStart(fieldId);

      lastInputEdited = fieldId;
      $.scrollTo("#field_" + fieldId, 500);
    });
  });
}

function editFieldByClassRadio(fieldId) {
  var editStatusPromise = requestEditStatus(editable, recordId);
  editStatusPromise.done(function () {
    updateFieldsContentFromServer().done(function () {
      disableLastFields();

      $(".radioLi." + fieldId).show();
      $(".select2:has(#select2-"+fieldId+"-container)").show();
      $("#radioText_" + fieldId).hide();

      lastRadioEdited = fieldId;
      updateMenuUponEditStart(fieldId);
      $.scrollTo("#field_" + fieldId, 500);
    });
  });
}

function editFieldByClassChoice(fieldId) {
  var editStatusPromise = requestEditStatus(editable, recordId);
  editStatusPromise.done(function () {
    updateFieldsContentFromServer().done(function () {
      disableLastFields();

      $(".choiceLi." + fieldId).show();
      $("#choiceText_" + fieldId).hide();

      lastCheckboxEdited = fieldId;
      updateMenuUponEditStart(fieldId);
      $.scrollTo("#field_" + fieldId, 500);
    });
  });
}

function editTextField(fieldId) {
  var editorInitialised = $.Deferred();
  var textFieldId = getTextFieldId(fieldId);

  // don't reload tinyMCE if field is already opened (RSPAC-965)
  if (tinyMCE.activeEditor && tinyMCE.activeEditor.id === textFieldId) {
    console.log("editor already opened for field " + textFieldId);
    runAfterTinymceActiveEditorInitialized(function () {
      editorInitialised.resolve();
    });
    return editorInitialised.promise();
  }

  var editStatusPromise = requestEditStatus(editable, recordId);
  editStatusPromise.done(function () {
    updateFieldsContentFromServer().done(function () {
      disableLastFields();
      $("#div_" + textFieldId)
        .html("")
        .hide();
      $("#" + textFieldId).show();
      toggleMobilePhotoField(true, textFieldId);

      updateMenuUponEditStart(fieldId);
      initTinyMCE(textFieldId).done(function () {
        editorInitialised.resolve();
        updateActiveEditorContentWithRetrievedMediaFileInfos();
        _scrollBrowserWindowToField(fieldId);
      });
      lastTextEdited = fieldId;
    });
  });
  return editorInitialised.promise();
}

function _scrollBrowserWindowToField(fieldId) {
  // if window is scrolled below the field starting point, scroll back to the beginning of the field
  var fieldTopRelativeToViewport = $("#field_" + fieldId)
    .get(0)
    .getBoundingClientRect().top;
  if (fieldTopRelativeToViewport < 0) {
    $(window).scrollTop($("#field_" + fieldId).offset().top);
  }

  // otherwise, if field starts in the bottom part of the window, scroll it up to the middle
  var windowHeight = $(window).height();
  var fieldTopDistanceFromWindowBottom =
    $(window).height() - fieldTopRelativeToViewport;
  if (fieldTopDistanceFromWindowBottom < windowHeight * 0.5) {
    $(window).scrollTop(
      $("#field_" + fieldId).offset().top - windowHeight * 0.5
    );
  }
}

function updateMenuUponEditStart(fieldId) {
  $("." + fieldId).attr("disabled", false);
  $("#saveandclose_" + fieldId).show();
  $("#view_" + fieldId).show();
  $("#saveandnew_" + fieldId).show();

  $("#edit_" + fieldId).hide();
  $("#stopEdit_" + fieldId).show();
  $("#signDocument, #delete, .templateActionDiv").hide();

  RS.checkToolbarDividers(".toolbar-divider");
}

function _updateMenuUponEditStop() {
  $(".editMode").hide();
  $("#signDocument, #delete, .templateActionDiv").show();
  RS.checkToolbarDividers(".toolbar-divider");
}

/*
 * Gets updated fields, in case record has been modified on the server,
 * perhaps by another user or the same user in a different window.
 *
 * returns a promise
 */
function updateFieldsContentFromServer(forceUpdate) {
  // if edit lock is kept and we just switch the fields, then skip the synchronization
  if (fieldsSynchronized && !forceUpdate) {
    return $.Deferred().resolve().promise();
  }

  console.log("getting updated fields from the server");
  var modificationDate = $("#lastModificationRecord").val();
  var data = {
    recordId: recordId,
    modificationDate: modificationDate,
  };

  RS.blockPage("Retrieving latest field content");
  var jqxhr = $.get(
    createURL("/workspace/editor/structuredDocument/ajax/getUpdatedFields"),
    data
  );
  jqxhr.done(function (data) {
    _updateFields(data.data);
    renderToolbar({ canSign: data.data.every(f => f.mandatoryStateSatisfied) && data.data.length > 0 });

    /* if user have the lock the content won't be edited by others (RSPAC-418) */
    if (hasEditLock) {
      fieldsSynchronized = true;
    }
  });
  jqxhr.always(function () {
    RS.unblockPage();
  });
  return jqxhr;
}

/*
 * Replaces all field data with newly loaded data from the server.
 * This is called when beginning to edit a document, or after save.
 * @param fields A List of Fields in JSON format
 */
function _updateFields(fields) {
  // disable active field, but define the callback to re-activate it later
  var activeFieldId = -1;
  var reactivateField = null;
  if (hasEditLock) {
    if (lastTextEdited != -1) {
      activeFieldId = lastTextEdited;
      reactivateField = function () {
        editTextField(activeFieldId).done(function () {
          if (tinyMCE.activeEditor) {
            tinyMCE.activeEditor.focus();
          }
        });
      };
      disableLastTextField();
    } else if (lastCheckboxEdited != -1) {
      activeFieldId = lastCheckboxEdited;
      reactivateField = function () {
        editFieldByClassChoice(activeFieldId);
      };
      disableLastCheckboxField();
    } else if (lastRadioEdited != -1) {
      activeFieldId = lastRadioEdited;
      reactivateField = function () {
        editFieldByClassRadio(activeFieldId);
      };
      disableLastRadioField();
    } else if (lastInputEdited != -1) {
      activeFieldId = lastInputEdited;
      reactivateField = function () {
        editInputField(activeFieldId);
      };
      disableLastInputField();
    }
  }

  // update the fields, and last modification date
  var lastModificationDate = $("#lastModificationRecord").val();
  $.each(fields, function (i, field) {
    if (field.type == "TEXT") {
      get$textField(field.id).val(field.fieldData);
      updateRichTextViewModeContent(getTextFieldId(field.id), field.fieldData);
      return;
    }

    if (field.type == "CHOICE") {
      $("[name=fieldSelectedChoicesFinal_" + field.id + "]").prop(
        "checked",
        false
      );
      $.each(field.choiceOptionSelectedAsList, function (j, val) {
        $("[name=fieldSelectedChoicesFinal_" + field.id + "]").each(
          function () {
            if ($(this).val() == val) {
              $(this).prop("checked", true);
            }
          }
        );
      });
      $("#choiceText_" + field.id).text(field.choiceOptionSelectedAsString);
      return;
    }

    if (field.type == "RADIO") {
      $("." + field.id).prop("checked", false);
      $("[name=fieldDefaultRadioFinal_" + field.id + "]").each(function () {
        if ($(this).val() == field.fieldData) {
          $(this).prop("checked", true);
        }
      });
      $("#radioText_" + field.id).text(field.fieldData);
      return;
    }

    //string, number, date, time
    $("#" + field.id).val(field.fieldData);
    setInputFieldViewModeValue(field.id, field.fieldData);

    if (field.modificationDate > lastModificationDate) {
      lastModificationDate = field.modificationDate;
    }
  });

  $("#lastModificationRecord").val(lastModificationDate);

  // re-enable previously active field
  if (typeof reactivateField === "function") {
    reactivateField();
  }
}

/* this is called after an 'import file' or drag & drop */
function insertContentAfterFileUpload(activeEditorId, result) {
  var template = "";
  var json = {};
  if (result.type == "Video" || result.type == "Audio") {
    var videoHTML = "";
    if (isPlayableOnJWPlayer(result.extension)) {
      videoHTML = setUpJWMediaPlayer(result.id, result.name, result.extension);
    }
    json.videoHTML = videoHTML;
    json.id = result.id;
    json.filename = result.name;
    json.extension = result.extension;
    json.imgClass = result.type == "Video" ? "videoDropped" : "audioDropped";
    json.iconSrc = RS.getIconPathForExtension(result.extension);
    json.compositeId = activeEditorId + "-" + result.id;

    template = "avTableForTinymceTemplate";
  } else if (result.type == "Image") {
    json.milliseconds = result.modificationDate;
    json.itemId = result.id;
    json.fieldId = activeEditorId;
    json.name = result.name;
    json.width = result.widthResized;
    json.height = result.heightResized;
    json.rotation = result.rotation;

    template = "insertedImageTemplate";
  } else if (result.type == "Document" || result.type == "Miscellaneous" || result.type == "Chemistry") {
    json.id = result.id;
    json.name = result.name;
    json.iconPath = RS.getIconPathForExtension(result.extension);

    template =
      result.type == "Document"
        ? "insertedDocumentTemplate"
        : "insertedMiscdocTemplate";
  }

  if (template) {
    RS.insertTemplateIntoTinyMCE(template, json);
  }
}

var saveInProgress = false; // set to 'true' at the start of save
var autosaveInProgressPromise; // the promise of currently running autosave
var runAnotherAutosave = false; // should another autosave be triggered after currently running one

function isAutosaveInProgress() {
  return (
    autosaveInProgressPromise !== undefined &&
    autosaveInProgressPromise.state() === "pending"
  );
}

// checks if autosave event should progress, be delayed or skipped.
function autosave(isPartOfSave) {
  if (editable !== "EDIT_MODE") {
    //console.log('not in EDIT_MODE, so skipping the autosave');
    return; // must be periodic autosave, it's fine to return nothing
  }
  if (saveInProgress && !isPartOfSave) {
    console.log(
      "save in progress, so skipping autosave request that doesn't come from save"
    );
    return;
  }
  if (isAutosaveInProgress()) {
    if (isPartOfSave) {
      // starting the save, but there is autosave running. lets attach our to run immediately after
      //console.log('binding part-of-save autosave to currently running promise');
      var deferred = $.Deferred();
      autosaveInProgressPromise
        .done(function () {
          //console.log('previous autosaved finished, running one being part of save');
          autosave(true)
            .done(function () {
              //console.log('scheduled part-of-save autosave completes');
              deferred.resolve();
            })
            .fail(function () {
              deferred.reject();
            });
        })
        .fail(function () {
          deferred.reject();
        });
      // also let's cancel queued autosave (if any), because the one being part of save will be enough
      runAnotherAutosave = false;
      return deferred.promise();
    } else {
      // one autosave is in progress, but we have periodic request for another. note it down by setting the flag
      //console.log('got request for another autosave, setting runAnotherAutosave to true');
      runAnotherAutosave = true;
      return;
    }
  }

  // no running save or autosave, so starting a new one
  if (isPartOfSave) {
    _resetAutosaveErrorHandling(true);
  } else if (_shouldAutoSaveBeSkipped()) {
    return;
  }

  return _autosaveFields();
}

function _shouldAutoSaveBeSkipped() {
  /* start skipping autosave attempts if ajax call continuously fails (RSPAC-1687) */
  if (autoSaveFailureCount > 3) {
    autoSaveSkipCount++;
    // start with 1 in 2 rate, then for every 10 failures reduce the rate, down to 1 in 6
    var allowRequestRate = Math.min(
      2 + Math.floor(autoSaveFailureCount / 10),
      6
    );
    var currentAttemptForRate = autoSaveSkipCount % allowRequestRate;
    if (currentAttemptForRate != 0) {
      console.log(
        "skipping autosave attempt due to continuous failures " +
          "(currently allowing 1 in " +
          allowRequestRate +
          ")"
      );
      return true;
    }
  }
  return false;
}

//returns a promise of autosaving all modified fields
function _autosaveFields() {
  var fieldPromises = [];
  $.each($(".field"), function (i, val) {
    var $field = $(val);
    var currentId, $fieldName;

    var wasFieldChanged = _getWasChangedFlagFromField($field);
    if (!wasFieldChanged) {
      return;
    }

    var attr = $field.attr("name");
    if (attr === "text") {
      var textFieldId = $field.find("textarea[name='fieldRtfData']").attr("id");
      currentId = getFieldIdFromTextFieldId(textFieldId);

      fieldPromises.push(_autosaveText($field));
    } else {
      currentId = $field.attr("id").split("_")[1];

      if (attr === "number") {
        fieldPromises.push(_autosaveNumber($field));
      } else if (attr === "string") {
        fieldPromises.push(_autosaveString($field));
      } else if (attr === "radio") {
        fieldPromises.push(_autosaveRadio($field));
      } else if (attr === "picklist") {
        fieldPromises.push(_autosavePickList($field));
      } else if (attr === "choice") {
        fieldPromises.push(_autosaveChoice($field));
      } else if (attr === "date") {
        fieldPromises.push(_autosaveDate($field));
      } else if (attr === "time") {
        fieldPromises.push(_autosaveTime($field));
      }
    }

    $fieldName = $("#field-name-" + currentId);
    showFieldNotification($fieldName, "Autosaving...");
    wasAutosaved = true;
  }); // end of .field loop

  autosaveInProgressPromise = $.when.apply($, fieldPromises);
  autosaveInProgressPromise.done(function () {
    if (runAnotherAutosave) {
      runAnotherAutosave = false;
      autosave();
    }
  });
  return autosaveInProgressPromise;
}

function _autosaveText($field) {
  var textArea$ = $field.find("textarea[name='fieldRtfData']");
  var dataV = textArea$.html();
  var textFieldId = textArea$.attr("id");
  if (dirtyFieldsMgr.isDirty(textFieldId)) {
    dataV = dirtyFieldsMgr.getDataForId(textFieldId);
  }
  var jqxhr = _postAutosave(dataV, $field);

  /* if field is on dirtyFieldsMgr list, then tinymce is not active anymore and we should wait
   * for successful autosave before clearing the field */
  jqxhr.done(function (data) {
    if (data.data === true) {
      if (dirtyFieldsMgr.isDirty(textFieldId)) {
        dirtyFieldsMgr.remove(textFieldId);
      }
    }
  });
  return jqxhr;
}

function _autosaveNumber($field) {
  var dataV = $field.find("input[name='fieldData']").val();
  return _postAutosave(dataV, $field);
}

function _autosaveString($field) {
  var dataV = $field.find("input[name='fieldData']").val();
  return _postAutosave(dataV, $field);
}

function _autosaveRadio($field) {
  var fieldId = $field.find("input[name='fieldId']").val();
  const radioSelected = $field.find("input[name=fieldDefaultRadioFinal_" + fieldId + "]:checked").val();
  return _postAutosave(radioSelected, $field);
}

function _autosavePickList($field) {
  var fieldId = $field.find("input[name='fieldId']").val();
  const pickListSelected = $field
      .find("option[class='radioLi " + fieldId + "' ]:selected")
      .val();
  return _postAutosave(pickListSelected, $field);
}

function _autosaveChoice($field) {
  var fieldId = $field.find("input[name='fieldId']").val();
  var selectedValues = $field.find(
    "input[name='fieldSelectedChoicesFinal_" + fieldId + "']:checked"
  );
  selectedValues =
    selectedValues === undefined
      ? ""
      : RS._serializeWithNoEscapes(selectedValues);
  return _postAutosave(selectedValues, $field);
}

function _autosaveDate($field) {
  var fieldId = $field.find("input[name='fieldId']").val();
  var dateData = $field.find("input[name='dateField_" + fieldId + "']").val();
  return _postAutosave(dateData, $field);
}

function _autosaveTime($field) {
  var fieldId = $field.find("input[name='fieldId']").val();
  var timeData = $field.find("input[name='timeField_" + fieldId + "']").val();
  return _postAutosave(timeData, $field);
}

function _postAutosave(dataValue, $field) {
  var fieldId = $field.find("input[name='fieldId']").val();
  var data = {
    dataValue: dataValue,
    fieldId: fieldId,
  };
  var jqxhr = $.ajax({
    type: "POST",
    url: "/workspace/editor/structuredDocument/ajax/autosaveField",
    data: data,
    dataType: "json",
    timeout: autosaveRequestTimeout,
  });
  jqxhr.done(function (data) {
    //console.log('autosave complete');
    if (data.data !== true) {
      apprise("Errors: " + getValidationErrorString(data.errorMsg, ";", true));
    }
    if (autoSaveFailureCount > 0) {
      // let user know that autosave recovered after connection failure
      RS.defaultConfirm("Autosave was successful");
      _resetAutosaveErrorHandling();
    }
  });
  jqxhr.fail(function () {
    _setWasChangedFlagOnField($field, true); // marking the field for next autosave attempt
    var errorStatus = jqxhr.status;
    if (errorStatus !== autoSaveLastErrorStatus) {
      // new problem with autosave, showing apprise to the user
      RS.ajaxFailed("Autosave ", false, jqxhr);
    } else {
      // user already acknowledged that particular autosave problem, so just show a toast
      RS.confirm(
        "Autosave attempt failed again (status: " + errorStatus + ")",
        "warning",
        4000
      );
    }
    autoSaveFailureCount++;
    autoSaveLastErrorStatus = errorStatus;
    autosaveRequestTimeout = Math.min(autosaveRequestTimeout + 15000, 60000); // increasing timeout up to 60s
  });

  /* clearing 'wasChanged' status ('isDirty' for active text fields) immediately rather than in callback,
   * because callback can be called much later, and user could change value again in the meantime */
  _setWasChangedFlagOnField($field, false);

  return jqxhr;
}

/* resets autosave error handling variables, so next incoming autosave error
 * will be treated as first ever occurrence of the error */
function _resetAutosaveErrorHandling(resetErrorStatus) {
  if (resetErrorStatus) {
    autoSaveLastErrorStatus = -1;
  }
  autoSaveFailureCount = 0;
  autoSaveSkipCount = 0;
}

function _setWasChangedFlagOnField($field, flagValue) {
  if ($field.attr("name") === "text") {
    var textFieldId = $field.find("textarea[name='fieldRtfData']").attr("id");
    if (tinyMCE.get(textFieldId)) {
      setTimeout(function () {
        tinyMCE.get(textFieldId).setDirty(flagValue);
      }, 0);
    }
  } else {
    $field.find("input[name='wasChanged']").val(flagValue);
  }
}

function _getWasChangedFlagFromField($field) {
  if ($field.attr("name") === "text") {
    var textFieldId = $field.find("textarea[name='fieldRtfData']").attr("id");

    // For any reason it takes wrong tinymce instances with wrong id, so let's check
    // id attribute from tinymce instance is the same that the textFieldId
    var tinyMCECurrentInstace = tinyMCE.get(textFieldId);
    var tinyMCEInstanceDirty =
      tinyMCECurrentInstace &&
      $(tinyMCECurrentInstace).attr("id") === textFieldId &&
      tinyMCECurrentInstace.isDirty();
    return dirtyFieldsMgr.isDirty(textFieldId) || tinyMCEInstanceDirty;
  } else {
    return $field.find("input[name='wasChanged']").val() === "true";
  }
}

function _wasAnyFieldChanged() {
  var changedFieldFound = false;
  $.each($(".field"), function (i, val) {
    var $field = $(val);
    changedFieldFound = _getWasChangedFlagFromField($field);
    if (changedFieldFound) {
      return false;
    }
  });
  return changedFieldFound;
}

function showFieldNotification($fieldName, message) {
  $fieldName
    .find(".fieldNotification")
    .queue(function (next) {
      $(this).html(message).hide();
      next();
    })
    .fadeIn(500)
    .delay(1000)
    .fadeOut(1000, function () {
      $(this).html("").show();
    });
}

function checkNumber(element) {
  var match = element.find("input[name='fieldData']");
  var value = match.val();
  var minValue = $(element).find("input[name='minValue']").val();
  var maxValue = $(element).find("input[name='maxValue']").val();
  var numberData = Number(value);

  var minNumber = Number(minValue);
  var maxNumber = Number(maxValue);
  var error = "";

  // if the minValue maxValue is valid then do default value validation,
  // otherwise the user will be confused
  if (isNaN(value)) {
    error = "Value [" + value + "] is not a number!";
  } else if (value != "" && maxValue != "" && numberData > maxNumber) {
    error = "Value greater than max value (" + maxValue + ").";
  } else if (value != "" && minValue != "" && numberData < minNumber) {
    error = "Value less than min value (" + minValue + "). ";
  }
  return error;
}

// marks it as modified for autosaving
function checkAndMarkField(element) {
  var currentId = $(element).attr("id");
  var $field = $("#field_" + currentId);
  _setWasChangedFlagOnField($field, true);
}

// checks if it has any error and marks it as modified for autosaving
function checkAndMarkNumber(element) {
  var currentId = $(element).attr("id");
  var $field = $("#field_" + currentId);
  var error = checkNumber($field);
  if (error == "") {
    $field
      .find("input[name='lastValue']")
      .val($field.find("input[name='fieldData']").val());
    _setWasChangedFlagOnField($field, true);
  } else {
    apprise(RS.escapeHtml(error));
    $field
      .find("input[name='fieldData']")
      .val($field.find("input[name='lastValue']").val());
  }
}

function cancelAutosavedEdits() {
  if (editable !== "EDIT_MODE") {
    apprise("You cannot cancel the current document");
    return;
  }
  apprise(
    "Cancelling will revert the document's content back to its state at the last save - please confirm that you want to proceed.",
    { confirm: true, textOk: "Yes, cancel", textCancel: "No, don't" },
    function () {
      RS.blockPage("Cancelling autosaved document edits");
      var jqxhr = $.post(
        createURL(
          "/workspace/editor/structuredDocument/ajax/cancelAutosavedEdits"
        ),
        {
          structuredDocumentId: recordId,
        },
        function (success) {
          if (success.data !== null) {
            var url = success.data;
            wasAutosaved = false;
            RS.confirmAndNavigateTo("Edits cancelled", "success", 1000, url);
          }
        },
        "json"
      );
      jqxhr.always(function () {
        RS.unblockPage();
      });
      jqxhr.fail(function () {
        RS.ajaxFailed("Cancel autosaved document edits", false, jqxhr);
      });
    }
  );
}

function saveStructuredDocument(close, unlock, implicitSave, urlToOpen) {
  if (editable !== "EDIT_MODE") {
    apprise("You cannot save the current document");
    displayStatus(editable);
    return;
  }

  if (saveInProgress) {
    console.log("there is already save in progress, skipping Save request");
    return;
  }

  var renamePromise = renameDocumentBeforeSaving();

  $.when(renamePromise).done(function () {
    // wait for the renaming promise
    saveInProgress = true;
    RS.blockPage("Autosaving...");
    var autosavePromise = autosave(true);
    autosavePromise.always(function () {
      RS.unblockPage();
    });
    autosavePromise.fail(function () {
      saveInProgress = false;
    });

    $.when(autosavePromise).done(function () {
      RS.blockPage("Saving...");
      var postData = {
        structuredDocumentId: recordId,
        unlock: unlock,
        settingsKey: settingsKey,
      };
      var jqxhr = $.post(
        createURL(
          "/workspace/editor/structuredDocument/ajax/saveStructuredDocument"
        ),
        postData,
        null,
        "json"
      );
      jqxhr.always(function () {
        RS.unblockPage();
        saveInProgress = false;
      });
      jqxhr.done(function (response) {
        if (response.data !== null) {
          var url = urlToOpen || response.data;
          wasAutosaved = false;

          // If we asked for it to be unlocked, it's now unlocked.
          if (unlock) {
            hasEditLock = false;
          }

          var msgToDisplay = "Document saved";
          var msgType = "success";
          var msgTime = 1000;

          if (
            response.error &&
            response.error.errorMessages[0] === "content.not.changed"
          ) {
            unchangedContentCounter++;
            msgToDisplay =
              "Document saved, but no change in content was detected";
            msgType = "warning";
            msgTime = 1500;
          } else {
            unchangedContentCounter = 0;
          }

          if (close && implicitSave && unchangedContentCounter) {
            // if its implicit save that doesn't result in changed content, then skip warning toast
            RS.navigateTo(url);
          } else if (close || (fromNotebook && unlock && basicDocument)) {
            // if closing, redirect to parent folder or notebook page,
            // or if 'save&view' for notebook basicDoc entry go to notebook view (RSPAC-1741)
            RS.confirmAndNavigateTo(msgToDisplay, msgType, msgTime, url);
          } else {
            // stay on the page, display a toast unless nothing changed after implicit save
            if (!implicitSave || !unchangedContentCounter) {
              if (unchangedContentCounter > 2) {
                apprise(
                  "The last few save actions didn't detect any changes to document content, is this expected? <br><br>" +
                    "If you <em>did</em> make changes to the content, then there may be a  communication problem between your " +
                    "browser and RSpace server, and your latest changes may be lost. Please copy and save your content outside " +
                    "RSpace, close and reopen the document, and check if everything is there.<br><br>" +
                    "If some content is missing, or if you keep seeing this message, please contact your System Admin."
                );
              }
              RS.confirm(msgToDisplay, msgType, msgTime * 3);
            }
          }

          if (unlock) {
            fieldsSynchronized = false;
            disableLastFields();
            _updateMenuUponEditStop();
            editable = "VIEW_MODE";
            displayStatus(editable);
          }

          if (!close) {
            updateFieldsContentFromServer(true);
          }
        }
      });
      jqxhr.fail(function () {
        RS.ajaxFailed("Save document", false, jqxhr);
      });
      displayStatus(editable);
    });
  });
}

function saveCopyStructuredDocument() {
  if (editable != "EDIT_MODE" || canCopy != true) {
    apprise(
      "You cannot perform 'Save and Copy' here - maybe you don't have  permission to create a document here"
    );
    return;
  }
  if (saveInProgress) {
    console.log(
      "there is already save in progress, skiping Save and Copy request"
    );
    return;
  }

  var renamePromise = renameDocumentBeforeSaving();

  $.when(renamePromise).done(function () {
    // wait for the renaming promise
    saveInProgress = true;
    RS.blockPage("Autosaving...");
    var autosavePromise = autosave(true);
    autosavePromise.always(function () {
      RS.unblockPage();
    });
    autosavePromise.fail(function () {
      saveInProgress = false;
    });

    $.when(autosavePromise).done(function () {
      RS.blockPage("Saving and copying...");
      var jqxhr = $.post(
        createURL(
          "/workspace/editor/structuredDocument/ajax/saveCopyStructuredDocument"
        ),
        {
          structuredDocumentId: recordId,
          recordName: recordName,
        },
        function (success) {
          if (success.data != null) {
            var url = success.data;
            wasAutosaved = false;
            hasEditLock = false;
            RS.confirmAndNavigateTo("Document saved", "success", 1000, url);
          }
        },
        "json"
      );
      jqxhr.always(function () {
        RS.unblockPage();
        saveInProgress = false;
      });
      jqxhr.fail(function () {
        RS.ajaxFailed("Save document", false, jqxhr);
      });
    });
  });
}

function saveNewStructuredDocument() {
  if (editable != "EDIT_MODE" || canCopy != true) {
    apprise(
      "You cannot perform 'Save and New' here - maybe you don't have permission to create a document here"
    );
    return;
  }
  if (saveInProgress) {
    console.log(
      "there is already save in progress, skiping Save and New request"
    );
    return;
  }

  var renamePromise = renameDocumentBeforeSaving();

  $.when(renamePromise).done(function () {
    // wait for the renaming promise
    saveInProgress = true;
    RS.blockPage("Autosaving...");
    var autosavePromise = autosave(true);
    autosavePromise.always(function () {
      RS.unblockPage();
    });
    autosavePromise.fail(function () {
      saveInProgress = false;
    });

    $.when(autosavePromise).done(function () {
      RS.blockPage("Saving and creating new...");
      var jqxhr = $.post(
        createURL(
          "/workspace/editor/structuredDocument/ajax/saveNewStructuredDocument"
        ),
        { structuredDocumentId: recordId },
        function (success) {
          if (success.data != null) {
            var url = success.data;
            wasAutosaved = false;
            hasEditLock = false;
            RS.confirmAndNavigateTo("Document saved", "success", 1000, url);
          } else if (success.errorMsg != null) {
            apprise(getValidationErrorString(success.errorMsg));
          }
        },
        "json"
      );
      jqxhr.always(function () {
        RS.unblockPage();
        saveInProgress = false;
      });
      jqxhr.fail(function () {
        RS.ajaxFailed("Save document", false, jqxhr);
      });
    });
  });
}

function unlockRecord() {
  // have we previously saved and unlocked the record? If so, don't do it again.
  if (!hasEditLock) {
    return;
  }
  console.log("unlocking record as window is closing...");
  var data = { id: recordId };
  $.post(
    createURL("/workspace/editor/structuredDocument/ajax/unlockrecord"),
    data,
    function (data) {
      console.log("edit lock removed for document " + recordId);
      hasEditLock = false;
    }
  );
}

// if any of the fields were changed or autosaved, but there was no full save
function checkForUnsavedChanges() {
  return wasAutosaved || _wasAnyFieldChanged();
}

$(document).ready(function () {
  if (canBeEditable()) {
    registerDblClickHandlersOnDocumentFields();

    autoSaveIntervalId = setInterval(autosave, 10000);

    $(window).bind("beforeunload", function (e) {
      if (checkForUnsavedChanges()) {
        var confirmationMsg =
          "Are you sure you want to exit without saving changes?";
        e.returnValue = confirmationMsg;
        return confirmationMsg;
      }
      unlockRecord(); // in Chrome works only when called from 'beforeunload' event
    });
    $(window).unload(function () {
      unlockRecord(); // in FF works only when called from 'unload' event
    });
  }
});

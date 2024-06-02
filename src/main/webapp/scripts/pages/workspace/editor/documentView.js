/* jshint maxerr: 200 */

var lastTextEdited = -1;
var lastCheckboxEdited = -1;
var lastRadioEdited = -1;
var lastInputEdited = -1;

var hasEditLock = false;

/*
 * Returns id, name and type of the record
 */
function getDocIdNameAndType() {
  return {
    ids: [recordId],
    names: [recordName],
    types: [recordType]
  };
}

/*
 * A manager for tracking dirty text fields. Field is marked as dirty when it's modified but not yet autosaved,
 * and is being hidden (i.e. user switches to another field). When a text field is hidden the tinyMEC editor
 * is removed and tinyMCE dirty tracker and state is lost, so this class acts as a cache for the tinyMCE state.
 */
function DirtyTextManager() {

  this._dirtyFieldList = [];

  /* Gets the index of text area's id in dirty array, or -1 if not found */
  this._dirtyListIndx = function (id) {
    var isInDirtyList = -1;
    $.grep(this._dirtyFieldList, function (value, indx) {
      if (id in value) {
        isInDirtyList = indx;
        return true;
      }
    });
    return isInDirtyList;
  };

  /* check if field with given id is dirty */
  this.isDirty = function (id) {
    return this._dirtyListIndx(id) != -1;
  };

  /* adds a field with given id and content as dirty */
  this.markDirty = function (id, html) {
    var id2Data = {};
    id2Data[id] = html;
    this._dirtyFieldList.push(id2Data);
    console.log("Field " + id + " added to _dirtyFieldList");
  };

  /* Removes an element from the dirty list (after saving) */
  this.remove = function (id) {
    var indx = this._dirtyListIndx(id);
    if (indx != -1) {
      this._dirtyFieldList.splice(indx, 1);
      console.log("Field " + id + " removed from _dirtyFieldList");
    }
  };

  /* Gets text field value for a given id, or "" if no text area of that id found */
  this.getDataForId = function (id) {
    var indx = this._dirtyListIndx(id);
    if (indx != -1) {
      return this._dirtyFieldList[indx][id];
    }
    return "";
  };
}

var dirtyFieldsMgr = new DirtyTextManager();

function getTextFieldId(fieldId) {
  return "rtf_" + fieldId;
}

function getFieldIdFromTextFieldId(textFieldId) {
  return textFieldId.substr("rtf_".length);
}

function get$textField(fieldId) {
  return $("#" + getTextFieldId(fieldId));
}

function disableEditing() {
  $('.editMode').hide();
  disableInputs();

  // Replace all textareas with a simple div
  $("textarea.tinymce").each(function (index, value) {
    var $textArea = $(value);
    var $parent = $textArea.parent();
    var text = $textArea.val();
    var textFieldId = $textArea.attr('id');
    var fieldId = getFieldIdFromTextFieldId(textFieldId);

    /* content is already escaped, but we escape it second time so potential scripts
     * are not executed on $parent.append (see RSPAC-782) */
    var escapedText = RS.escapeHtml(text);

    var $newNode = $('<div id="div_' + textFieldId + '" class="isResizable textFieldViewModeDiv"></>');
    $newNode.append(escapedText);
    $parent.append($newNode);

    // now when we appended double-escaped node let's safely show unescaped content
    showRichTextInViewMode(textFieldId, true);
    $textArea.hide();

    /* If a Basic Document (BDoc) is opened for edit, load tinymce immediately.
     * The edit lock is already obtained. That happens after creating new BDoc
     * from Workspace, or after opening BDoc entry for editing in notebook view */
    if (basicDocument && editable == 'EDIT_MODE') {
      editTextField(fieldId);
    }
  });
}

function showRichTextInViewMode(textFieldId, alreadyEscaped) {
  var $viewModeNode = $('#div_' + textFieldId);
  var text = $('#' + textFieldId).val();

  if (!alreadyEscaped) {
    var escapedText = RS.escapeHtml(text);
    $viewModeNode.append(escapedText);
  }

  updateRichTextViewModeContent(textFieldId, text);
  $viewModeNode.show();
}

function updateRichTextViewModeContent(textFieldId, newHtml) {
  var $viewModeNode = $('#div_' + textFieldId);
  $viewModeNode.html(newHtml);
  // some beautification of text field content
  updateAttachmentDivs($viewModeNode.find('.attachmentDiv'));
  updateInternalLinks($viewModeNode.find('a.linkedRecord'));
  updateEmbedIframes($viewModeNode.find('div.embedIframeDiv'));
  addImagePreviewToChems($viewModeNode.find('img.chem'));
  updateMediaFileThumbnails();
  reloadPhotoswipeImageArray();
  applyCodeSampleHighlights($viewModeNode);
  addDownloadButtonToTables($('.textFieldViewModeDiv table'));
  toggleMobilePhotoField(false, textFieldId);
  // Tell React that a new document was placed into the dom
  document.dispatchEvent(new CustomEvent('document-placed', {'detail': textFieldId}));
}

// find images on the page and use them to populate PhotoSwipe Gallery
function reloadPhotoswipeImageArray() {
  document.addEventListener('images-replaced', function (e) {
    populatePhotoswipeImageArray($("img.imageDropped, img.sketch, img.chem"));
  });
}

function toggleMobilePhotoField(toggleFlag, textFieldId) {
  if (DeviceMeta.isMobileOrTablet()) {
    $('#mobilePhoto_' + textFieldId).toggle(toggleFlag);
  }
}

function initAttachmentButton() {
  const publicView = $("#public_document_view").length > 0;
  if(!publicView) {
    $('.attachmentButton').bootstrapButton({
      icons: {primary: "ui-icon-mail-open"},
      text: false
    });
  }
  $(".attachmentButton").click(function () {
    if ($(".attachmentList").is(":hidden")) {
      _scanAllTextFieldsForAttachments();
    }
    $(".attachmentList").slideToggle();
  });
}

function _scanAllTextFieldsForAttachments() {
  var listAttachments = [];
  var $viewModeFields = $('.field-value-inner .isResizable');
  var $editModeField = $('.tox-edit-area > iframe').contents().find('#tinymce');
  var $textFields = $viewModeFields.add($editModeField);
  $textFields.each(function(idx, field) {
    try {
      var viewModeLinks = $(field).find('.attachmentPanel');
      viewModeLinks.each(function() {
        var $thisAttachment = $(this);
        var url = $thisAttachment.find('a.downloadActionLink')[0].href;
        var id = $thisAttachment.data('id');
        var name = $thisAttachment.find('.attachmentName').text();
        var data = { id: id, name: name, url: url };
        if (name && $.inArray(data, listAttachments) == -1) {
          listAttachments.push(data);
        }
      });

      var editModeLinks = $(field).find('.attachmentLinked');
      editModeLinks.each(function(i, l) {
        var name = $(l).text();
        var id = $(l).attr("id").split("_")[1];
        var url = l.href;
        var data = { id: id, name: name, url: url };
        if ($.inArray(data, listAttachments) == -1) {
          listAttachments.push(data);
        }
      });
    } catch (error) {
      console.log("text area for index " + idx + " could not be parsed by Jquery");
    }
  });

  $(".attachmentUL").html("");
  $.each(listAttachments, function(i, att) {
    var milliseconds = new Date().getTime();
    // removed 'download' attribute to pre
    var new_item = $('<li>');
    new_item.attr('id', "attach_" + att.id);
    new_item.attr('class', "attachmentLI");
    var ah = $('<a>');
    ah.attr('href', att.url);
    ah.attr('target', "_blank");
    // only add download attribute if not an external link to Box/dropbox etc RSPAC-1138
    if (att.url.indexOf('Streamfile') != -1) {
      ah.attr('download', att.name);
    }
    ah.text(att.name);
    new_item.append(ah);

    $(".attachmentUL").append(new_item);
  });

  if (listAttachments.length === 0) {
    $(".attachmentUL").append("The document has no attachments.");
  }
}

function initTopBarButtonsForEditableDoc() {
  $('#close').click(function (e) {
    if (editable === 'EDIT_MODE') {
      e.preventDefault();
      saveStructuredDocument(true, true, true); // save & close
    }
  });
  if (fromNotebook) {
    $('#close').attr('href', getDocumentViewUrl(fromNotebook, recordId, true));
  }

  $('#save').click(function (e) {
    e.preventDefault();
    saveStructuredDocument(false, false);
  });

  $('#saveClose').click(function (e) {
    e.preventDefault();
    saveStructuredDocument(true, true);
  });

  $('#saveView').click(function (e) {
    e.preventDefault();
    saveStructuredDocument(false, true);
  });

  $('#saveClone').click(function (e) {
    e.preventDefault();
    saveCopyStructuredDocument();
  });

  $('#saveNew').click(function (e) {
    e.preventDefault();
    saveNewStructuredDocument();
  });

  $('#cancel').click(function (e) {
    e.preventDefault();
    cancelAutosavedEdits();
  });

  $('.stopEditButton').click(function (e) {
    e.preventDefault();
    saveStructuredDocument(false, true);
  });

  initSaveAsTemplateDlg();
  $('#saveAsTemplateSaveMenuBtn, #saveAsTemplateBtn').click(function () {
    openSaveAsTemplateDlg();
  });

  $('#delete').click(function (e) {
    e.preventDefault();
    deleteStructuredDocument(recordId);
  });

  $(document).on('click', '.moreDeleteInfo', function () {
    $('#moreDeleteInfoContent, #moreDeleteInfoLnk').slideToggle("slow");
  });

  renderToolbar({ eventHandlers: { onCanSign: () => {
    $.get('/vfpwd/ajax/checkVerificationPasswordNeeded', function (response) {
      if (response.data) {
        apprise("Please set your verification password in <a href=\"/userform\" target=\"_blank\">My RSpace</a> before signing.");
      } else {
        $('#signDocumentDialog').data("recordId", recordId).dialog('open');
      }
    });
  }}});
}

function disableLastFields() {
  $('.editMode').show();
  disableInputs();
  disableLastTextField();
  disableLastCheckboxField();
  disableLastRadioField();
  disableLastInputField();
}

function disableLastTextField() {
  if (lastTextEdited != -1) {
    var lastTextFieldId = getTextFieldId(lastTextEdited);
    var $lastTextField = get$textField(lastTextEdited);
    var dataText = $lastTextField.html();

    // Rewrite the width and height of img src urls to their resized values if present
    var tempDiv = document.createElement('div');
    tempDiv.innerHTML = dataText;

    $(tempDiv).find("img").each(function () {
      var imgUri = $(this).attr("src");
      var widthAttr = $(this).attr("width");
      if (typeof widthAttr !== 'undefined') {
        imgUri = setQueryParam(imgUri, "width", $(this).attr("width"));
        imgUri = setQueryParam(imgUri, "height", $(this).attr("height"));
        $(this).attr("src", imgUri);
      }
    });

    dataText = tempDiv.innerHTML;

    var lastTextTinyMCE = tinyMCE.get(lastTextFieldId);
    if (lastTextTinyMCE && lastTextTinyMCE.isDirty()) {
      dirtyFieldsMgr.markDirty(lastTextFieldId, dataText);
    }

    //If you add dataText before remove tinyMCE instance pictures don't showed.
    tinyMCE.execCommand('mceRemoveControl', false, lastTextFieldId);
    tinyMCE.remove("#" + lastTextFieldId);
    showRichTextInViewMode(lastTextFieldId);
    $lastTextField.hide();

    var fieldId = getFieldIdFromTextFieldId(lastTextFieldId);
    $('#edit_' + fieldId).show();
    $('#stopEdit_' + fieldId).hide();

    _informUserIfFieldContentTooWide();
    lastTextEdited = -1;
  }
}

function _informUserIfFieldContentTooWide() {
  if (lastTextEdited != -1) {
    var viewModeNode = $('#div_' + getTextFieldId(lastTextEdited))[0];
    if (viewModeNode.scrollWidth > viewModeNode.clientWidth) {
      $().toastmessage('showToast', {
        text: 'The content of last edited field is wider than the page. It may affect PDF export and printout',
        type: 'warning',
        stayTime: 8000
      });
    }
  }
}

function setQueryParam(uri, key, value) {
  if (!uri.match(/\?/)) {
    uri += '?';
  }
  if (!uri.match(new RegExp('([\?&])' + key + '='))) {
    if (!uri.match(/[&\?]$/)) {
      uri += '&';
    }
    uri += key + '=' + escape(value);
  } else {
    uri = uri.replace(new RegExp('([\?\&])' + key + '=[^&]*'), '$1' + key + '=' + escape(value));
  }
  return uri;
}

function disableLastCheckboxField() {
  if (lastCheckboxEdited != -1) {
    var inputName = "fieldSelectedChoicesFinal_" + lastCheckboxEdited;
    var selectedValues = $('.' + lastCheckboxEdited + ':checked');
    if (selectedValues === undefined) {
      selectedValues = "";
    } else {
      selectedValues = RS._serializeWithNoEscapes(selectedValues, inputName);
    }
    var re = new RegExp("&" + inputName + "=", "g");
    selectedValues = selectedValues.replace(re, ', ');
    selectedValues = selectedValues.replace(inputName + '=', '');
    $("#choiceText_" + lastCheckboxEdited).text(selectedValues);
    $('.choiceLi.' + lastCheckboxEdited).hide();
    $('#choiceText_' + lastCheckboxEdited).show();
    $('#edit_' + lastCheckboxEdited).show();
    $('#stopEdit_' + lastCheckboxEdited).hide();
    lastCheckboxEdited = -1;
  }
}

function disableLastRadioField() {
  if (lastRadioEdited != -1) {
    var selectedValues = $('.' + lastRadioEdited + ':checked').val();
    if (selectedValues == undefined) {
      selectedValues = "";
    }
    $("#radioText_" + lastRadioEdited).text(selectedValues);
    $('.radioLi.' + lastRadioEdited).hide();
    $(".formPickList."+ lastRadioEdited).hide();
    $('#radioText_' + lastRadioEdited).show();
    $('#edit_' + lastRadioEdited).show();
    $('#stopEdit_' + lastRadioEdited).hide();
    lastRadioEdited = -1;
  }
}

function disableLastInputField() {
  if (lastInputEdited != -1) {
    var newValue = $('.inputField.' + lastInputEdited).val();
    setInputFieldViewModeValue(lastInputEdited, newValue);
    $('.inputField.' + lastInputEdited).hide();
    $('.plainTextField.' + lastInputEdited).show();
    $('#edit_' + lastInputEdited).show();
    $('#stopEdit_' + lastInputEdited).hide();
    lastInputEdited = -1;
  }
}

function setInputFieldViewModeValue(fieldId, value) {
  var escapedValue = RS.escapeHtml(value);
  $('.plainTextField.' + fieldId).empty().append(escapedValue);
}

function disableInputs() {
  $("#structuredDocument :radio").attr('disabled', true);
  $("#structuredDocument :text").attr('disabled', true);

  $(".choiceLi").hide();
  $(".radioLi").hide();
  $(".select2").hide();
  $(".inputField").hide();
}

function deleteStructuredDocument(recordId) {
  apprise("<div style='line-height:1.3em'>Do you want to delete the following document(s) ? - " + RS.escapeHtml(recordName) +
    ". <a href='#' id='moreDeleteInfoLnk' class='moreDeleteInfo'>Details...</a>" +
    "<div style='display:none;' id='moreDeleteInfoContent'>Deleting documents that you <em>own</em> will also delete them from the view of those you're sharing with.</br>" +
    " Deleting a document <em>shared with you</em> will only delete it from your view. <br/>" +
    "<a href='#' class='moreDeleteInfo'>Hide... </a></div></div>", {
      confirm: true,
      textOk: 'Delete'
    },
    function () {
      var jqxhr = $.post("/workspace/editor/structuredDocument/ajax/deleteStructuredDocument/" + recordId + "/",
        function (data) {
          var url = data.data;
          if (url != null) {
            window.location = createURL(url);
          } else if (data.errorMsg !== null) {
            apprise(getValidationErrorString(data.errorMsg));
          }
        });
      jqxhr.fail(function () {
        RS.ajaxFailed("Delete operation", false, jqxhr);
      });
    });
  RS.focusAppriseDialog(false);
}

function autoCheckEditableStatus() {
  if (editable === 'VIEW_MODE' || editable === 'CANNOT_EDIT_OTHER_EDITING') {
    $.get(createURL("/workspace/editor/structuredDocument/ajax/otherUserEditingRecord"), { recordId: recordId }, function (result) {
      // check status again, as it could change in the meantime (RSPAC-1616)
      if (editable === 'VIEW_MODE' || editable === 'CANNOT_EDIT_OTHER_EDITING') {
        var resultData = result.data;
        $('.fieldHeaderEditButton').toggle(resultData == null);
        if (!resultData) {
          editable = 'VIEW_MODE';
          isEditable = true;
        } else if (resultData && resultData.username !== currentUser) {
          editable = 'CANNOT_EDIT_OTHER_EDITING';
          editor = resultData.username;
          isEditable = false;
        }
        displayStatus(editable);
      }
    }, "json");
  }
}

/* called on document.ready if there is autosaved version of the document */
function _updateAutosavedFields(data) {
  $.each(data, function (i, value) {
    var $field = $("#field_" + value.id);
    var attr = $field.attr("name");

    if (attr == "date") {
      $field.find("input[name='dateField_" + value.id + "']").val(value.fieldData);
      $("#plainText_" + value.id).html(value.fieldData);
      return;
    }
    if (attr == "time") {
      $field.find("input[name='timeField_" + value.id + "']").val(value.fieldData);
      $("#plainText_" + value.id).html(value.fieldData);
      return;
    }
    if (attr == "choice") {
      var selectedValues = $field.find(
        "input[name='fieldSelectedChoicesFinal_" + value.id + "']:checked");
      $(selectedValues).prop('checked', false);
      $.each(value.choiceOptionSelectedAsList, function (j, val) {
        $.each($("input[name='fieldSelectedChoicesFinal_" + value.id + "']"), function () {
          var $option = $(this);
          if ($option.val() === val) {
            $option.prop("checked", true);
          }
        });
      });
      selectedValues = value.fieldData;
      if (selectedValues == undefined) {
        selectedValues = "";
      }
      $("#choiceText_" + value.id).text(value.choiceOptionSelectedAsList.join(','));
      return;
    }
    if (attr == "radio") {
      var selectedValues = $field.find(
        "input[name='fieldDefaultRadioFinal_" + value.id + "']:checked");
      $(selectedValues).prop('checked', false);
      $.each($("input[name='fieldDefaultRadioFinal_" + value.id + "']"), function () {
        var $option = $(this);
        if ($option.val() === value.fieldData) {
          $option.prop("checked", true);
        }
      });
      $("#radioText_" + value.id).text(value.fieldData);
      return;
    }
    if (attr == "text") {
      $field.find("textarea[name='fieldRtfData']").val(value.fieldData);
      var textFieldId = getTextFieldId(value.id);
      updateRichTextViewModeContent(textFieldId, value.fieldData);
      return;
    }
    if (attr == "string" || attr == "number") {
      $field.find("input[name='fieldData']").val(value.fieldData);
      $("#plainText_" + value.id).html(value.fieldData);
      return;
    }
    console.log('unknown attr: ' + attr);
  });
}

function loadDateFields() {
  $.each($(".field"), function (i, val) {
    var attr = $(val).attr("name");
    if (attr == "date") {
      loadDatePicker(val);
    }
  });
}

function loadTimeFields() {
  $.each($(".field"), function (i, val) {
    var attr = $(val).attr("name");
    if (attr == "time") {
      loadTimePicker(val);
    }
  });
}

function loadTimePicker(val) {
  var idDate = $(val).find("input[name='fieldId']").val();
  var formatTime = $(val).find("input[name='format_" + idDate + "']").val();

  var minTime = $(val).find("input[name='minTime']").val();
  minTime = parseInt(minTime);
  var maxTime = $(val).find("input[name='maxTime']").val();
  maxTime = parseInt(maxTime);
  var minHour = $(val).find("input[name='minHour']").val();
  minHour = parseInt(minHour);
  var minMinutes = $(val).find("input[name='minMinutes']").val();
  minMinutes = parseInt(minMinutes);
  var maxHour = $(val).find("input[name='maxHour']").val();
  maxHour = parseInt(maxHour);
  var maxMinutes = $(val).find("input[name='maxMinutes']").val();
  maxMinutes = parseInt(maxMinutes);

  var timepickerConfig = {
    ampm: (formatTime == "hh:mm a")
  };
  if (maxTime > 0) {
    timepickerConfig.maxDate = new Date(1970, 01, 02, maxHour, maxMinutes);
  }
  if (minTime > 0) {
    timepickerConfig.minDate = new Date(1970, 01, 02, minHour, minMinutes);
  }
  $("#" + idDate + ".timepicker").timepicker(timepickerConfig);
}

function loadDatePicker(val) {
  var idDate = $(val).find("input[name='fieldId']").val();
  var minDate = $(val).find("input[name='minValue']").val();
  minDate = parseInt(minDate);
  var maxDate = $(val).find("input[name='maxValue']").val();
  maxDate = parseInt(maxDate);
  var dateformat = $(val).find("input[name='format_" + idDate + "']").val();
  if (dateformat == "dd/MM/yyyy") {
    dateformat = "dd/mm/yy";
  }
  if (dateformat == "dd MM yyyy") {
    dateformat = "dd mm yy";
  }
  if (dateformat == "dd-MM-yyyy") {
    dateformat = "dd-mm-yy";
  }
  if (dateformat == "dd MMM yyyy") {
    dateformat = "dd M yy";
  }
  if (dateformat == "yyyy/MM/dd") {
    dateformat = "yy/mm/dd";
  }
  if (dateformat == "yyyy MM dd") {
    dateformat = "yy mm dd";
  }
  if (dateformat == "yyyy-MM-dd") {
    dateformat = "yy-mm-dd";
  }
  if (dateformat == "yyyy MMM dd") {
    dateformat = "yy M dd";
  }

  if (minDate > 0 && maxDate > 0) {
    $("#" + idDate + ".datepicker").datepicker({
      dateFormat: dateformat,
      minDate: new Date(minDate),
      maxDate: new Date(maxDate)
    });
  } else if (minDate > 0 && maxDate == 0) {
    $("#" + idDate + ".datepicker").datepicker({
      dateFormat: dateformat,
      minDate: new Date(minDate)
    });
  } else if (minDate == 0 && maxDate > 0) {
    $("#" + idDate + ".datepicker").datepicker({
      dateFormat: dateformat,
      maxDate: new Date(maxDate)
    });
  } else {
    $("#" + idDate + ".datepicker").datepicker({
      dateFormat: dateformat
    });
  }
}

function markTinyMCEAreaDroppable(manualCleanup) {
  markAreaDroppable('.tox-edit-area', manualCleanup);
}

function clearTinyMCEAreaDroppable() {
  clearAreaDroppable('.tox-edit-area');
}

// if user can edit the document now, or when another user finish edit
function canBeEditable() {
  return editable === 'VIEW_MODE' || editable === 'EDIT_MODE' || editable === 'CANNOT_EDIT_OTHER_EDITING';
}

$(document).ready(function () {
  //init dialogs
  initCommentDialog();
  initCommentViewDialog();
  initSignDialog("Signing Document");
  initWitnessDialog("Witnessing Document");
  const publicView = $("#public_document_view").length > 0;
  if (canBeEditable()) {
    // is editable, make buttons available
    if(!publicView) {
      window.addEventListener("ReactToolbarMounted", () => {
        initTopBarButtonsForEditableDoc();
        loadDateFields();
        loadTimeFields();
        disableEditing();
      });
    } else {
      initTopBarButtonsForEditableDoc();
      loadDateFields();
      loadTimeFields();
      disableEditing();
    }

    setInterval(autoCheckEditableStatus, 20000);
  } else {
    if(!publicView) {
      window.addEventListener("ReactToolbarMounted", () => {
        disableEditing();
        $('#delete, .templateActionDiv, .fieldHeaderEditButton').hide();
      });
    } else {
      disableEditing();
      $('#delete, .templateActionDiv, .fieldHeaderEditButton').hide();
    }
  }

  if (hasAutosave == 'true') {
    console.log('loading autosaved version of the document');
    var data = { recordId: recordId };
    $.get(createURL('/workspace/editor/structuredDocument/getAutoSavedFields'),
      data, function (result) {
        runAfterTinymceActiveEditorInitialized(function () {
          _updateAutosavedFields(result);
        });
      });
  }

  window.addEventListener("ReactToolbarMounted", () => {
    $('#witnessDocument').click(function () {
      $.get('/vfpwd/ajax/checkVerificationPasswordNeeded', function (response) {
        if (response.data) {
          apprise("Please set your verification password in <a href=\"/userform\" target=\"_blank\">My RSpace</a> before witnessing.");
        } else {
          $('#witnessDocumentDialog').data("recordId", recordId).dialog('open');
        }
      });
    });
  });

  doPoll(); // poll notifications immediately
  RS.pollRegistry.register(doPoll, 15000); // set up 15s poll interval for new notifications

  initAttachmentButton();

  $(document).on('click', '.commentIcon', function () {
    showCommentDialog(this, false);
  });

  if (!isSigned && !isWitnessed) {
    displayStatus(editable);
  }

  var mdata = {
    name: RS.unescape(recordName),
    globalId: globalId,
    recordId: recordId,
    versionId: versionNumber
  };
  var htmlData = Mustache.render($('#newRecordHeaderTemplate').html(), mdata);
  $('.rs-record-header-line').html(htmlData);
    initTagMetaData('#notebookTags');

   setUpViewModeInfo(recordTags);
    const tags = initTagMetaData('#notebookTags');

    addOnClickToTagsForViewMode(tags);

  initUseTemplateDlg(function () { return { id: recordId, name: recordName }; });
  window.addEventListener("ReactToolbarMounted", () => {
    $('#createDocFromTemplate').click(function (e) {
      e.preventDefault();
      $('#useTemplateDlg').dialog('open');
    });
  });

  // Set up header info edit features if entry is editable
  if (isEditable) {
    // set up Tags
    $("#editTags").show(fadeTime);
    $(document).on("click", "#editTags", function () {
      if (!tagsEditMode) {
        initTagForm("#notebookTags");
      }
    });
    $(document).on("click", "#saveTags", function (e) {
      e.preventDefault();
      collapseTagForm("#notebookTags");
    });
    // set up Name
    initInlineRenameRecordForm("#inlineRenameRecordForm");
  }

  recordName = mdata.name;

  initDragDropAreaHandling('.tox-edit-area',
    function (e) {
      uploadGalleryFile(e.originalEvent, tinyMCE.activeEditor);
    }
  );
});

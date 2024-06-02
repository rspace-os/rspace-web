/* jshint maxerr: 100 */

var fieldId = -1;
var LISTFORMS_BY_MODFICATIONDATE_DESC = "/workspace/editor/form/list?orderBy=modificationDate&sortOrder=DESC&userFormsOnly=true";
function createEditDeleteButtons(id) {
  var data = { id: id };
  var template = $('#editDeleteButtonTemplate').html();
  var toolbarHTML = Mustache.render(template, data);
  return toolbarHTML;
}

function initChangeFormIconDialog() {
  $('#changeFormIconDialog').dialog({
    title: "Change Form Icon",
    resizable: true,
    autoOpen: false,
    height: 250,
    width: 350,
    modal: true,
    buttons: {
      Cancel: function () {
        $("#changeFormIconDialog").dialog('close');
      },
      Submit: function () {
        var cfg = {
          fileInputId: "#newIconImage",
          postParamName: "filex",
          postURL: "/workspace/editor/form/ajax/saveImage/" + recordId,
          "onloadSuccess": function (json) {
            $("#changeFormIconDialog").dialog('close');
            $('#iconImgSrc').attr('src', createURL('/image/getIconImage/' + json.data));
            RS.confirm("Icon changed!", "success", 3000);
          }
        };
        RS.submitIconUpload(cfg);
      }
    }
  });
}

$(document).ready(function () {

  initChangeFormIconDialog();
  // basic document is read only
  disableInputs();

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
    title: "Configure access to forms",
    buttons:
    {
      Cancel: function () {
        $(this).dialog('close');
      },
      OK: function () {
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
          },
          error: function (xhr, err) { alert('Error'); }
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
    $('#add').hide();
    $('#unpublish').hide();
    $('.editButton').hide();
    $('.deleteButton').hide();
    initNonEditableTinyMCE();
  } else {
    $('#tmp_tag').prop('disabled', false);
    $('#field-loading').hide();
    $('#add').bootstrapButton().click(function () {
      addField();
    });

    $('#save').bootstrapButton().click(function () {
      saveForm();
    });

    $('#update').click(function () {
      updateForm();
    });

    $('#abandon').click(function () {
      abandonUpdateForm();
    });
    $('#changeOrder').click(function () {
      changeOrder();
    });

    $("#field-editor").dialog({
      resizable: false,
      autoOpen: false,
      height: 500,
      width: 550,
      modal: true,
      buttons: {
        Cancel: function () {
          $(this).dialog('close');
        },
        'Save': function () {
          $("#errorSummary").hide();
          $("#errorSummary").empty();
          if (fieldFormValid()) {
            var idFieldForm = $("#fieldFormId").val();
            if (idFieldForm !== "") {
              saveEditedField(idFieldForm);
              $('#fieldEditorSelect').show();
            } else {
              createField();
            }
          }
        }
      },
      open: function () {
        $("#errorSummary").hide();
        $("#errorSummary").empty();
      },
      close: function () {
        fieldId = -1;
        $('.form_field').remove();
        $("#fieldEditorSelect").val('1');
        $('#fieldEditorSelect').attr('disabled', false);
        $('#field-loading').hide();
      }
    });

    $('#fieldEditorSelect').change(function () {
      $("#field-editor-form td").removeClass('field-error');
      var fieldType = $("#fieldEditorSelect").val();
      loadFieldForm();
    });

    initNonEditableTinyMCE();
    initSoftTinyMCE();
    loadDateFields();
    loadTimeFields();
    setUpPublishButtons();

    $(window).bind('beforeunload', function () {
      if (wasAutosaved) {
        autosave(true);
        return 'Are you sure you want to exit without saving changes?';
      }
    });

    $(window).unload(function () {
      if (wasAutosaved) {
        unlockform();
      }
    });
  }

  // Events handler related to reorder fields functionality
  $(document).on('change', 'input[name=fields]:radio', function (e) {
    //Fist of all, we hide previous order buttons.
    $(".orderButtons").css('display', 'none');
    // Select the field id and display the buttons related to the field.
    var id = $(this).attr('id').split("_")[1];
    $("#buttonBlock_" + id).css('display', 'inline-block');
  });

  $(document).on('click', '.fieldMover', function (e) {
    e.stopPropagation();

    var action = $(this).attr('id').split("_")[0];
    var row = $(this).parents("tr:first");
    var tbody = $(this).parents("tbody");

    if (action === "up") {
      row.insertBefore(row.prev());
    } else if (action === "down") {
      row.insertAfter(row.next());
    } else if (action === "top") {
      tbody.prepend(row);
    } else if (action === "bottom") {
      tbody.append(row);
    }
  });

});

function unlockform() {
  $.ajaxSetup({
    async: false
  });
  var data = {
    id: recordId
  };
  $.post(createURL('/workspace/editor/form/unlockform'), data,
    function () {
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
      //get the access control dialog
      $.get(createURL("/workspace/editor/form/ajax/publishAndShare?templateId=" + recordId), function (data) {
        $('#publishShareDlgContent').html(data);
        $('#publishShareDlg').dialog('open');
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
        RS.ajaxFailed("Unpublishing form", false, jqxhr);
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

function disableInputs() {
  $("#structuredDocument :radio").attr('disabled', true);
  $("#structuredDocument :checkbox[class!='alwaysActive']").attr('disabled', 'disabled');
  $("#structuredDocument :text").attr('disabled', 'disabled');
}

function initNonEditableTinyMCE() {
  $(function () {
    $('textarea.tinymce').tinymce({
      selector: "",
      theme: "silver",
      width: "800px",
      plugins: [
        "advlist autolink anchor lists link charmap print preview anchor visualchars nonbreaking",
        "searchreplace visualblocks code insertdatetime codesample hr table paste noneditable fullscreen"
      ],
      toolbar: [
        "bold italic underline strikethrough | alignleft aligncenter alignright alignjustify ",
      ],
      elementpath: false,
      menubar: false,
      branding: false,
      readonly: 1,
    });
  });
}

function initSoftTinyMCE() {
  $('textarea.tempform').tinymce({
    selector: "",
    theme: "silver",
    width: "800px",
    plugins: [
      "advlist autolink anchor lists link charmap print preview anchor visualchars nonbreaking",
      "searchreplace visualblocks code insertdatetime codesample hr table paste noneditable fullscreen"
    ],
    toolbar: [
      "bold italic underline strikethrough | alignleft aligncenter alignright alignjustify ",
    ],
    elementpath: false,
    menubar: true,
    branding: false,
    relative_urls: false,
    setup: function (ed) {
      ed.on('keypress', function () {
        ed.isNotDirty = false;
      });
    }
  });
}

function initSpecificTinyMCE(id) {
  $(function () {
    var objt = '#textfield_' + id;

    $(objt).tinymce({
      theme: "silver",
      width: "800px",
      plugins: [
        "advlist autolink anchor lists link charmap print preview anchor visualchars nonbreaking",
        "searchreplace visualblocks code insertdatetime codesample hr table paste noneditable fullscreen"
      ],
      toolbar: [
        "bold italic underline strikethrough | alignleft aligncenter alignright alignjustify ",
      ],
      elementpath: false,
      menubar: true,
      branding: false,
      setup: function (ed) {
        ed.on('keypress', function () {
          ed.isNotDirty = false;
        });
      }
    });
  });
}

function addField() {
  $('#fieldEditorSelect').show();
  $('#field-editor').dialog('open');
  return false;
}

/**
 * Called when selection changes on the Field select box after clicking 'Add'
 */
function loadFieldForm() {
  // remove previous errors
  $("#errorSummary").hide();
  $("#errorSummary").empty();
  // get selected option
  var name = $('#fieldEditorSelect').val();
  $('.form_field').remove();
  if (name != "Select Field Type") {
    // disablw hile loading
    $('#fieldEditorSelect').attr('disabled', true);
    // loading imahe
    $('#field-loading').show();
    $('#field-editor').dialog('open');

    $.get(createURL("/workspace/editor/form/ajax/getField"), {
      name: name, // the field type - number, string etc
      recordId: recordId
    }, function (data) {
      $('#field-loading').hide();
      if (fieldId == -1) {
        $('#fieldEditorSelect').attr('disabled', false);
      }
      $("#field-editor-form").append(data);
      fieldFormValidation(name);
    });
  }
}

function editField(id) {
  $("#errorSummary").hide();
  $("#errorSummary").empty();
  $('.form_field').remove();
  $('#fieldEditorSelect').hide();
  $('#field-loading').show();
  $('#field-editor').dialog('open');

  $.get(createURL("/workspace/editor/form/ajax/getFieldById"), {
    fieldId: id
  }, function (data) {
    $('#field-loading').hide();
    $("#field-editor-form").append(data);
    var name = $("#fieldFormType").val();

    // Date needs to be initialize on a different way if you are editing.
    if (name == "Date") {
      name = "DateEdit";
    }
    fieldFormValidation(name);
  });
}

/*
 * Every field form is unique so to prevent major code bloat we only load the
 * javascript we need for which ever form is selected.
 */
function fieldFormValidation(field) {

  if (field == "Text") {
    initSpecificTinyMCE();
  }

  if (field == "Date") {
    // $(".datepickerAddNew").datepicker();
    var datef = "dd/mm/yy";

    $('.datepickerAddNew').datepicker({

      beforeShow: function () {
        setTimeout(function () {
          $('.ui-datepicker').css('z-index', 99999999999999);
        }, 0);
      }
    });
    $('.datepickerAddNew').datepicker('option', {
      dateFormat: datef
    });
    $("#field_dateformat").change(function () {
      // jquery format is very different from the date formatter format
      // the following code makes date act like the one used in the forms
      var dateformat = $(this).val();
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
      $('.datepickerAddNew').datepicker('option', {
        dateFormat: dateformat
      });
    });
  }

  if (field == "DateEdit") {
    // $(".datepickerAddNew").datepicker();
    var dateformat = $("#field_dateformat").val();
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
    $('.datepickerAddNew').datepicker({
      dateFormat: dateformat
    });

    $("#field_dateformat").change(function () {
      // jquery format is very different from the date formatter format
      // the following code makes date act like the one used in the forms
      var dateformat = $(this).val();
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
      $('.datepickerAddNew').datepicker('option', {
        dateFormat: dateformat
      });
    });
  } else if (field == "Time") {
    $(".timePickerForm").timepicker("destroy");
    $("#field_defaulttime.timePickerForm").timepicker({
      ampm: true,
      minDate: new Date(1970, 01, 02, 00, 00),
      maxDate: new Date(1970, 01, 02, 11, 59),
    });
    $("#field_minvalue.timePickerForm").timepicker({
      ampm: true,
      minDate: new Date(1970, 01, 02, 00, 00),
      maxDate: new Date(1970, 01, 02, 11, 59),
    });
    $("#field_maxvalue.timePickerForm").timepicker({
      ampm: true,
      minDate: new Date(1970, 01, 02, 00, 00),
      maxDate: new Date(1970, 01, 02, 11, 59),
    });
    //
    $("#field_timeFormat").change(function () {
      var timeformat = $(this).val();

      var date, hours, min;

      if (timeformat == "hh:mm a") {

        $(".timePickerForm").timepicker("destroy");
        $(".timePickerForm").timepicker({
          ampm: true
        });

        date = $("#field_defaulttime.timePickerForm")
          .datetimepicker('getDate');
        if (date !== null) {
          hours = date.getHours();
          min = date.getMinutes();
          $("#field_defaulttime.timePickerForm").val(
            $.datepicker.formatTime('hh:mm tt', {
              hour: hours,
              minute: min
            }, { ampm: true }));
        }
        date = $("#field_minvalue.timePickerForm")
          .datetimepicker('getDate');
        if (date !== null) {
          hours = date.getHours();
          min = date.getMinutes();
          $("#field_minvalue.timePickerForm").val(
            $.datepicker.formatTime('hh:mm tt', {
              hour: hours,
              minute: min
            }, { ampm: true }));
        }
        date = $("#field_maxvalue.timePickerForm")
          .datetimepicker('getDate');
        if (date !== null) {
          hours = date.getHours();
          min = date.getMinutes();
          $("#field_maxvalue.timePickerForm").val(
            $.datepicker.formatTime('hh:mm tt', {
              hour: hours,
              minute: min
            }, { ampm: true }));
        }

      } else {
        $(".timePickerForm").timepicker("destroy");
        $(".timePickerForm").timepicker({
          ampm: false
        });

        date = $("#field_defaulttime.timePickerForm")
          .datetimepicker('getDate');

        if (date !== null) {
          hours = date.getHours();
          min = date.getMinutes();
          $("#field_defaulttime.timePickerForm").val(
            $.datepicker.formatTime('h:mm', {
              hour: hours,
              minute: min
            }, { ampm: false }));
        }
        date = $("#field_minvalue.timePickerForm")
          .datetimepicker('getDate');
        if (date !== null) {
          hours = date.getHours();
          min = date.getMinutes();
          $("#field_minvalue.timePickerForm").val(
            $.datepicker.formatTime('h:mm', {
              hour: hours,
              minute: min
            }, { ampm: false }));
        }
        date = $("#field_maxvalue.timePickerForm")
          .datetimepicker('getDate');
        if (date !== null) {
          hours = date.getHours();
          min = date.getMinutes();
          $("#field_maxvalue.timePickerForm").val(
            $.datepicker.formatTime('h:mm', {
              hour: hours,
              minute: min
            }, { ampm: false }));
        }
      }
    });
  } else if (field == "Radio") {
    const existingChoiceNames = [];
    const populateExistingChoiceNames = () => {
      if (existingChoiceNames.length === 0) {
        const displayedChoices = $('#radioFields li input');
        if (displayedChoices && displayedChoices.length > 0) {
          displayedChoices.each(index => {
            const text = displayedChoices[index].value;
            if(!existingChoiceNames.includes(text)) {
              existingChoiceNames.push(displayedChoices[index].value);
            }
          });
        }
      }
    };
    const delete_radio_id_prefix = 'del_radio_';
    const bindDeleteToNewlyAddedOptions = () => {
      $('.deleteRadio').click(function () {
        populateExistingChoiceNames();
        const myName = $(this)[0].id.replace(delete_radio_id_prefix,'');
        const index = existingChoiceNames.indexOf(myName);
        if(index>-1) {
          $(this).parent().remove();
          existingChoiceNames.splice(index, 1);
        }
      });
    }
    bindDeleteToNewlyAddedOptions();

    const addValueToExistingChoicesIfIsNewAndNonEmptyValue = (valueText) => {
      if (valueText) {
        if(!existingChoiceNames.includes(valueText)) {
          existingChoiceNames.push(valueText);
          return true;
        }
      }
      return false;
    }
    const addToExistingChoicesAndDisplayValueIfIsNewAndNonEmptyValue = (valueText) => {
      if(addValueToExistingChoicesIfIsNewAndNonEmptyValue(valueText)){
        addDisplayOptionForRadio(valueText);
      }
    }

    let  fileList;
    $("#radioFields").sortable();
    $('#uploadRadioButton').click(function () {
      populateExistingChoiceNames();
      const file = fileList[0];
      const fileSize = file.size;
      const fileKb = fileSize / 1024;
      if (fileKb >= 100) {
        apprise("Please upload a file smaller than 100kb.");
        return;
      }
      const reader = new FileReader();
      let text;
      reader.addEventListener("load", () => {
        const displayText = parseRadioOptions(reader.result, {
          isCSV: file.type === "text/csv"
        });
        if(checkSizeAllowsInsertionOfNewTerms(displayText)){
          if($('#alphabeticSortChoice')[0].checked){
            displayText.forEach(valueText => {
              addValueToExistingChoicesIfIsNewAndNonEmptyValue(valueText);
            });
            reorderValuesAlphabetically();
          } else {
            displayText.forEach(valueText => {
              addToExistingChoicesAndDisplayValueIfIsNewAndNonEmptyValue(valueText);
            });
          }
        }

        $("#radioFields").sortable();
        $('#uploadRadioChooser').val('');
        $('#uploadRadioButton').attr('disabled', 'disabled');
        bindDeleteToNewlyAddedOptions();
      }, false);
      if (file) {
        reader.readAsText(file);
      }
    });
    const checkSizeAllowsInsertionOfNewTerms = (newText) =>{
      populateExistingChoiceNames();
      const merged = [...newText,...existingChoiceNames];
      const uniqueMerged = new Set(merged);
      if (uniqueMerged.size >= 2000) {
        apprise("The picklist cannot hold more than 2000 items.");
        return false;
      }
      return true;
    }
    $('#alphabeticSortChoice').change(function (event) {
      if(event.target.checked){
        reorderValuesAlphabetically();
      }
    });
    const reorderValuesAlphabetically = () => {
      populateExistingChoiceNames();
      existingChoiceNames.sort((a,b)=> a.localeCompare(b));
      $("#radioFields").empty();
      existingChoiceNames.forEach(a=>addDisplayOptionForRadio(a));
      bindDeleteToNewlyAddedOptions();
      $("#radioFields").sortable();
    }

    $('#uploadRadioChooser').change(function (event) {
      fileList = event.target.files;
      $('#uploadRadioButton').attr('disabled', false);
    });

    $('#addRadio').click(function () {
      var radioName = $("#addRadioName").val();
      if ("" !== radioName) {
        const radiosArr = radioName.split(",");
        populateExistingChoiceNames();
        if(checkSizeAllowsInsertionOfNewTerms(radiosArr)) {
          radiosArr.forEach(valueText =>
              addToExistingChoicesAndDisplayValueIfIsNewAndNonEmptyValue(valueText.trim()));
          if ($('#alphabeticSortChoice')[0].checked) {
            reorderValuesAlphabetically();
          }
        }

        $("#addRadioName").val("");
        $("#addRadioName").focus();
        bindDeleteToNewlyAddedOptions();
        $("#radioFields").sortable();
      }
    });

    const addDisplayOptionForRadio = (radioName) => {
      if(radioName) {
        $('#radioFields').append("<li style='white-space: normal;cursor: pointer'><input type='radio' name='fieldDefaultRadio' value='" +
            radioName + "'> <input type='hidden' name='fieldRadios' value='" + RS.escapeHtml(radioName) + "'> " +
            RS.escapeHtml(radioName) + " <a href='#' id='"+delete_radio_id_prefix+RS.escapeHtml(radioName)+"' " +
            "style='font-weight:bold;' class='deleteRadio'>(Delete)</a></li>");
      }
    }
  } else if (field == "Choice") {
    $('.deleteChoice').click(function () {
      $(this).parent().remove();
    });
    $("#choiceFields").sortable();
    $('#addChoice').click(function () {
      var choiceName = $("#addChoiceName").val();
      if ("" !== choiceName) {
        $('#choiceFields').append("<li><input type=\"checkbox\" name=\"fieldSelectedChoices\" value=\"" +
          choiceName + "\"> <input type=\"hidden\" name=\"fieldChoices\" value=\"" + RS.escapeHtml(choiceName) + "\"> " +
          RS.escapeHtml(choiceName) + " <a href=\"#\" style=\"font-weight:bold;\" class=\"deleteChoice\">(Delete)</a></li>");
        $("#addChoiceName").val("");
        $("#addChoiceName").focus();
        $('.deleteChoice').click(function () {
          $(this).parent().remove();
        });
        $("#choiceFields").sortable();
      }
    });
  } else if (field == "Number") {
    validateNumberField($("#field_minvalue"));
    validateNumberField($("#field_maxvalue"));
    validateNumberField($("#field_defaultvalue"));

    $("#field_minvalue, #field_maxvalue, #field_defaultvalue").change(
      function () {
        validateNumberField(this);
      });

    $("#field_decimalplaces").change(function () {
      var decimalPlaces = Number($(this).val());
      if (!isNaN(decimalPlaces)) {
        $(this).val(decimalPlaces.toFixed(0));
      } else {
        // if its not a number set to 0
        $(this).val(0);
      }
      validateNumberField($("#field_minvalue"));
      validateNumberField($("#field_maxvalue"));
      validateNumberField($("#field_defaultvalue"));
    });
  }
}

function validateNumberField(field) {
  var decimalPlaces = Number($("#field_decimalplaces").val());
  var fieldValue = Number($(field).val());
  if ($(field).val() !== "" && !isNaN(fieldValue)) {
    $(field).val(fieldValue.toFixed(decimalPlaces));
  } else {
    // if its not a number, make it empty
    $(field).val("");
  }
}

function createField() {
  $('#field-loading').show();
  var fieldType = $("#fieldEditorSelect").val();

  if (fieldType == "Number") {
    addNumberField();
  } else if (fieldType == "String") {
    addStringField();
  } else if (fieldType == "Text") {
    addTextField();
  } else if (fieldType == "Radio") {
    addRadioField();
  } else if (fieldType == "Choice") {
    addChoiceField();
  } else if (fieldType == "Date") {
    addDateField();
  } else if (fieldType == "Time") {
    addTimeField();
  }
  // show publish button if hidden
  $('#publish').show();
}

function saveEditedField(fieldId) {
  $('#field-loading').show();
  // It needs to know what kind of field is been editing
  var fieldType = $("#fieldFormType").val();
  if (fieldType == "Number") {
    saveEditedNumberField(fieldId);
  } else if (fieldType == "String") {
    saveEditedStringField(fieldId);
  } else if (fieldType == "Text") {
    saveEditedTextField(fieldId);
  } else if (fieldType == "Radio") {
    saveEditedRadioField(fieldId);
  } else if (fieldType == "Choice") {
    saveEditedChoiceField(fieldId);
  } else if (fieldType == "Date") {
    saveEditedDateField(fieldId);
  } else if (fieldType == "Time") {
    saveEditedTimeField(fieldId);
  }
}

/*
 * Collects data from form and returns it as a Json version of a
 *  NumberFieldDTO
 */
function collectDataFromNumberEditor() {
  var defaultV = $("#field_defaultvalue").val() || "";
  var minV = $("#field_minvalue").val();
  var maxV = $("#field_maxvalue").val();
  var decimalP = $("#field_decimalplaces").val();
  var fieldName = $("#field_name").val();
  var isMandatory = $("#mandatoryCheckbox").prop('checked');
  var dataField = {
    defaultNumberValue: defaultV,
    minNumberValue: minV,
    maxNumberValue: maxV,
    decimalPlaces: decimalP,
    name: fieldName,
    mandatory: isMandatory,
  };
  return dataField;
}

function addNumberField() {
  var numberEditorData = collectDataFromNumberEditor();
  numberEditorData.parentId = recordId;
  var data = JSON.stringify(numberEditorData);

  var jqxhr = jQuery.ajax({
    url: createURL("/workspace/editor/form/ajax/createNumberField"), dataType: 'json',
    data: data, type: "POST", contentType: "application/json;", success: function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        addNumberCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      $("#field-editor").dialog('close');
    }
  });

  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Saving field", false, jqxhr);
  });
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

function addNumberCode(data) {
  //  var defaultValue = "";
  //  if (data.defaultNumberValue != null) {
  //    defaultValue = data.defaultNumberValue;
  //  }
  var template = $('#createNumberRowTemplate').html();
  var partial = { editDeleteButtonTemplate: $('#editDeleteButtonTemplate').html() };
  var newrow = Mustache.render(template, data, partial);
  $('#structuredDocument > tbody:last').append(newrow);
}

function saveEditedNumberField(fieldId) {
  var numberEditorData = collectDataFromNumberEditor();
  numberEditorData.parentId = fieldId;
  var data = JSON.stringify(numberEditorData);


  var jqxhr = jQuery.ajax({
    url: createURL("/workspace/editor/form/ajax/saveEditedNumberField"),
    dataType: 'json', data: data, type: "POST", contentType: "application/json;",
    success: function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        editNumberCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      $("#field-editor").dialog('close');
    }
  });

  jqxhr.fail(function () {
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Saving field", false, jqxhr);
  });
}
/*
 * Updates number field when its template properties have been edited.
 */
function editNumberCode(data) {
  var fieldCode = $("#field_" + data.id);
  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  $(fieldCode).find("td[name='fieldName']").text(data.name);
  $(fieldCode).find("td[name='fieldName']").append(mandatoryAsterisk);
  $(fieldCode).find("input[name='fieldData']").val(data.defaultNumberValue);
  $(fieldCode).find("input[name='lastValue']").val(data.defaultNumberValue);
  $(fieldCode).find("input[name='minValue']").val(data.minNumberValue);
  $(fieldCode).find("input[name='maxValue']").val(data.maxNumberValue);
  $(fieldCode).find("input[name='decimalPlaces']").val(data.decimalPlaces);
}

/*
 * Gets the data from the string-field editor configuration dialog
 */
function collectDataFromStringEditor() {
  var defaultV = $("#field_defaultvaluestring").val();
  var ifPasswordV = $("input[name='fieldIfPassword']:checked").val();
  var fieldName = $("#field_name").val();
  if (ifPasswordV === undefined) {
    ifPasswordV = "no";
  }
  const isMandatory = $("#mandatoryCheckbox").prop('checked');

  var dataField = {
    defaultValue: defaultV,
    ifPassword: ifPasswordV,
    name: fieldName,
    fieldId: fieldId,
    mandatory: isMandatory,
  };
  return dataField;
}

function addStringField() {
  var dataField = collectDataFromStringEditor();
  dataField.recordId = recordId;
  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/createStringField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        addStringCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      // refreshFields();
      $("#field-editor").dialog('close');
    }, "json");
  jqxhr.fail(function () {
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Adding field", false, jqxhr);
  });
}

function addStringCode(data) {
  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  var newrow = "<tr  id=\"field_" + data.id + "\"  name=\"string\" class=\"field\"><td name=\"fieldName\" class=\"field-name\">" +
    RS.escapeHtml(data.name) + "<span class='field-type-enum'> (" + data.type + ")</span>" + mandatoryAsterisk + "</td><td class=\"field-value\">" +
    "<table><tr><td class=\"icon-field bootstrap-custom-flat\">" + "<button class=\"editButton btn btn-default\" onclick=\"editField(" + data.id + ")\">Edit</button>" +
    "<button class=\"deleteButton btn btn-default\" onclick=\"deleteField(" + data.id + ")\">Delete</button>" + "</td></tr><tr><td class=\"field-value-inner\">";
  if (data.ifPassword) {
    newrow = newrow + "<input  readOnly='true' type=\"password\" id=\"" + data.id + "\" name=\"fieldData\" value=\"" + RS.escapeHtml(data.defaultStringValue) + "\"/>";
  } else {
    newrow = newrow + "<input readOnly='true' type=\"text\" id=\"" + data.id + "\" name=\"fieldData\" value=\"" + RS.escapeHtml(data.defaultStringValue) + "\"/>";
  }

  newrow = newrow + "<input type=\"hidden\" name=\"defaultValue\" value=\"" + RS.escapeHtml(data.defaultStringValue) + "\"/>" +
    "<input type=\"hidden\" name=\"wasChanged\" value=\"false\"/>" + "<input type=\"hidden\" name=\"isPassword\" value=\"" +
    data.ifPassword + "\"/>" + "<input type=\"hidden\" name=\"fieldId\" value=\"" + data.id + "\"/>" + "</td></tr></table></td></tr>";
  $('#structuredDocument > tbody:last').append(newrow);
}

function editStringCode(data) {

  var fieldCode = $("#field_" + data.id);
  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  $(fieldCode).find("td[name='fieldName']").text(data.name);
  $(fieldCode).find("td[name='fieldName']").append(mandatoryAsterisk);
  var password = $(fieldCode).find("input[name='isPassword']").val();

  if (password != data.ifPassword) {

    var passwordInput = $(fieldCode).find("input[name='fieldData']");
    $(passwordInput).hide();
    var old_id = $(passwordInput).attr("id");
    $(passwordInput).attr("id", "Password_hidden");
    var new_input = document.createElement("input");
    new_input.setAttribute("id", old_id); // Assign old hidden input ID to
    // new input
    new_input.setAttribute("name", "fieldData");
    if (data.ifPassword) {
      new_input.setAttribute("type", "password"); // Set proper type
    } else {
      new_input.setAttribute("type", "text"); // Set proper type
    }

    new_input.value = data.defaultStringValue; // Transfer the value to new input
    $("#Password_hidden").after(new_input);
    $("#Password_hidden").remove();

  }
  $(fieldCode).find("input[name='fieldData']").val(data.defaultStringValue);
  $(fieldCode).find("input[name='isPassword']").val(data.ifPassword);
}

function saveEditedStringField(fieldId) {

  var dataField = collectDataFromStringEditor();
  dataField.fieldId = fieldId;
  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/saveEditedStringField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        editStringCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      $("#field-editor").dialog('close');
    }, "json");
  jqxhr.fail(function () {
    RS.ajaxFailed("Saving field", false, jqxhr);
  });
}

function collectDataFromTextEditor() {

  var defaultV = $("#field_defaultvaluestring").val();
  var fieldName = $("#field_name").val();
  var isMandatory = $("#mandatoryCheckbox").prop('checked');
  var dataField = {
    defaultValue: defaultV,
    name: fieldName,
    mandatory: isMandatory,
  };
  return dataField;
}

function addTextField() {

  var dataField = collectDataFromTextEditor();
  dataField.recordId = recordId;

  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/createTextField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        addTextCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }

      $("#field-editor").dialog('close');
      initSpecificTinyMCE(data.data.id);
    }, "json");
  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Adding field", false, jqxhr);
  });
}

function addTextCode(data) {

  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  var newrow = "<tr  id=\"field_" + data.id + "\"  name=\"text\" class=\"field\">" +
    "<td  name=\"fieldName\" class=\"field-name\">" + RS.escapeHtml(data.name) + "<span class=\"field-type-enum\"> (" + data.type + ")</span>" + mandatoryAsterisk + "</td>" +
    "<td class=\"field-value\">" + "<table>" + "<tr>" + "<td class=\"icon-field bootstrap-custom-flat\">" + createEditDeleteButtons(data.id) + "</td>" + "</tr>" +
    "<tr>" + "<td class=\"field-value-inner\">" + "<textarea  readonly='true' id=\"textfield_" + data.id + "\" name=\"fieldRtfData\" class=\"tinymce\">" +
    RS.escapeHtml(data.defaultValue) + "</textarea>" + "<input type=\"hidden\" name=\"fieldId\" value=\"" + data.id + "\"/>" + "</td>" + "</tr>" + "</table>" +
    "</td>" + "</tr>";
  $('#structuredDocument > tbody:last').append(newrow);
}

function saveEditedTextField(fieldId) {

  var dataField = collectDataFromTextEditor();
  dataField.fieldId = fieldId;
  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/saveEditedTextField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        editTextCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      $("#field-editor").dialog('close');
    }, "json");
  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Saving field", false, jqxhr);
  });
}

function editTextCode(data) {
  var fieldCode = $("#field_" + data.id);
  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  $(fieldCode).find("td[name='fieldName']").text(data.name);
  $(fieldCode).find("td[name='fieldName']").append(mandatoryAsterisk);
  var textArea$ = $(fieldCode).find("textarea[name='fieldRtfData']");
  textArea$.html(data.defaultValue);
  tinyMCE.activeEditor.isNotDirty = false;
}

function addRadioField() {
  var radioValues = $("input[name='fieldRadios']");
  radioValues = RS._serializeWithNoEscapes(radioValues);
  var radioSelected = $("input[name=fieldDefaultRadio]:checked").val();
  if (radioSelected === undefined) {
    radioSelected = "";
  }
  const showAsPickList = $("#radioAsPickListChoice").is(":checked");
  const sortAlphabetic = $("#alphabeticSortChoice").is(":checked");
  var fieldName = $("#field_name").val();
  const isMandatory = $("#mandatoryCheckbox").prop('checked');

  var dataField = {
    radioValues: radioValues,
    radioSelected: radioSelected,
    name: fieldName,
    recordId: recordId,
    showAsPickList:showAsPickList,
    sortAlphabetic:sortAlphabetic,
    mandatory: isMandatory,
  };
  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/createRadioField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        addRadioCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }

      // refreshFields();
      $("#field-editor").dialog('close');
      updateUIWithRadioOrPicklistAfterSave(data.data);
    }, "json");
  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Adding field", false, jqxhr);
  });
}

function addRadioCode(data) {

  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  var newrow = "<tr  id=\"field_" + data.id + "\"  name=\"radio\" class=\"field\"><td name=\"fieldName\" class=\"field-name\">" +
    RS.escapeHtml(data.name) + "<span class='field-type-enum'> (" + data.type + ")</span>" + mandatoryAsterisk + "</td><td class=\"field-value\">" +
    "<table><tr><td class=\"icon-field bootstrap-custom-flat\">" + createEditDeleteButtons(data.id) + "</td></tr><tr><td class=\"field-value-inner\">" +
    "<ul id=\"radioFieldsFinal\">";

  $.each(data.radioOptionAsList, function (i, val) {
    if (val == data.defaultRadioOption) {
      newrow = newrow + "<li><input id=\"" + data.id + "\" disabled='disabled' type=\"radio\" name=\"fieldDefaultRadioFinal_" +
        data.id + "\" value=\"" + val + "\" checked> <input type=\"hidden\" name=\"fieldRadioFinal_" +
        data.id + "\" value=\"" + val + "\">" + RS.escapeHtml(val) + "</li>";
    } else {
      newrow = newrow + "<li><input id=\"" + data.id + "\"  disabled='disabled' type=\"radio\" name=\"fieldDefaultRadioFinal_" +
        data.id + "\" value=\"" + val + "\"> <input type=\"hidden\" name=\"fieldRadioFinal_" +
        data.id + "\" value=\"" + val + "\">" + RS.escapeHtml(val) + "</li>";
    }
  });

  newrow = newrow + "<input type=\"hidden\" name=\"wasChanged\" value=\"false\"/> <input type=\"hidden\" name=\"fieldId\" value=\"" +
    data.id + "\"/></ul></td></tr></table></td></tr>";

  $('#structuredDocument > tbody:last').append(newrow);
}

function saveEditedRadioField(fieldId) {

  var radioValues = $("input[name='fieldRadios']");
  radioValues = RS._serializeWithNoEscapes(radioValues);
  var radioSelected = $("input[name=fieldDefaultRadio]:checked").val();
  if (radioSelected === undefined) {
    radioSelected = "";
  }
  var fieldName = $("#field_name").val();
  const showAsPickList = $("#radioAsPickListChoice").is(":checked");
  const sortAlphabetic = $("#alphabeticSortChoice").is(":checked");
  const isMandatory = $("#mandatoryCheckbox").prop('checked');

  var dataField = {
    radioValues: radioValues,
    radioSelected: radioSelected,
    name: fieldName,
    fieldId: fieldId,
    showAsPickList:showAsPickList,
    sortAlphabetic:sortAlphabetic,
    mandatory: isMandatory,
  };
  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/saveEditedRadioField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        editRadioCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      $("#field-editor").dialog('close');
    }, "json");
  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Saving field", false, jqxhr);
  });
}
const updateUIWithRadioOrPicklistAfterSave = (data) => {
  const fieldCode = $("#field_" + data.id);
  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  $(fieldCode).find("td[name='fieldName']").text(data.showAsPickList ? data.name + ' (Picklist)': data.name + ' (Radio)');
  $(fieldCode).find("td[name='fieldName']").append(mandatoryAsterisk);
}

function editRadioCode(data) {
  const fieldCode = $("#field_" + data.id);
  updateUIWithRadioOrPicklistAfterSave(data);

  var ulRadio = $(fieldCode).find("#radioFieldsFinal");
  $(ulRadio).hide();
  $(ulRadio).attr("id", "ulRadio_hidden");

  var newCode = "<ul id=\"radioFieldsFinal\">";

  $.each(data.radioOptionAsList, function (i, val) {
    if (val == data.defaultRadioOption) {
      newCode = newCode + "<li><input id=\"" + data.id + "\"  disabled='disabled' type=\"radio\" name=\"fieldDefaultRadioFinal_" +
        data.id + "\"   value=\"" + val + "\" checked> <input type=\"hidden\" name=\"fieldRadioFinal_" + data.id + "\" value=\"" +
        val + "\">" + RS.escapeHtml(val) + "</li>";
    } else {
      newCode = newCode + "<li><input id=\"" + data.id + "\" disabled='disabled' type=\"radio\" name=\"fieldDefaultRadioFinal_" +
        data.id + "\"   value=\"" + val + "\"> <input type=\"hidden\" name=\"fieldRadioFinal_" + data.id + "\" value=\"" +
        val + "\">" + RS.escapeHtml(val) + "</li>";
    }
  });

  newCode = newCode + "<input type=\"hidden\" name=\"wasChanged\" value=\"false\"/> <input type=\"hidden\" name=\"fieldId\" value=\"" +
    data.id + "\"/></ul>";

  $("#ulRadio_hidden").after(newCode);
  $("#ulRadio_hidden").remove();
}


function collectChoiceFieldEditFormData() {
  var choiceValues$ = $("input[name='fieldChoices']");
  var choiceString = RS._serializeWithNoEscapes(choiceValues$);
  var multipleChoice = $("input[name=fieldMultipleAllowed]:checked").val();
  var selectedValues = $("input[name='fieldSelectedChoices']:checked");
  if (selectedValues === undefined) {
    selectedValues = "";
  } else {
    selectedValues = RS._serializeWithNoEscapes(selectedValues);
  }

  if (multipleChoice === undefined) {
    multipleChoice = "no";
  }
  var fieldName = $("#field_name").val();
  const isMandatory = $("#mandatoryCheckbox").prop('checked');
  var dataField = {
    choiceValues: choiceString,
    multipleChoice: multipleChoice,
    selectedValues: selectedValues,
    name: fieldName,
    mandatory: isMandatory,
  };
  return dataField;
}

function addChoiceField() {
  var dataField = collectChoiceFieldEditFormData();
  dataField.recordId = recordId;
  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/createChoiceField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        addChoiceCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      // refreshFields();
      $("#field-editor").dialog('close');
    }, "json");
  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Adding field", false, jqxhr);
  });
}

function addChoiceCode(data) {

  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  var newrow = "<tr  id=\"field_" + data.id + "\" name=\"choice\"  class=\"field\"><td name=\"fieldName\" class=\"field-name\">" +
    RS.escapeHtml(data.name) + "<span class='field-type-enum'> (" + data.type + ")</span>" + mandatoryAsterisk + "</td><td class=\"field-value\">" +
    "<table><tr><td class=\"icon-field bootstrap-custom-flat\">" + createEditDeleteButtons(data.id) + "</td></tr><tr><td class=\"field-value-inner\">" +
    "<ul id=\"choiceFieldsFinal\">";

  var isSelected = false;

  $.each(data.choiceOptionAsList,
    function (i, val) {
      isSelected = false;
      $.each(data.defaultChoiceOptionAsList, function (j,
        selected) {
        if (val == selected) {
          isSelected = true;
        }
      });

      if (isSelected) {
        newrow = newrow + "<li><input id=\"" + data.id + "\" disabled='disabled'  type=\"checkbox\" name=\"fieldSelectedChoicesFinal_" +
          data.id + "\"  value=\"" + val + "\"  checked><input type=\"hidden\" name=\"fieldChoicesFinal_" + data.id + "\" value=\"" + val + "\">" +
          RS.escapeHtml(val) + "</li>";
      } else {
        newrow = newrow + "<li><input id=\"" + data.id + "\" disabled='disabled' type=\"checkbox\"  name=\"fieldSelectedChoicesFinal_" +
          data.id + "\"  value=\"" + val + "\"><input type=\"hidden\" name=\"fieldChoicesFinal_" + data.id + "\" value=\"" + val + "\">" +
          RS.escapeHtml(val) + "</li>";
      }
    });

  newrow = newrow + "<input type=\"hidden\" name=\"wasChanged\" value=\"false\"/> <input type=\"hidden\" name=\"fieldId\" value=\"" +
    data.id + "\"/> </ul></td></tr></table></td></tr>";
  $('#structuredDocument > tbody:last').append(newrow);
}

function saveEditedChoiceField(fieldId) {
  var dataField = collectChoiceFieldEditFormData();
  dataField.fieldId = fieldId;
  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/saveEditedChoiceField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        editChoiceCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      $("#field-editor").dialog('close');
    }, "json");
  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Saving field", false, jqxhr);
  });
}

function editChoiceCode(data) {

  var fieldCode = $("#field_" + data.id);
  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  $(fieldCode).find("td[name='fieldName']").text(data.name);
  $(fieldCode).find("td[name='fieldName']").append(mandatoryAsterisk);
  var ulChoice = $(fieldCode).find("#choiceFieldsFinal");
  $(ulChoice).hide();
  $(ulChoice).attr("id", "ulChoice_hidden");

  var newCode = "<ul id=\"choiceFieldsFinal\">";
  var isSelected = false;
  $.each(data.choiceOptionAsList, function (i, val) {
    isSelected = false;
    $.each(data.defaultChoiceOptionAsList, function (j, selected) {
      if (val == selected) {
        isSelected = true;
      }
    });

    if (isSelected) {
      newCode = newCode + "<li><input id=\"" + data.id + "\"   type=\"checkbox\" name=\"fieldSelectedChoicesFinal_" +
        data.id + "\"  value=\"" + val + "\" checked><input type=\"hidden\" name=\"fieldChoicesFinal_" +
        data.id + "\" value=\"" + val + "\">" + RS.escapeHtml(val) + "</li>";
    } else {
      newCode = newCode + "<li><input id=\"" + data.id + "\"  type=\"checkbox\" name=\"fieldSelectedChoicesFinal_" +
        data.id + "\"  value=\"" + val + "\"><input type=\"hidden\" name=\"fieldChoicesFinal_" + data.id +
        "\" value=\"" + val + "\">" + RS.escapeHtml(val) + "</li>";
    }
  });

  newCode = newCode + "<input type=\"hidden\" name=\"wasChanged\" value=\"false\"/> <input type=\"hidden\" name=\"fieldId\" value=\"" + data.id + "\"/> </ul>";
  $("#ulChoice_hidden").after(newCode);
  $("#ulChoice_hidden").remove();
}

function collectDataFromDateFieldEditor() {
  var defaultV = $("#field_defaultdate").val();
  var minV = $("#field_minvalue").val();
  var maxV = $("#field_maxvalue").val();
  var dateFormat = $("#field_dateformat").val();
  var fieldName = $("#field_name").val();
  var isMandatory = $("#mandatoryCheckbox").prop('checked');

  var dataField = {
    defaultValue: defaultV,
    minValue: minV,
    maxValue: maxV,
    dateFormat: dateFormat,
    name: fieldName,
    mandatory: isMandatory,
  };
  return dataField;
}

function addDateField() {
  var dataField = collectDataFromDateFieldEditor();
  dataField.recordId = recordId;

  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/createDateField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        addDateCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      // refreshFields();
      $("#field-editor").dialog('close');
    }, "json");
  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Adding field", false, jqxhr);
  });
}

function addDateCode(data) {

  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  var newrow = "<tr  id=\"field_" + data.id + "\" name=\"date\" class=\"field\"><td name=\"fieldName\" class=\"field-name\">" +
    RS.escapeHtml(data.name) + "<span class='field-type-enum'> (" + data.type + ")</span>" + mandatoryAsterisk + "</td><td class=\"field-value\">" +
    "<table><tr><td class=\"icon-field bootstrap-custom-flat\">" + createEditDeleteButtons(data.id) + "</td></tr><tr><td class=\"field-value-inner\">" + "<input id=\"" +
    data.id + "\"  name=\"dateField_" + data.id + "\"  readOnly='true' type=\"text\"  value=\"" + data.defaultDateAsString + "\"/>" +
    "<input type=\"hidden\" name=\"format_" + data.id + "\" value=\"" + data.format + "\"/>" + "<input type=\"hidden\" name=\"minValue\" value=\"" +
    data.minValue + "\"/>" + "<input type=\"hidden\" name=\"maxValue\" value=\"" + data.maxValue + "\"/>" + "<input type=\"hidden\" name=\"wasChanged\" value=\"false\"/>" +
    "<input type=\"hidden\" name=\"fieldId\" value=\"" + data.id + "\"/>";

  newrow = newrow + "</ul></td></tr></table></td></tr>";

  $('#structuredDocument > tbody:last').append(newrow);

  var dateformat = data.format;
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

  if (data.minValue > 0 && data.maxValue > 0) {
    $("#" + data.id + ".datepicker").datepicker({
      dateFormat: dateformat,
      minDate: new Date(data.minValue),
      maxDate: new Date(data.maxValue)
    });
  } else if (data.minValue > 0 && data.maxValue === 0) {
    $("#" + data.id + ".datepicker").datepicker({
      dateFormat: dateformat,
      minDate: new Date(data.minValue)

    });
  } else if (data.minValue === 0 && data.maxValue > 0) {
    $("#" + data.id + ".datepicker").datepicker({
      dateFormat: dateformat,
      maxDate: new Date(data.maxValue)

    });
  } else {
    $("#" + data.id + ".datepicker").datepicker({
      dateFormat: dateformat
    });
  }
}

function saveEditedDateField(fieldId) {
  var dataField = collectDataFromDateFieldEditor();
  dataField.fieldId = fieldId;

  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/saveEditedDateField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        editDateCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      $("#field-editor").dialog('close');
    }, "json");
  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Saving field", false, jqxhr);
  });
}

function editDateCode(data) {
  var fieldCode = $("#field_" + data.id);
  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  $(fieldCode).find("td[name='fieldName']").text(data.name);
  $(fieldCode).find("td[name='fieldName']").append(mandatoryAsterisk);
  $(fieldCode).find("input[name='dateField_" + data.id + "']").val(data.defaultDateAsString);
  $(fieldCode).find("input[name='format_" + data.id + "']").val(data.format);
  $(fieldCode).find("input[name='minValue']").val(data.minValue);
  $(fieldCode).find("input[name='maxValue']").val(data.maxValue);
  $("#" + data.id + ".datepicker").datepicker("destroy");
  loadDatePicker(fieldCode);
}

/*
 * Called when user clicks on 'Save' after creating a time field in Sructured
 * Document Editor
 */
function addTimeField() {

  var defaultV = $("#field_defaulttime").val();
  var minV = $("#field_minvalue").val();
  var maxV = $("#field_maxvalue").val();
  var timeFormat = $("#field_timeFormat").val();
  var fieldName = $("#field_name").val();
  const isMandatory = $("#mandatoryCheckbox").prop('checked');

  var dataField = {
    defaultValue: defaultV,
    minValue: minV,
    maxValue: maxV,
    timeFormat: timeFormat,
    name: fieldName,
    recordId: recordId,
    mandatory: isMandatory,
  };

  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/createTimeField"),
    dataField, function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        addTimeCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      // refreshFields();
      $("#field-editor").dialog('close');
    }, "json");
  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Saving field", false, jqxhr);
  });
}

function addTimeCode(data) {

  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  var newrow = "<tr  id=\"field_" + data.id + "\" name=\"time\" class=\"field\"><td name=\"fieldName\" class=\"field-name\">" +
    RS.escapeHtml(data.name) + "<span class='field-type-enum'> (" + data.type + ")</span>" + mandatoryAsterisk + "</td><td class=\"field-value\">" +
    "<table><tr><td class=\"icon-field bootstrap-custom-flat\">" + createEditDeleteButtons(data.id) + "</td></tr><tr><td class=\"field-value-inner\">" + "<input id=\"" +
    data.id + "\"  name=\"timeField_" + data.id + "\"  readOnly='true' type=\"text\"  value=\"" + data.defaultTimeAsString + "\" />" +
    "<input type=\"hidden\" name=\"defaultTime\" value=\"" + data.defaultTime + "\"/>" + "<input type=\"hidden\" name=\"format_" + data.id + "\" value=\"" +
    data.timeFormat + "\"/>" + "<input type=\"hidden\" name=\"minTime\" value=\"" + data.minTime + "\"/>" + "<input type=\"hidden\" name=\"maxTime\" value=\"" +
    data.maxTime + "\"/>" + "<input type=\"hidden\" name=\"minHour\" value=\"" + data.minHour + "\"/>" + "<input type=\"hidden\" name=\"minMinutes\" value=\"" +
    data.minMinutes + "\"/>" + "<input type=\"hidden\" name=\"maxHour\" value=\"" + data.maxHour + "\"/>" + "<input type=\"hidden\" name=\"maxMinutes\" value=\"" +
    data.maxMinutes + "\"/>" + "<input type=\"hidden\" name=\"wasChanged\" value=\"false\"/>" + "<input type=\"hidden\" name=\"fieldId\" value=\"" + data.id + "\"/>";

  newrow = newrow + "</ul></td></tr></table></td></tr>";

  $('#structuredDocument > tbody:last').append(newrow);

  var timeformat;
  if (data.timeFormat == "hh:mm a") {
    timeformat = true;

  } else {
    timeformat = false;
  }

  if (data.minTime > 0 && data.maxTime > 0) {
    $("#" + data.id + ".timepicker").timepicker({
      ampm: timeformat,
      minDate: new Date(1970, 01, 02, data.minHour, data.minMinutes),
      maxDate: new Date(1970, 01, 02, data.maxHour, data.maxMinutes),
    });
  } else if (data.minTime > 0 && data.maxTime === 0) {
    $("#" + data.id + ".timepicker").timepicker({
      ampm: timeformat,
      minDate: new Date(1970, 01, 02, data.minHour, data.minMinutes)

    });
  } else if (data.minTime === 0 && data.maxTime > 0) {
    $("#" + data.id + ".timepicker").timepicker({
      ampm: timeformat,
      maxDate: new Date(1970, 01, 02, data.maxHour, data.maxMinutes)

    });
  } else {
    $("#" + data.id + ".timepicker").timepicker({
      ampm: timeformat
    });
  }
}

function saveEditedTimeField(fieldId) {

  var defaultV = $("#field_defaulttime").val();
  var minV = $("#field_minvalue").val();
  var maxV = $("#field_maxvalue").val();
  var timeFormat = $("#field_timeFormat").val();
  var fieldName = $("#field_name").val();
  var isMandatory = $("#mandatoryCheckbox").prop('checked');

  var dataField = {
    defaultValue: defaultV,
    minValue: minV,
    maxValue: maxV,
    timeFormat: timeFormat,
    name: fieldName,
    fieldId: fieldId,
    mandatory: isMandatory,
  };

  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/saveEditedTimeField"), dataField,
    function (data) {
      $('#field-loading').hide();
      if (data.data !== null) {
        editTimeCode(data.data);
      } else if (data.errorMsg !== null) {
        apprise("Errors : " + getValidationErrorString(data.errorMsg));
      }
      $("#field-editor").dialog('close');
    }, "json");
  jqxhr.fail(function () {
    $('#field-loading').hide();
    $("#field-editor").dialog('close');
    RS.ajaxFailed("Saving field", false, jqxhr);
  });
}

function editTimeCode(data) {

  var fieldCode = $("#field_" + data.id);
  const mandatoryAsterisk = data.mandatory ? " <span style=\"color: red\">*</span>" : "";
  $(fieldCode).find("td[name='fieldName']").text(data.name);
  $(fieldCode).find("td[name='fieldName']").append(mandatoryAsterisk);
  $(fieldCode).find("input[name='dateField_" + data.id + "']").val(data.defaultTime);
  $(fieldCode).find("input[name='format_" + data.id + "']").val(data.timeFormat);
  $(fieldCode).find("input[name='minTime']").val(data.minTime);
  $(fieldCode).find("input[name='maxTime']").val(data.maxTime);
  $(fieldCode).find("input[name='minHour']").val(data.minHour);
  $(fieldCode).find("input[name='maxHour']").val(data.maxHour);
  $(fieldCode).find("input[name='minMinutes']").val(data.minMinutes);
  $(fieldCode).find("input[name='maxMinutes']").val(data.maxMinutes);
  $("#" + data.id + ".timepicker").timepicker("destroy");
  loadTimePicker(fieldCode);
  var date = $("#" + data.id + ".timepicker").datetimepicker('getDate');
  var hours;
  var min;
  if (date !== null) {
    hours = date.getHours();
    min = date.getMinutes();
    if (data.timeFormat == "hh:mm a") {
      $("#" + data.id + ".timepicker").val(
        $.datepicker.formatTime('hh:mm tt', {
          hour: hours,
          minute: min
        }, {
            ampm: true
          }));
    } else {
      $("#" + data.id + ".timepicker").val(
        $.datepicker.formatTime('h:mm', {
          hour: hours,
          minute: min
        }, {
            ampm: false
          }));
    }
  }
}

/**
 * Function classed when a field form is submitted
 */
function fieldFormValid(field) {
  $("#field-editor-form td").removeClass('field-error'); // clear highlighted field
  var fieldType = $("#fieldEditorSelect").val();
  var isValid = true;
  // if it is editing a field, fieldType needs to be taken from the hidden
  // input from the form.

  var fieldId = $("#fieldFormId").val();
  if (fieldId !== "") {
    fieldType = $("#fieldFormType").val();
  }

  if (fieldType == "Select Field Type") {
    isValid = addError("#fieldEditorSelect", "No field type selected.");
  } else {
    var MAX_FIELD_NAME_LENGTH = 50;
    if ("" === $("#field_name").val()) {
      isValid = addError("#field_name", "Field Name required.");
    } else if ($("#field_name").val().length > MAX_FIELD_NAME_LENGTH) {
      isValid = addError("#field_name", "Field name maximum length is "
        + MAX_FIELD_NAME_LENGTH + " characters");
    }
    if (fieldType === "Choice") {
      if ($("#choiceFields").children().length === 0) {
        isValid = addError("#addChoiceName",
          "No choices have been added.");
      }
    }
    if (fieldType == "Radio") {
      if ($("#radioFields").children().length === 0) {
        isValid = addError("#addRadioName",
          "No radios have been added.");
      }
    }
    if (fieldType == "Number") {
      var defaultValue = $("#field_defaultvalue").val();
      var minValue = $("#field_minvalue").val();
      var maxValue = $("#field_maxvalue").val();
      var defaultNumber = Number(defaultValue);
      if (minValue !== "") {
        minValue = Number(minValue);
      } else {
        minValue = Number.MIN_VALUE;
      }

      if (maxValue !== "") {
        maxValue = Number(maxValue);
      } else {
        maxValue = Number.MAX_VALUE;
      }

      if (minValue > maxValue) {
        isValid = addError("#field_minvalue",
          "Min value is greater than max value.");
      }

      // if the minValue maxValue is valid then do default value
      // validation, otherwise the user will be confused
      if (minValue < maxValue) {
        if (defaultValue !== "" && maxValue !== "") {
          if (defaultNumber > maxValue) {
            isValid = addError("#field_defaultvalue",
              "Default value greater than max value.");
          }
        }
        if (defaultValue !== "" && minValue !== "") {
          if (defaultNumber < minValue) {
            isValid = addError("#field_defaultvalue",
              "Default value less than min value. ");
          }
        }
      }
    }
    if (fieldType == "Time") {

      var defaultTime = $("#field_defaulttime.timePickerForm")
        .datetimepicker('getDate');
      var minTime = $("#field_minvalue.timePickerForm").datetimepicker(
        'getDate');
      var maxTime = $("#field_maxvalue.timePickerForm").datetimepicker(
        'getDate');

      if (defaultTime !== null) {
        defaultTime = defaultTime.getTime();
      }
      if (minTime !== null) {
        minTime = minTime.getTime();
      }
      if (maxTime !== null) {
        maxTime = maxTime.getTime();
      }
      if (minTime !== null && maxTime !== null) {
        if (minTime > maxTime) {
          isValid = addError("#field_minvalue",
            "The min value was greater than the max value. ");
        }
      }
      if (defaultTime !== null && maxTime !== null) {
        if (defaultTime > maxTime) {
          isValid = addError("#field_defaulttime",
            "The defualt time is greater than the max value. ");
        }
      }
      if (defaultTime !== null && minTime !== null) {
        if (defaultTime < minTime) {
          isValid = addError("#field_defaulttime",
            "The defualt time is less than the min value.");
        }
      }
    }

    if (fieldType == "Date") {
      var defaultDate = $("#field_defaultdate").datepicker("getDate");
      var minDate = $("#field_minvalue").datepicker("getDate");
      var maxDate = $("#field_maxvalue").datepicker("getDate");
      if (maxDate !== null && minDate !== null) {
        if (minDate > maxDate) {
          isValid = addError("#field_minvalue",
            "The min value is greater than the max value.");
        }
      }
      if (defaultDate !== null && minDate !== null) {
        if (defaultDate < minDate) {
          isValid = addError("#field_defaultdate",
            "The default date is less than the min value.");
        }
      }
      if (defaultDate !== null && maxDate !== null) {
        if (defaultDate > maxDate) {
          isValid = addError("#field_defaultdate",
            "The default date is greater than the max value.");
        }
      }
    }
  }

  if (!isValid) {
    $("#errorSummary").show();
  }
  return isValid;
}

function addError(field, message) {
  $("#errorSummary").append(
    "<img src=\"/images/iconWarning.gif\" alt=\"Warning\" class=\"icon\">" + message + " <br/>");
  $(field).parent().addClass('field-error');
  return false;
}

function loadRecord(record) {
  $.each(record.deletedFieldsIdAsList, function (i, val) {
    $("#field_" + val).slideUp("slow", function () {
      $("#field_" + val).remove();
    });
  });
}

/*
 * Saves template and goes to admin page
 */
function saveForm() {

  RS.blockPage("Saving form...");
  $.ajaxSetup({
    async: false
  });

  saveFormTag();
  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/saveForm"), {
    templateId: recordId
  }, function (success) {
    if (success.data !== null) {
      var parentRecordId = success.data;
      wasAutosaved = false;
      $.ajaxSetup({
        async: true
      });
      RS.unblockPage();
      window.location = createURL(LISTFORMS_BY_MODFICATIONDATE_DESC);
    }
  }, "json");

  jqxhr.fail(function () {
    RS.ajaxFailed("Saving form", true, jqxhr);
  });
}

function updateForm() {
  RS.blockPage("Updating  form...");
  $.ajaxSetup({
    async: false
  });

  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/updateForm"), {
    templateId: recordId
  }, function (data) {
    if (data.data !== null) {
      wasAutosaved = false;
      $.ajaxSetup({
        async: true
      });
      RS.unblockPage();
      $('#noMessages').html("Form updated to new version..");
      setTimeout(function () {
        window.location = createURL(LISTFORMS_BY_MODFICATIONDATE_DESC);
      }, 1000);

    } else if (data.errorMsg !== null) {
      apprise("Errors : " + getValidationErrorString(data.errorMsg));
      $.ajaxSetup({
        async: true
      });
      RS.unblockPage();
    }
  }, "json");

  jqxhr.fail(function () {
    RS.ajaxFailed("Updating form", true, jqxhr);
  });
}

function changeOrder() {

  // Reorder fields dialog.
  $("#reorderFieldsDialog").dialog({
    resizable: false,
    autoOpen: false,
    height: 500,
    width: 500,
    modal: true,
    open: function () {
      // When open the dialog, populate with visible fields !
      $('.field').each(function () {
        var id = $(this).attr('id').split("_")[1];
        var name = $(this).children().first().text().toLowerCase();
        var row = "<tr id='row_" + id + "' class='reorderField'><td class='fieldLabel'><input id='radio_" + id + "' type=radio name=fields><label>" + RS.escapeHtml(name) + "</label></td><td>" +
          "<div id='buttonBlock_" + id + "' class='orderButtons' style='display:none;'>" +
          "<button id='top_" + id + "' type=button alt='Top' title='Top' class=fieldMover><img src='/images/icons/upAll.png'></button>" +
          "<button id='up_" + id + "' type=button alt='Up' title='Up' class=fieldMover><img src='/images/icons/upOne.png'></button>" +
          "<button id='down_" + id + "' type=button alt='Down' title='Down' class=fieldMover><img src='/images/icons/downOne.png'></button>" +
          "<button id='bottom_" + id + "' type=button alt='Bottom' title='Bottom' class=fieldMover><img src='/images/icons/downAll.png'></button>" +
          "</div>" + "</td></tr>";
        $("#reorderFieldTable").append(row);
      });
    },
    close: function () {
      $(".reorderField").remove();
    },
    buttons: {
      Cancel: function () {
        $(this).dialog('close');
      },
      'Done': function () {
        var ids = [];
        $('.reorderField').each(function () {
          var id = $(this).attr('id').split("_")[1];
          ids.push(id);
        });

        var jqxhr = $.post("/workspace/editor/form/reorderFields",
          {
            formId: recordId,
            "fieldids[]": ids
          }, function (response) {
            if (response.data) {
              RS.confirm("Fields reordered, page reloading", "success", 3000);
              location.href = "/workspace/editor/form/edit/" + recordId;
            }
          });

        jqxhr.fail(function () {
          RS.ajaxFailed("Reordering fields", false, jqxhr);
        });
      }
    }
  });

  // Call to open the reorder field dialog.
  $("#reorderFieldsDialog").dialog("open");
}

function abandonUpdateForm() {
  RS.blockPage("Abandoning form updates...");
  $.ajaxSetup({
    async: false
  });

  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/abandonUpdateForm"), {
    templateId: recordId
  }, function (data) {
    if (data.data !== null) {
      wasAutosaved = false;
      $.ajaxSetup({
        async: true
      });
      RS.unblockPage();
      window.location = createURL(LISTFORMS_BY_MODFICATIONDATE_DESC);
    } else if (data.errorMsg !== null) {
      apprise("Errors : " + getValidationErrorString(data.errorMsg));
      $.ajaxSetup({
        async: true
      });
      RS.unblockPage();
    }
  }, "json");

  jqxhr.fail(function () {
    RS.ajaxFailed("Reverting to previous form version", true, jqxhr)
  });
}

// Take the value of a number field and save/autosave
function saveNumber(element, autosave) {
  var inputs = $(element).find(".field-value-inner input");
  inputs = inputs.serialize();
  return inputs;
}

// Take the value of a number field and save/autosave
function saveString(element, autosave) {
  var inputs = $(element).find(".field-value-inner input");
  inputs = inputs.serialize();
  return inputs;
}

// Take the value of a number field and save/autosave
function saveText(element, autosave) {
  var inputs = $(element).find(".field-value-inner input");
  inputs = inputs.serialize();
  var textarea = $(element).find(".field-value-inner textarea");
  textarea = textarea.serialize();
  inputs = inputs + "&" + textarea;
  return inputs;
}

// Take the value of a number field and save/autosave
function saveRadio(element, autosave) {
  var inputs = $(element).find(".field-value-inner input");
  inputs = inputs.serialize();
  return inputs;
}

// Take the value of a number field and save/autosave
function saveChoice(element, autosave) {
  var inputs = $(element).find(".field-value-inner input");
  inputs = inputs.serialize();
  return inputs;
}

function checkNumber(element) {
  var value = $(element).find("input[name='fieldData']").val();
  //var decimalPlaces = $(element).find("input[name='decimalPlaces']").val();
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
  } else if (value !== "" && maxValue !== "") {
    if (numberData > maxNumber) {
      error = "Value greater than max value(" + maxValue + ").";
    }
  } else if (value !== "" && minValue !== "") {
    if (numberData < minNumber) {
      error = "Value less than min value(" + minValue + "). ";
    }
  }
  return error;
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

  var timeformat;
  if (formatTime == "hh:mm a") {
    timeformat = true;

  } else {
    timeformat = false;
  }

  if (minTime > 0 && maxTime > 0) {
    $("#" + idDate + ".timepicker").timepicker({
      ampm: timeformat,
      minDate: new Date(1970, 01, 02, minHour, minMinutes),
      maxDate: new Date(1970, 01, 02, maxHour, maxMinutes),
    });
  } else if (minTime > 0 && maxTime === 0) {
    $("#" + idDate + ".timepicker").timepicker({
      ampm: timeformat,
      minDate: new Date(1970, 01, 02, minHour, minMinutes)

    });
  } else if (minTime === 0 && maxTime > 0) {
    $("#" + idDate + ".timepicker").timepicker({
      ampm: timeformat,
      maxDate: new Date(1970, 01, 02, maxHour, maxMinutes)

    });
  } else {
    $("#" + idDate + ".timepicker").timepicker({
      ampm: timeformat
    });
  }
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
  } else if (minDate > 0 && maxDate === 0) {
    $("#" + idDate + ".datepicker").datepicker({
      dateFormat: dateformat,
      minDate: new Date(minDate)

    });
  } else if (minDate === 0 && maxDate > 0) {
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

function deleteField(fieldId) {
  var jqxhr = $.post(createURL("/workspace/editor/form/ajax/deleteField"), { fieldId: fieldId }, function (data) {
    if (data.data) {
      RS.blockPage("Deleting field...");
      $("#field_" + fieldId).slideUp("slow", function () {
        $("#field_" + fieldId).remove();
      });
      RS.unblockPage();
    } else {
      apprise(getValidationErrorString(data.error));
    }
  }, "json");

  jqxhr.fail(function () {
    RS.ajaxFailed("Deleting field", false, jqxhr);
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
    RS.ajaxFailed("Saving form tags", false, jqxhr);
  });
};

function parseRadioOptions(text, { isCSV }) {
  let textArray;
  if (isCSV) {
    textArray = text.split(/\r?\n/).join(",").split(",");
  } else {
    textArray = text.split(/\r?\n/);
  }
  textArray = textArray.filter(a => a.length>0);
  const uniqueText = new Set(textArray);
  let displayText = [];
  for(const item of uniqueText){
    displayText.push(item.trim());
  }
  return displayText;
}

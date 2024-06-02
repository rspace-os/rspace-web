/*
 * To be used in conjunction with message_ajax.jsp. Provides JS for the dialog
 * Takes an optional option hash
 * - recordIdGetter An optional function returning recordId (to link to the record)
 * - targetFinderPolicy. String 'ALL' which looks up all users,  or 'STRICT' (Default)
 * - availableMessageTypes an  optional comma separated list of the available MessageType options
 */
function initialiseRequestDlg(options) {

  var _recordIdGetter;
  var _availableMessageTypes;
  var _targetPolicy;
  var _dialogDivSelector$="#createRequestDlg";
  var _dialogDivSelector$Content = _dialogDivSelector$+"Content";

  var messageTypeToTargetPolicy = {
    "REQUEST_EXTERNAL_SHARE": "ALL_PIS",
    "SIMPLE_MESSAGE": "ALL",
    "GLOBAL_MESSAGE": "ALL",
    "REQUEST_JOIN_EXISTING_COLLAB_GROUP": "ALL_PIS",
    "REQUEST_RECORD_REVIEW": "STRICT"
  };

  if (options) {
    _recordIdGetter = options.recordIdGetter || null;
    _availableMessageTypes = options.availableMessageTypes || null;
    _targetPolicy = options.targetFinderPolicy || 'STRICT';
    _dialogDivSelector$ = options.dialogDivSelector || '#createRequestDlg';
    _dialogDivSelector$Content = options.dialogDivSelector || '#createRequestDlgContent';
  }

  // sets up request creation dialog
  $(document).ready(function(){
    RS.switchToBootstrapButton();
    $(_dialogDivSelector$).dialog({
      title: "Send a Message",
      resizable: true,
      autoOpen: false,
      height: 500,
      width: 600,
      modal: true,
      create: function() {
        $('body').on("change", 'select.msgTypes', function() {
          var msgType = $(this).find(":selected").val();
          $('.date').toggle(msgType !== "SIMPLE_MESSAGE");

          _targetPolicy = messageTypeToTargetPolicy[msgType];
          if (msgType == "REQUEST_EXTERNAL_SHARE") {
            getPotentialRecipients('ALL_PIS');
          } else if (msgType == "SIMPLE_MESSAGE") {
            getPotentialRecipients('ALL');
          } else if (msgType == "REQUEST_JOIN_EXISTING_COLLAB_GROUP") {
            getPotentialRecipients('ALL_PIS');
          } else {
            getPotentialRecipients(_targetPolicy);
          }
        });
        $(this).parent().addClass('bootstrap-custom-flat');
      },
      open: function() {
        var recipient = $(_dialogDivSelector$).data('recipient');
        var data = {};
        // add optional group id if this 
        if ($(this).data("groupId") != null) {
          data.groupId = $(this).data("groupId");
        }
        if (_availableMessageTypes != null) {
          data["messageTypes[]"] = _availableMessageTypes;
        }
        // get new object
        $.get(createURL('/messaging/ajax/create'), data, function(xhr) {
          $(_dialogDivSelector$Content).html(xhr);
          $('input.date').datetimepicker({
            dateFormat: "yy-mm-dd",
            minDate: "+0d", // must be a future date
          });
          $('.date').hide();
          // init first
          var firstOption = $('#messageType').first().val();
          _targetPolicy = messageTypeToTargetPolicy[firstOption];

          // populate hidden field
          getPotentialRecipients(_targetPolicy);

          _populateMessageCharCounter();

          if(recipient != null && recipient != "") {
            $(_dialogDivSelector$).data('recipient', "");
            $("#recipientnames").val(recipient);
            $("#optionalMessage").focus();
          } else {
            // Force focus on the first field of the dialog
            $("#recipientnames").focus();
          }
        });
        return this;
      },
      buttons: [{
          id: 'msgDlgCancel',
          text: 'Cancel',
          click: function() {
            $(this).dialog('close');
          }
        },
        // persists request
        {
          id: 'msgDlgSend',
          text: 'Send',
          click: function() {

            var messageLength = $('.optionalMessageArea').val().length;
            if (messageLength > 2000) {
              apprise('Optional Message is too long <br /> (' + messageLength + '/2000 characters)');
              return;
            }

            var dlg$ = $(this);
            var dlgWindow$ = dlg$.parent('.ui-dialog');
            var form$ = $('form.requestForm');
            form$.find("input[name=recordId]").val(_getRecordId());
            var json = form$.serialize();

            RS.blockPage('Sending...', false, dlgWindow$);
            var jqxhr = $.post(createURL('/messaging/ajax/create'), json);
            jqxhr.always(function() {
              RS.unblockPage(dlgWindow$);
            });
            jqxhr.done(function(xhr) {
              // this means that an validation error has been found
              if (xhr.indexOf("error") != -1) {
                // might be validation error messages
                $(_dialogDivSelector$Content).html(xhr);
                var msgType = $('select.msgTypes').find(":selected").val();
                $('.date').toggle(msgType !== "SIMPLE_MESSAGE");
                _populateMessageCharCounter();
                getPotentialRecipients(_targetPolicy);
                return;
              }
              // we get an empty response
              var text$ = $("<p style='text-align:center'>" +
                "<img src='/images/tick-icon.png'/> Request sent</p>");
              $(_dialogDivSelector$Content).prepend(text$);
              text$.fadeOut(2000, function() {
                dlg$.dialog('close');
              });
            });
            jqxhr.fail(function() {
              RS.ajaxFailed("Sending message", false, jqxhr);
            });
          }
        }
      ] // end  buttons
    });
    RS.switchToJQueryUIButton();
  });

  function getPotentialRecipients(_targetPolicy) {

    var form$ = $('form.requestForm');
    form$.find("input[name=recordId]").val(_getRecordId());
    form$.find("input[name=targetFinderPolicy]").val(_targetPolicy);

    var dataGetter = function() {
      return {
        messageType: $('form.requestForm').find("#messageType").val(),
        recordId: _getRecordId(),
        targetFinderPolicy: _targetPolicy
      }
    };
    setUpAutoCompleteUsernameBox('#recipientnames', '/messaging/ajax/recipients', dataGetter);
  }

  // stops dlg submission when there are no recipients
  function _disableDialog() {
    $('#msgDlgSend').bootstrapButton("disable");
    $('#recipientnames').prop('disabled', true);
    $('#optionalMessage').prop('disabled', true);
  }

  // re-enables dlg submission when there are recipients
  function _enableDialog() {
    $('#msgDlgSend').bootstrapButton("enable");
    $('#recipientnames').prop('disabled', false);
    $('#optionalMessage').prop('disabled', false);
  }

  function _populateMessageCharCounter() {
    $('.optionalMessageArea').simplyCountable({
      counter: '#optionalMessageCounter',
      countDirection: 'up',
      maxCount: 2000,
      overClass: 'optionalMessageTooLong'
    });
  }

  function _getRecordId() {
    return _recordIdGetter ? _recordIdGetter() : null;
  }

}
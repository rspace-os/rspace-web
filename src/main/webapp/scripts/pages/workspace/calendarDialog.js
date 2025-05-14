var currentlySelectedCalendarAttachment = null;
var currentlyAttachedFiles = [];

function clearCalendarDialogAttachments() {
    currentlyAttachedFiles = [];
    $('#cd_current_files').html('<li style="list-style: none;">None</li>');
}

function initCreateCalendarEntryDlg() {
  console.debug("initCreateCalendarEntryDlg");
  if (!RS.useBootstrapModals) {
     $('#createCalendarEntryDlg').dialog({
        modal: true,
        height: 500,
        width: 600,
        autoOpen: false,
        title: "Create a calendar entry",
        buttons: {
            Cancel: function() {
                $(this).dialog('close');
            },
            Create: function() {
              var eventTitle = $('#cd_event_title').val();
              var eventStart = $('#cd_event_start').val();
              var eventEnd = $('#cd_event_end').val();
              var repeat = $('#cd_repeat_event').is(':checked');
              var frequency = $('#cd_frequency_select').val();
              var occurrences = $('#cd_repeat_n_times').val();
              var description = $('#cd_description').val();

              // Event title is a required field
              if (!eventTitle || eventTitle.length === 0) {
                  $().toastmessage('showErrorToast', 'Event title is a required field.');
                  return false;
              }

              // Event start is a required field
              if (!eventStart || eventStart.length === 0) {
                  $().toastmessage('showErrorToast', 'Event start is a required field.');
                  return false;
              }

              // Repeat for number of times should be a positive integer
              if (repeat && (!$.isNumeric(occurrences) || parseInt(occurrences) <= 0 || parseInt(occurrences) != occurrences)) {
                  $().toastmessage('showErrorToast', 'Number of times must be a positive integer!');
                  return false;
              }

              var attachedFiles = "";
              for (var i = 0; i < currentlyAttachedFiles.length; i++) {
                  if (i > 0)
                      attachedFiles += ',';
                  attachedFiles += currentlyAttachedFiles[i].global_id;
              }
              if (eventStart.length > 0) {
            	  eventStart = new Date(eventStart).toISOString()
              }
              if (eventEnd.length > 0) {
            	  eventEnd = new Date(eventEnd).toISOString()
              }

              data = {
                  title: eventTitle,
                  start: eventStart,
                  end: eventEnd,
                  description: description,
                  attachments: attachedFiles
              };
              if (repeat) {
                  data.frequency = frequency;
                  data.occurrences = occurrences;
              }

              $.post("/messaging/create_calendar_event", data).done(function (response) {
              	RS.trackEvent('CalendarEventCreated', {
                  repeat: repeat,
              	    description: description,
              	});
              	   
                  if (response.data && !response.error) {
                      $().toastmessage('showSuccessToast', 'Calendar event is being downloaded.');
                      $('#createCalendarEntryDlg').dialog('close');

                      window.location.href = window.location.href = "/messaging/get_calendar_event";
                  } else {
                      $().toastmessage('showErrorToast', response.error.errorMessages[0]);
                  }
              }).fail(function (response) {
                  $().toastmessage('showErrorToast', 'An error occurred during the creation of the calendar event.');
              });
            }
        },
        create: function() {
          $(this).parent().addClass('bootstrap-custom-flat');
        },
        open: function() {
            $('#cd_event_start').datetimepicker({
                minDate: "+0d", // must be a future date
            });
            $('#cd_event_end').datetimepicker({
                minDate: "+0d", // must be a future date
            });

            /*
            var ensureMessagingFieldsVisibility = function() {
                if ($('#cd_share_event').is(':checked')) {
                    $('#cd_share_event_to').show();
                    $('#cd_message_text').show();
                } else {
                    $('#cd_share_event_to').hide();
                    $('#cd_message_text').hide();
                }
            }
            ensureMessagingFieldsVisibility();
            $('#cd_share_event').change(ensureMessagingFieldsVisibility);
            */

            var ensureResourcesTreeViewVisibility = function() {
                if ($('#cd_include_links_to_resources').is(':checked')) {
                    $('#calendar_file_tree').fileTree({
                        root: '/',
                        script: '/fileTree/ajax/filesInModel',
                        expandSpeed: 25,
                        /* minimal delay*/
                        collapseSpeed: 25,
                        multiSelect: true,
                        preventLinkAction: true
                    }, function(file, type, $node){
                        currentlySelectedCalendarAttachment = {
                            global_id: $node.attr('data-globalid'),
                            name: $node.text()
                        }
                        $('#selectedNode').val($node.data('name'));
                    });
                    $('#cd_tree_view').show();
                } else {
                    $('#cd_tree_view').hide();
                }
            }
            ensureResourcesTreeViewVisibility();
            $('#cd_include_links_to_resources').change(ensureResourcesTreeViewVisibility);

            var ensureEventFrequencyFieldsVisibility = function() {
                if ($('#cd_repeat_event').is(':checked')) {
                    $('#cd_repeat_event_frequency').show();
                    $('#cd_repeat_event_times').show();
                } else {
                    $('#cd_repeat_event_frequency').hide();
                    $('#cd_repeat_event_times').hide();
                }
            }
            ensureEventFrequencyFieldsVisibility();
            $('#cd_repeat_event').change(ensureEventFrequencyFieldsVisibility);

            /*
            var dataGetter = function() {
              return {
                messageType: 'SIMPLE_MESSAGE',
                targetFinderPolicy: 'ALL'
              }
            };
            setUpAutoCompleteUsernameBox('#cd_share_to', '/messaging/ajax/recipients', dataGetter);
            */

            clearCalendarDialogAttachments();
        }
     });
  } else {
     console.log('create calendar entry dialog doesn\'t support this yet');
  }

  $('#cd_add_file').click(function() {
    $('#attachFileToCalendarEntryDlg').dialog('open');
  });

  $('#cd_clear').click(function() {
    clearCalendarDialogAttachments();
  });
}

function initAttachFileToCalendarEntryDlg() {
    if (!RS.useBootstrapModals) {
        $('#attachFileToCalendarEntryDlg').dialog({
        modal: true,
        autoOpen: false,
        title: "Attach a file",
        width: 600,
        height: 500,
        buttons: {
            Cancel: function() {
                // Unselect the record
                $('#selectedNode').val("");
                currentlySelectedCalendarAttachment = null;

                $(this).dialog('close');
            },
            Attach: function() {
                if (currentlySelectedCalendarAttachment !== null) {
                    // Append to the selected files
                    currentlyAttachedFiles.push(currentlySelectedCalendarAttachment)
                    if (currentlyAttachedFiles.length == 1) {
                        $('#cd_current_files').html('');
                    }
                    $('#cd_current_files').append("<li>" + currentlySelectedCalendarAttachment.name + "</li>");

                    // Unselect the record
                    $('#selectedNode').val("");
                    currentlySelectedCalendarAttachment = null;

                    $(this).dialog('close');
                } else {
                    alert('Nothing was selected!');
                }
            }
        },
        open: function() {
        }
        });
    } else {
        console.log('create calendar entry dialog doesn\'t support this yet');
    }
}

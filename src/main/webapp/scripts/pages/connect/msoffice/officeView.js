
var office_frame;

function initOfficeOnlineFrame() {
    var frameholder = document.getElementById('frameholder');
    office_frame = document.createElement('iframe');
    office_frame.name = 'office_frame';
    office_frame.id = 'office_frame';

    // The title should be set for accessibility
    office_frame.title = 'Office Online Frame';

    // This attribute allows true fullscreen mode in slideshow view
    // when using PowerPoint Online's 'view' action.
    office_frame.setAttribute('allowfullscreen', 'true');

    // The sandbox attribute is needed to allow automatic redirection to the
    // O365 sign-in page in the business user flow
    office_frame.setAttribute('sandbox',
            'allow-scripts allow-same-origin allow-forms allow-popups allow-top-navigation allow-popups-to-escape-sandbox');
    frameholder.appendChild(office_frame);

    document.getElementById('office_form').submit();
}

function initConvertDialog() {
    $('#convertDlg').dialog({
        title : "Conversion Required",
        resizable : true,
        modal : true,
        autoOpen : false,
        height : 290,
        minWidth : 620,
        hide : 'fade',
        show : 'fade',
        open: function() {
            $('.ui-dialog-buttonset button:eq(1)').focus();
        },
        buttons : {
            "No, just keep viewing" : function() {
                office_frame.contentWindow.postMessage("Grab_Focus", "*");
                $(this).dialog("close");
            },
            "Yes, convert and edit" : function() {
                window.location.href = convertActionUrl;
                $(this).dialog("close");
            }
        }
    });
}

function handlePostMessage(e) {
    var msg = JSON.parse(e.data);
    var msgId = msg.MessageId;

    if (msgId === "UI_Edit") {
        console.log('edit in browser triggered');
        if (editActionAvailable) {
            window.location.href = editActionUrl;
        } else if (convertActionAvailable) {
            $('#convertDlg').dialog('open');
            office_frame.contentWindow.postMessage("Blur_Focus", "*");
        }
    }
}

$(document).ready(function() {
    initOfficeOnlineFrame();
    
    if (convertActionAvailable) {
      initConvertDialog();
    }
    window.addEventListener('message', handlePostMessage, false);
});
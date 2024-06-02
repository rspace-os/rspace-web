var collabora_frame;

function initCollaboraOnlineFrame() {
    var frameholder = document.getElementById('frameholder');
    collabora_frame = document.createElement('iframe');
    collabora_frame.name = 'collabora_frame';
    collabora_frame.id = 'collabora_frame';

    // The title should be set for accessibility
    collabora_frame.title = 'Collabora Online Frame';

    frameholder.appendChild(collabora_frame);

    document.getElementById('collabora_form').submit();
}

function handlePostMessage(e) {
    var msg = JSON.parse(e.data);
    if (msg.MessageId == 'App_LoadingStatus') {
        if (msg.Values) {
            if (msg.Values.Status == 'Document_Loaded') {
                console.log('==== Document loaded ...init viewer...');
                var iframe = document.getElementById('collabora_frame');
                iframe = iframe.contentWindow || (iframe.contentDocument.document || iframe.contentDocument);
                iframe.postMessage(JSON.stringify({ 'MessageId': 'Host_PostmessageReady' }), '*');

                // Show close button
                console.log("Adding close button to collabora...")
                iframe.postMessage(JSON.stringify({
                    "MessageId": "Insert_Button",
                    "SendTime": Date.now(),
                    "Values": {
                        "id": "rspaceclose",
                        "imgurl": "/browser/dist/images/closedoc.svg",
                        "hint": "Save and Close",
                        "mobile": false,
                        "label": "Save and Close",
                        "insertBefore": "sidebar",
                        "unoCommand": ""
                    }
                }), '*');
            }
        }
    } else if (msg.MessageId == 'Clicked_Button') {
        if (msg.Values) {
            if (msg.Values.Id == 'rspaceclose') {
                console.log("Closing document...");
                window.close();
            }
        }
    }
}

$(document).ready(function() {
    initCollaboraOnlineFrame();
    window.addEventListener('message', handlePostMessage, false);
});
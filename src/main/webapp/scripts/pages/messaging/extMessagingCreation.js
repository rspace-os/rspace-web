/*
 */
function _initialiseExtMessageRequestDlg(recordIdsGetter, appName) {
  $(document).ready(function() { 
    RS.switchToBootstrapButton();
  	$('#extMessageRequestDlg_'+appName).dialog({
  		title: "Send message to external messaging platform",
  		resizable: true, 
  		autoOpen: false,
  		height: 330,
  		width: 500,
  		modal: true,
  		open: function() {
  			console.log('opening external messaging dialog');
  			var singleDocSelected = recordIdsGetter().length == 1;
  			$('.extMessageRequestMessageLegend_singleDoc').toggle(singleDocSelected);
  			$('.extMessageRequestMessageLegend_manyDocs').toggle(!singleDocSelected);
  		},
  		buttons: {
  		    Cancel: function() {
      			$(this).dialog('close');
  			},
  			Send: function() {
  			    console.log('sending to '+appName);
  			    
  			    var channelId = $(this).find('.channelSelect').val();
  			    var message = $(this).find('.extMessageRequestMessage').val();
  			    
  			    var data = {
  		            "recordIds": recordIdsGetter(),
  		            "appConfigElementSetId": channelId,
  		            "message": message
  			    }
  			    
  			    RS.blockPage("Sending the message to Channel...");
  			    
  			    var jqxhr = $.post('/messaging/ajax/sendExternalMessage', data);
  			    jqxhr.done(function(result) {
  	                var sendResult = result.data;
  	                if (sendResult) {
  	                    $().toastmessage('showSuccessToast', 'Message sent');
  	                    $('.extMessageRequestDlg').dialog('close');
  	                } else if (result.errorMsg) {
  	                    $().toastmessage('showErrorToast', getValidationErrorString(result.errorMsg));
  	                }
  			    });
  	            jqxhr.fail(function() {
  	                RS.ajaxFailed("Sending message", false, jqxhr);
  	            });
                  jqxhr.always(function () {
                      RS.unblockPage();
                  });
  			}
  		}
  	});
  	RS.switchToJQueryUIButton();
  });
}

var _extMessageRequestDialogChannels = {};

function addExtMessageChannel(id, label, extMessageType) {
    _extMessageRequestDialogChannels[id] = {"label":label, "type":extMessageType};
    console.log('adding channel: ' + id + ' with label: ' + label + ' of type ' + extMessageType);
}

function appendExtMessageChannelsToRequestDialog(extMessageType) {
    $.each(_extMessageRequestDialogChannels, function(id, label) {
        $('#extMessageChannelsSelect')
            .append($("<option></option>")
                        .attr("value", id)
                        .text(label.label));
        $('.externalMessageDlgAppName').html(extMessageType)
    });
}

function initialiseExtMessageChannelListButtonAndDialog(recordIdsGetter, btnSelector) {
	
	$('body').on('click', btnSelector, function() {
		var selectedDocs = recordIdsGetter();
	    if (selectedDocs.length > 20) {
	    	apprise("There is a limit of 20 links that can be included with the external message.<br> Please select fewer records.");
	    	return;
	    } else if (selectedDocs.length == 1 && selectedDocs[0] == null) {
	    	apprise("There is no entry that could be linked in external message.");
	    	return;
	    }
	    var appName = $(this).data('app');
    	$('#extMessageRequestDlg_'+appName).remove();
    	var jqxhr =  $.get('/integration/integrationInfo', {"name":appName}, function (resp) {
    		var data = resp.data;
    		console.log(data);
    		if($.isEmptyObject(data.options)) {
    			 apprise('No messaging channels were set up yet. Please go to <a href="/apps" target="_blank">Apps</a> page and add the communication channels that you want to use.');
    		} else {
    			var dlgTemplate = $('#extMessageRequestDlg-template').html();
    			var channels = [];
    			for (key in data.options) {
    				var channelName = appName + "_CHANNEL_LABEL";
    				if (data.options[key][channelName]) {
    				    channels.push({"id":key, "label":data.options[key][channelName]});
    				}
    			}
    	        var dlgTemplateHtml = Mustache.render(dlgTemplate, {"channels": channels, 
    	        	"name":appName, "label":data.displayName});
    	        RS.appendMustacheGeneratedHtmlToElement(dlgTemplateHtml, 'body');
    	        _initialiseExtMessageRequestDlg(recordIdsGetter, appName);
    	        $('#extMessageRequestDlg_'+appName).dialog('open');
    		}
    	}); 
    });
   
}

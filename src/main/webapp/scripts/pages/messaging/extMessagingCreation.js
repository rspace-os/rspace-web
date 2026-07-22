/*
 */
function _initialiseExtMessageRequestDlg(recordIdsGetter, appName) {
  $(document).ready(function() { 
    RS.switchToBootstrapButton();
  	$('#extMessageRequestDlg_'+appName).dialog({
  		title: RS.msg("legacyjs.messaging.extMessageDialogTitle"),
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
  		    [RS.msg("legacyjs.common.cancel")]: function() {
      			$(this).dialog('close');
  			},
  			[RS.msg("legacyjs.common.send")]: function() {
  			    console.log('sending to '+appName);
  			    
  			    var channelId = $(this).find('.channelSelect').val();
  			    var message = $(this).find('.extMessageRequestMessage').val();
  			    
  			    var data = {
  		            "recordIds": recordIdsGetter(),
  		            "appConfigElementSetId": channelId,
  		            "message": message
  			    }
  			    
  			    RS.blockPage(RS.msg("legacyjs.messaging.sendingToChannel"));
  			    
  			    var jqxhr = $.post('/messaging/ajax/sendExternalMessage', data);
  			    jqxhr.done(function(result) {
  	                var sendResult = result.data;
  	                if (sendResult) {
  	                    $().toastmessage('showSuccessToast', RS.msg("legacyjs.messaging.messageSent"));
  	                    $('.extMessageRequestDlg').dialog('close');
  	                } else if (result.errorMsg) {
  	                    $().toastmessage('showErrorToast', getValidationErrorString(result.errorMsg));
  	                }
	                    RS.trackEvent('user:external_message:sent:' + appName.toLowerCase());
  			    });
  	            jqxhr.fail(function() {
  	                RS.ajaxFailed(RS.msg("legacyjs.messaging.actionSendingMessage"), false, jqxhr);
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
	    	apprise(RS.msg("legacyjs.messaging.extMessageLinkLimit"));
	    	return;
	    } else if (selectedDocs.length == 1 && selectedDocs[0] == null) {
	    	apprise(RS.msg("legacyjs.messaging.extMessageNoLinkableEntry"));
	    	return;
	    }
	    var appName = $(this).data('app');
    	$('#extMessageRequestDlg_'+appName).remove();
    	var jqxhr =  $.get('/integration/integrationInfo', {"name":appName}, function (resp) {
    		var data = resp.data;
    		console.log(data);
    		if($.isEmptyObject(data.options)) {
    			 apprise(RS.msg("legacyjs.messaging.noChannelsSetUp"));
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

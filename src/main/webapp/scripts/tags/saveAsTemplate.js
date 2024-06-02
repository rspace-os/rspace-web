function initSaveAsTemplateDlg() {

    RS.switchToBootstrapButton();
    $('#saveAsTemplateDlg').dialog({
        title: "Save Template",
        resizable: true, 
        autoOpen: false,
        minHeight: 200,
        maxHeight: 450,
        width: 400,
        modal: true,
        buttons: 
        {
            Cancel: function() {$(this).dialog('close');},
            OK: function () {

                var IDs = [];
                $("#saveAsTemplateDlg").find("input").each(function(){ IDs.push(this.id); });
        
                var sendIds=[];
                var cnt =0;
                var ix=0;
                for(ix=0; ix<IDs.length; ix++) {
                    var fid = "#"+IDs[ix];
                    if(($(fid).is(':checked'))==true)
                    {
                        sendIds[cnt] = IDs[ix];
                        cnt++;
                    }
                }
                // this is essential else if empty will send a null array to server throwing an error
                if(cnt==0){
                    apprise("Please choose at least one field!");
                    return;
                    
                }
                var tmp_name = $("#template_name").val();
                RS.blockPage("Creating template...");
                var jqxhr = $.post(createURL("/workspace/editor/structuredDocument/saveTemplate"),
                        { fieldCompositeIds:sendIds,
                          recordId:recordId,
                          templateName:tmp_name },
                        function (result) { 
                            RS.unblockPage();
                            RS.confirm(result, "success", 3000);
                        });
                jqxhr.fail(function(){
                    RS.unblockPage();
                    //RSPAC-1712. Show special message if not authorised
                    if(jqxhr.status == 404) {
                    	apprise("Sorry, you may not create a template from a file you do not own. <br/>" +
                    			"Please ask the owner to create a template from this document, and share it with you.")
                    } else {
                    	 RS.ajaxFailed("Create template", false, jqxhr);
                    }
                   
                });

                $(this).dialog('close');
            },  
        }//end of buttons
    });
    RS.switchToJQueryUIButton();
};


function openSaveAsTemplateDlg(){
    $("input").removeClass('ui-helper-hidden-accessible');
    $("#template_name").removeAttr("disabled");
    
    if (nameEditMode) {
        var name = RS.removeForbiddenCharacters($(nameFeatureSelectors.editorSelector).val(), nameForbiddenCharacters);
        $("#saveAsTemplateDlg #template_name").val(name + "_template");
    }

	$('#saveAsTemplateDlg').dialog('open');
}

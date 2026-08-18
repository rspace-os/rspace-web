function initSaveAsTemplateDlg() {

    RS.switchToBootstrapButton();
    $('#saveAsTemplateDlg').dialog({
        title: RS.msg("legacyjs.core.saveTemplate.title"),
        resizable: true, 
        autoOpen: false,
        minHeight: 200,
        maxHeight: 450,
        width: 400,
        modal: true,
        buttons: 
        {
            [RS.msg("legacyjs.common.cancel")]: function() {$(this).dialog('close');},
            [RS.msg("legacyjs.common.ok")]: function () {

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
                    apprise(RS.msg("legacyjs.core.saveTemplate.chooseField"));
                    return;
                    
                }
                var tmp_name = $("#template_name").val();
                RS.blockPage(RS.msg("legacyjs.core.saveTemplate.creating"));
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
	apprise(RS.msg("legacyjs.core.saveTemplate.notOwner"))
                    } else {
	 RS.ajaxFailed(RS.msg("legacyjs.core.saveTemplate.createAction"), false, jqxhr);
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
        var name = $(nameFeatureSelectors.editorSelector).val().replace(/./gi, function check(c) {
            if (nameForbiddenCharacters.indexOf(c) >= 0) return '';
            return c;
        });
        $("#saveAsTemplateDlg #template_name").val(name + "_template");
    }

	$('#saveAsTemplateDlg').dialog('open');
}

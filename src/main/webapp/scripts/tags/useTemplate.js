
function initUseTemplateDlg(templateIdAndNameGetter) {
    
  $(document).ready(function () {
	initFolderChooser('-useTemplate');

	RS.switchToBootstrapButton();
	$('#useTemplateDlg').dialog({
		modal: true,
		autoOpen: false,
	    title: RS.msg("legacyjs.core.useTemplate.title"),
	    width: 420,
	    open : function(event, ui) {
            showFolderChooser('-useTemplate', RS.msg("legacyjs.core.useTemplate.folderChooserDescription"));
            $("#useTemplateNameInput").val("from_" + templateIdAndNameGetter().name);
	    },
	    buttons: {
			[RS.msg("legacyjs.common.cancel")]: function (){$(this).dialog('close');},
			[RS.msg("legacyjs.core.action.create")]: function() {
			    
                var targetFolderId = -1; // root folder
			    var selectedFolder = $('#folderChooser-id-useTemplate').val();
			    if (selectedFolder && selectedFolder.length > 0) {
			        targetFolderId = selectedFolder.trim();
			    }

			    console.log('submitting the form...')
			    
			    var actionUrl = "/workspace/editor/structuredDocument/createFromTemplate/" + targetFolderId;
			    var templateId = templateIdAndNameGetter().id;
			    var docName = $("#useTemplateNameInput").val();
			    
			    var $form = $('#useTemplateForm');
                $form.attr('action', actionUrl);
                $form.find('#useTemplateFormTemplateId').val(templateId);
                $form.find('#useTemplateNewName').val(docName);
                $form.submit();
        RS.trackEvent("user:create_document:from_template:workspace");
                
                RS.blockPage(RS.msg("legacyjs.core.document.creating"));
			}
		}
	});
	RS.switchToJQueryUIButton();
  });
}

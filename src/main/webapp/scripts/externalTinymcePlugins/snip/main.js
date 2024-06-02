/**
 * snippet creation common for notebook and structuredDocument
 */
var snippetDialog = {

	init: function () {
		var selectedContent = parent.tinymce.activeEditor.selection.getContent();
		$("#snippet_name").val("");
		$("#snippet_name").removeAttr("disabled");
		$('#snippet_content').html(selectedContent);
	},

	saveSnippet: function (ed) {
		var snippetName = $("#snippet_name").val();
		var snippetContent = $("#snippet_content").html();

		if (snippetName === "") {
			apprise("Please provide a name for the snippet");
			return;
		}

		if (snippetContent === "") {
			apprise("No content was selected");
			return;
		}

		var url = "/snippet/create";
		var fieldId = ed.id.split("_")[1];
		var data = {
			snippetName: snippetName,
			content: snippetContent,
			fieldId: fieldId
		};

		var successMsg;

		$.ajaxSetup({ async: false });

		var jxqr = $.post(url, data, function (result) {
			if (result.errorMsg !== null) {
				apprise(getValidationErrorString(result.errorMsg));
			} else {
				successMsg = result.data;
			}
		});
		jxqr.fail(function () {
			tinymceDialogUtils.showErrorAlert("Creating new snippet failed.");
		});

		$.ajaxSetup({ async: true });

		if (successMsg) {
			RS.confirm(successMsg, "success", 3000, null, null);
			ed.windowManager.close();
		}
	},
};

$(document).ready(function (e) {
	snippetDialog.init();

	// listen for create button click
	parent.tinymce.activeEditor.on('snippet-create', function () {
		if(parent && parent.tinymce) {
			snippetDialog.saveSnippet(parent.tinymce.activeEditor);
		}
	});
});
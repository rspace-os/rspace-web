var commentsDialog = {
	/**
	 * Gets comments from the server.
	 */
	getComments: function (fieldId, commentId, revision) {
		var data = {
			commentId: commentId,
			revision: revision
		};

		//Getting previous comments from the server
		var jxqr = $.get("/workspace/editor/structuredDocument/getComments", data,
			function (result) {
				var commentRowTemplate = $('#commentRowTemplate').html();
				$.each(result, function (i, val) {
					var commentRow = Mustache.render(commentRowTemplate,
						{ user: val.lastUpdater, date: val.formatDate, comment: val.itemContent });
					$('#tablecomments').append(commentRow);
				});
			});
		jxqr.fail(function () {
			tinymceDialogUtils.showErrorAlert("Getting comments failed.");
		});
	},

	init: function (ed) {
		// focus input
		$("#commentText").focus();
		
		var fieldId = parent.tinymce.activeEditor.id.split("_")[1];
		$('.fieldIdComment').attr("data-id", fieldId);

		var node = ed.selection.getNode();

		//Checks if <img class="commentIcon" /> already exists.
		if ((node.nodeName = "IMG") && (node.getAttribute("class") == "commentIcon")) {
			// Marks as previous comment on the dialog.htm
			$('#commentText').attr("class", "previousComment");

			// Add commentId to dialog.htm
			var commentId = node.getAttribute("id");
			$('.commentIdComment').attr("data-id", commentId);

			// Function getComments to retrieve previous comments from the server.
			commentsDialog.getComments(fieldId, commentId, null);
		}
	},

	insert: function (ed) {
		var prefix = tinymceDialogUtils.getContextPath();
		//It needs async flag to false to wait until server give a response.
		$.ajaxSetup({ async: false });

		var fieldId = $('.fieldIdComment').attr("data-id");
		var comment = $('#commentText').val();
		var typeComment = $('#commentText').attr("class");
		var closeDialog = false;

		//If is a newComment it should insert a new <img class="commentIcon" /> on the text
		if (typeComment == "newComment") {

			var formdata = {
				fieldId: fieldId,
				comment: comment
			};
			var imageURL = prefix.concat('/images/commentIcon.gif');
			var jxqr = $.post(prefix.concat("/workspace/editor/structuredDocument/insertComment"), formdata,
				function (data) {
					if (data.data != null) {
						var json = {
							id: data.data,
							imageURL: imageURL
						};
						$.get("/fieldTemplates/ajax/commentLink", function (htmlTemplate) {
							var html = Mustache.render(htmlTemplate, json);
							if (html != "") {
								ed.execCommand('mceInsertContent', false, html);
							}
						});
						closeDialog = true;
					} else {
						if (data.errorMsg.errorMessages.length) {
							tinymceDialogUtils.showErrorAlert(data.errorMsg.errorMessages.join(' '));
						}
					}
				});

			jxqr.fail(function () {
				tinymceDialogUtils.showErrorAlert("Inserting comment failed.");
			});

		} else {
			var commentId = $('.commentIdComment').attr("data-id");
			var data = {
				fieldId: fieldId,
				commentId: commentId,
				comment: comment
			};
			var jxqr = $.post(prefix.concat("/workspace/editor/structuredDocument/addComment"), data,
				function (data) {
					if (data.data != null) {
						closeDialog = true;
					} else {
						if (data.errorMsg.errorMessages.length) {
							tinymceDialogUtils.showErrorAlert(data.errorMsg.errorMessages.join(' '));
						}
					}
				});
			jxqr.fail(function () {
				tinymceDialogUtils.showErrorAlert("Adding comment failed.");
			});
		}

		if (closeDialog) {
			$.ajaxSetup({ async: true });

			// Close the front most window (dialog.htm)
			ed.windowManager.close();
		}
	}
};

$(document).ready(function (e) {
	commentsDialog.init(parent.tinymce.activeEditor);

	// listen for insert button click
	parent.tinymce.activeEditor.on('comments-insert', function () {
		if(parent && parent.tinymce) {
			commentsDialog.insert(parent.tinymce.activeEditor);
		}
	});
});

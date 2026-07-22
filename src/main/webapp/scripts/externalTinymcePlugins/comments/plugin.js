/**
 * Comments plugin
 * Author: fran
 * Date: 23/09/2015
 */
tinymce.PluginManager.add('comments', function (editor, url) {
	editor.addCommand('cmdComments', function () {
		editor.windowManager.openUrl({
			title: RS.msg("legacyjs.tinymce.comments.title"),
			url: RS.withCacheVersion(url + '/iframe.html'),
			width: 550,
			height: 460,
			buttons: [
				{ 
					text: RS.msg("legacyjs.common.cancel"),
					type: 'custom',
					name: 'cancel' 
				},
				{
					text: RS.msg("legacyjs.tinymce.comments.addComment"),
					type: 'custom',
					name: 'submit',
					primary: true
				}
			],
			onAction: function(api, button) {
				if(button.name === 'cancel') {
					editor.execCommand("cmdConfirmCancel");
				} else if(button.name == 'submit') {
					editor.fire('comments-insert');
				}
			}
		});
	});

	editor.ui.registry.addButton('comments', {
		icon: "comment",
		tooltip: RS.msg("legacyjs.tinymce.comments.insertComment"),
		onAction: function () { editor.execCommand("cmdComments"); },
		stateSelector: 'img.commentIcon'
	});

	editor.ui.registry.addMenuItem('optComments', {
		text: RS.msg("legacyjs.tinymce.comments.comment"),
		icon: "comment",
		onAction: function () { editor.execCommand("cmdComments"); },
	});

  if(!window.insertActions) window.insertActions = new Map();
  window.insertActions.set("optComments", {
    text: RS.msg("legacyjs.tinymce.comments.comment"),
    icon: 'comment',
    action: () => {
      editor.execCommand('cmdComments');
    },
  });
});

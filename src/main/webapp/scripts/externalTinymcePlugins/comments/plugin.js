/**
 * Comments plugin
 * Author: fran
 * Date: 23/09/2015
 */
tinymce.PluginManager.add('comments', function (editor, url) {
	editor.addCommand('cmdComments', function () {
		editor.windowManager.openUrl({
			title: 'Comments',
			url: url + '/iframe.html',
			width: 550,
			height: 460,
			buttons: [
				{ 
					text: 'Cancel', 
					type: 'custom',
					name: 'cancel' 
				},
				{
					text: 'Add comment',
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
		tooltip: 'Insert comment',
		onAction: function () { editor.execCommand("cmdComments"); },
		stateSelector: 'img.commentIcon'
	});

	editor.ui.registry.addMenuItem('optComments', {
		text: 'Comment',
		icon: "comment",
		onAction: function () { editor.execCommand("cmdComments"); },
	});

  if(!window.insertActions) window.insertActions = new Map();
  window.insertActions.set("optComments", {
    text: 'Comment',
    icon: 'comment',
    action: () => {
      editor.execCommand('cmdComments');
    },
  });
});

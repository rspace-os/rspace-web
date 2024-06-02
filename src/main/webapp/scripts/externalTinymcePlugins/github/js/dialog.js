var githubDialog = {

	init: function (ed) {
		var prefix = tinymceDialogUtils.getContextPath();
		$('#file_tree').fileTree({
			root: '/',
			script: prefix.concat('/github/ajax/get_repository_tree'),
			expandSpeed: 1,
			collapseSpeed: 1,
			multiFolder: false,
			showGallery: false
		}, function (file, type) {
			if ("file" == type) {
				// 0 index: repository ('username/repo_name')
				// 1 index: hash value
				// 2 index: filepath
				var splitFile = file.split("#");
				splitFile[2] = splitFile[2].slice(0, -1);
				$('#link').val(splitFile[0] + splitFile[2]);
				$('#gitHubRepo').val(splitFile[0]);
				$('#gitHubHash').val(splitFile[1]);
				$('#gitHubPath').val(splitFile[2]);
			}
		});
	},

	insert: function (ed) {
		var json = {
			id: 'github-' + $('#gitHubHash').val(),
			fileStore: 'github',
			recordURL: 'https://github.com/' + $('#gitHubRepo').val() + '/blob/master' + $('#gitHubPath').val(),
			name: $('#gitHubPath').val().split("/").splice(-1)[0],
			iconPath: '/images/icons/textBig.png',
			badgeIconPath: '/images/icons/GitHub-Mark-64px.png'
		};
		RS.insertTemplateIntoTinyMCE('insertedExternalDocumentTemplate', json, ed, function () {
			ed.windowManager.close()
		});
	}
};

$(document).ready(function (e) {
	githubDialog.init(parent.tinymce.activeEditor);

	parent.tinymce.activeEditor.on('github-insert', function () {
    if (parent && parent.tinymce) {
      githubDialog.insert(parent.tinymce.activeEditor);
    }		
  });
});

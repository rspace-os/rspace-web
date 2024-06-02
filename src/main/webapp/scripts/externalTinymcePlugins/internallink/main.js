var internalLinkDialog = {

  selectedFile: {},

  init: function () {
    // focus input
		$("#searchQueryInput").focus();
    this.disableInsertButton();
  },

  enableInsertButton: function () {
    window.parent.postMessage({mceAction: 'enable'}, '*');
  },

  disableInsertButton: function () {
    window.parent.postMessage({mceAction: 'disable'}, '*');
  },

  fileSelected: function (globalId, name) {
    this.selectedFile.globalId = globalId;
    this.selectedFile.id = globalId.substring(2);
    this.selectedFile.name = name;
    this.enableInsertButton();
  },

  fileUnselected: function () {
    this.disableInsertButton();
  }
};

$(document).ready(function (e) {
  internalLinkDialog.init();

	// listen for insert button click
	parent.tinymce.activeEditor.on('internallink-insert', function () {
    if(parent && parent.tinymce) {
      var file = internalLinkDialog.selectedFile;

      if (file.globalId.startsWith("GL") || file.globalId.startsWith("GF")) {
        var url = `/workspace/getRecordInformation?recordId=${file.id}`;
        $.get(url, function (result) {
          let data = result.data;
          parent.addFromGallery(data);
          parent.tinymce.activeEditor.windowManager.close();
        }).fail(function (result) {
          console.error("Failed to retrieve data for the gallery record " + file.globalId)
        });
      } else {
        RS.tinymceInsertInternalLink(file.id, file.globalId, file.name, parent.tinymce.activeEditor, function () {
          parent.tinymce.activeEditor.windowManager.close();
        });
        parent.RS.trackEvent('InternalLinkCreated', { source: 'tinymce_plugin', linkedDoc: file.globalId });
      }
    }
  });
});

function fileSelected(globalId, name) {
  internalLinkDialog.fileSelected(globalId, name);
};

function fileUnselected() {
  internalLinkDialog.fileUnselected();
};
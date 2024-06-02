/**
 * Custom shortcuts plugin
 * Author: Kristiyan
 * Date : 01/07/2018
 */

tinymce.PluginManager.add('toolbar', function (editor, url) {

  editor.addCommand('cmdRSCustomToolbar', function () {
    editor.windowManager.openUrl({
      title: 'Configure toolbar',
      url: url + '/plugin.html',
      width: 980,
      height: 590,
      autoScroll: true,
      buttons: [
        {
          text: 'Cancel',
          type: 'custom',
          name: 'cancel',
        },
        {
          text: 'Reset',
          type: 'custom',
          name: 'reset',
        },
        {
          text: 'Reset to default',
          type: 'custom',
          name: 'hard-reset',
        },
        {
          text: 'Save',
          type: 'custom',
          name: 'save',
          primary: true
        }
      ],
      onAction(api, button) {
        if(button.name == 'cancel') {
          editor.execCommand("cmdConfirmCancel");
        } else if(button.name == 'reset') {
          editor.fire('toolbar-reset');
        } else if(button.name == 'hard-reset') {
          editor.fire('toolbar-hard-reset');
        } else if(button.name == 'save') {
          editor.fire('toolbar-save');
        }
      },
      onClose: function () {
        let savedToolbar = localStorage.getItem('custom_toolbar');
        if (savedToolbar) {
          tinymcesetup.toolbar = Object.values(JSON.parse(savedToolbar));
          resetTinyMCE();
        }
      },
    });
  });

  editor.ui.registry.addMenuItem('confToolbar', {
    text: 'Configure Toolbar',
    icon: 'toolbar',
    onAction: function () {
      editor.execCommand("cmdRSCustomToolbar");
    },
  });
});




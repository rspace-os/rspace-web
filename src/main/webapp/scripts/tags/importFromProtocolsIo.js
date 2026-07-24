$(document).ready(function() {
  $('#protocolsIoChooserDlg').dialog({
    modal: true,
    autoOpen: false,
    title: RS.msg("legacyjs.core.protocolsIo.dialogTitle"),
    width: 600,
    height: 750,
    buttons: [
      {
        text: RS.msg("legacyjs.common.cancel"),
        click: function() {
          $(this).dialog('close');
        }
      },
      // Import button works, because onClick handler is added by protocols_io-dialog.js
      {
        text: RS.msg("legacyjs.core.word.importTitle"),
        class: 'protocolsImportButton',
        click: function() {}
      }
    ]
  });
});

function openProtocolsIoChooserDlg() {
  $('#protocolsIoChooserDlgIframe').attr('src', RS.withCacheVersion('/scripts/externalTinymcePlugins/protocols_io/iframe.html'));
  $('#protocolsIoChooserDlg').dialog('open');
}

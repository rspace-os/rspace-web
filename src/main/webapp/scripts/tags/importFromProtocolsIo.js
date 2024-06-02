$(document).ready(function() {
  $('#protocolsIoChooserDlg').dialog({
    modal: true,
    autoOpen: false,
    title: "Import from Protocols.io",
    width: 600,
    height: 750,
    buttons: {
      Cancel: function() {$(this).dialog('close');},
      Import: function() {}  // Import button works, because onClick handler is added by protocols_io-dialog.js
    }
  });

  $('div[aria-describedby="protocolsIoChooserDlg"] button:contains("Import")').addClass('protocolsImportButton');
});

function openProtocolsIoChooserDlg() {
  $('#protocolsIoChooserDlgIframe').attr('src', '/scripts/externalTinymcePlugins/protocols_io/iframe.html');
  $('#protocolsIoChooserDlg').dialog('open');
}


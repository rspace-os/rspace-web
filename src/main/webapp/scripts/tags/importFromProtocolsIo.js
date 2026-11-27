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

// Navigating to the import folder reloads the page and drops any toast, so the
// "/" renamed notice is kept for one Workspace load of that folder in this tab.
var PROTOCOLS_IO_RENAMED_NOTICE_KEY = 'protocolsIoRenamedNotice';

function rememberProtocolsIoRenamedNotice(importFolderId, renamedCount) {
  try {
    sessionStorage.setItem(PROTOCOLS_IO_RENAMED_NOTICE_KEY,
        JSON.stringify({ folderId: String(importFolderId), renamedCount: renamedCount }));
  } catch (e) {
    console.warn('Could not store the Protocols.io renamed notice', e);
  }
}

function showProtocolsIoRenamedNotice(folderId) {
  try {
    var notice = JSON.parse(sessionStorage.getItem(PROTOCOLS_IO_RENAMED_NOTICE_KEY));
    if (notice && notice.folderId === String(folderId)) {
      sessionStorage.removeItem(PROTOCOLS_IO_RENAMED_NOTICE_KEY);
      RS.confirm(RS.msg("legacyjs.tinymce.protocols.namesChanged", notice.renamedCount), 'notice', 8000);
    }
  } catch (e) {
    console.warn('Could not read the Protocols.io renamed notice', e);
  }
}

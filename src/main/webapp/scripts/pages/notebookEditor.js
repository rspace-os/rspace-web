/**
 * Global variables
 */
var selectedNotebookEntryId = 0;
var autosave = null; // holds autosave logic
var selectedFieldId = 0;
var wasAutosaved = false;
var isWorkspacePage = true;

function init(){
  configurePermittedActions();
  initCommentDialog();
  initCommentViewDialog();
  initSignDialog("Signing Entry");
  initWitnessDialog("Witnessing Entry");
  initFormListDlg();

  // Load the journal plugin
  $("#notebook").journal({
    parentRecordId: notebookId,
    appPath: notebookPath,
    notebookName: notebookName,
    entryCount: entryCount,
    initialRecordToDisplay: initialRecordToDisplay
  });
}

function configurePermittedActions() {
  $('#createNotebook').toggle(false);
  $("#createFolder").toggle(false);
}

function editEntryHandler(e) {
  e.preventDefault();
  if(isEditable) {
    var entryId = getCurrentEntryId();
    if (typeof entryId === "string" && (entryId.indexOf('journalEntry') === 0)) {
      entryId = entryId.substring('journalEntry'.length);
    }
    if(entryId === null){
      apprise("There are no entries to edit yet in this notebook - please click on Create > New Entry to start editing.");
      return false;
    }
    // Redirect to the structuredDocument/entryId to modify the entry from there.
    var url = createURL("/workspace/editor/structuredDocument/" + entryId + "?fromNotebook=" + notebookId +"&settingsKey=" + settingsKey);
    window.location.href = url;
  }
}

function updateEntryNameInBreadcrumbs(entryId, entryName) {
  /* FIXME: this is too simplistic, in case of shared entries the parent notebook is not visible at all.
    * also particular entries may be shared into various locations of a Shared folder */

  // reset breadcrumbs to notebook level 
  RS.addBreadcrumbAndRefresh("editorBcrumb", "" + notebookId, notebookName);
  // add breadcrumb for notebook entry
  RS.addBreadcrumbAndRefresh("editorBcrumb", "" + entryId, RS.escapeHtml(entryName));
  setUpEditorBreadcrumbs();
}

function deleteEntry(){
  var callback = function () {
    var entryId = getCurrentEntryId();
    var parentId = notebookId;

    var jxqr = $.post("/notebookEditor/ajax/delete/"+entryId+"/"+parentId+"/",
      function(data){
        $("#notebook").journal("decrementEntryCount");
        $("#notebook").journal("loadEntry",0); // Reload journal
        reloadFileTreeBrowser();
      });
    jxqr.fail(function() {
      RS.ajaxFailed("Delete entry",false,jxqr);
    });
  };

  var event = new CustomEvent('confirm-action', { 'detail': {
    title: "Confirm deletion",
    consequences: `Are you sure you want to delete the following entry? - <b>${$("#recordNameInHeader").html()}</b>. Deleting documents that you <em>own</em> will also delete them from the view of those you're sharing with. Deleting a document <em>shared with you</em> will only delete it from your view.`,
    variant: "warning",
    callback: callback
  }});
  document.dispatchEvent(event);
}

/*
 * Returns id, name and type of the entry
 */
function getDocIdNameAndType() {
  entryId = getCurrentEntryId();
  if (!entryId) {
    return { ids: [], names: [], types: [] };
  }
  return {  ids: [ entryId ], names: [ $("#recordNameInHeader").text() ], types: [ 'NORMAL' ] };
}

function getCurrentEntryId() {
  return $('#notebook').journal("selectedRecordId");
}

function initFormListDlg(){
  /* Init formListDlg */
  $('#formListDlg').dialog({
    modal : true,
    autoOpen: false,
    height: 500,
    title: "Choose a form",
    buttons: {
      Cancel: function (){$(this).dialog('close');},
    },
    open: function () {
      var t = $(this).parent(), w = window;
      t.offset({
          top: (w.outerHeight / 2) - (t.height() / 2),
          left: (w.outerWidth / 2) - (t.width() / 2)
      });
      // make sure at least 1 radio button is checked
      if (!$(this).find("input:checked").val()) {
        $(this).find("input").eq(0).attr('checked', 'checked');
        return false;
      }
    }
  });
}
$(window).on('load',function () {
  configurePermittedActions();
  initCommentDialog();
  initCommentViewDialog();
  initSignDialog("Signing Entry");
  initWitnessDialog("Witnessing Entry");
  initFormListDlg();
});

$(document).ready(function(e) {
  init();
  //defined in notifications.js
  doPoll(); // poll immediately for notifications
  RS.pollRegistry.register(doPoll, 15000); // set 15s interval to poll future notifications

  // Clicking on edit button or double click on page opens edit mode
  $(document).on('click', '#editEntry', editEntryHandler);
  $(document).on("dblclick", "#journalPage", function(e) {
    if (!$(e.target).hasClass('ignoreDblClick')) {
      editEntryHandler(e);
    }
  });

  $(document).on('click', '#createEntry', function (e){
    var form = document.createElement('form');
    form.setAttribute('action', '/workspace/editor/structuredDocument/createEntry/'+notebookId);
    form.setAttribute('method', 'POST');
    document.body.appendChild(form);
    form.submit();
    e.preventDefault();
  });

  $(document).on('click', '#templateMenuLnk', function (e){
    e.preventDefault();
    RS.trackEvent('CreateFromTemplate');
    initFormCreateMenuDialog(notebookId);
    $('#formListDlg').dialog('open');
  });

  // Gets the appropriate URL. Clicking on Other Document and then clicking on the document to create.
  $(document).on('click', '.createDocument', function (e){
    var url = $(this).attr('href');
    var Form$= $(this).closest('form');
    Form$.attr('action', url);
    Form$.submit();
    e.preventDefault();
  });

  window.addEventListener("ReactToolbarMounted", () => {
    if (typeof initWordChooserDlg === 'function') {
      initWordChooserDlg();
      $('#createFromWord').click( function (e){
        e.preventDefault();
        openWordChooserDlg(getDocIdNameAndType, {
          title:"Import from Word/Open Office",
          fileType:"Word or Open Office",
          showImportOptions:true, listNotebooks:true
        });
      });

      $('#createNewForm').click(function(e) {
        e.preventDefault();
        var form = $("<form target='_blank' method='POST' action='/workspace/editor/form/'></form>");
        $(document.body).append(form);
        form.submit();
      });
    }
   
      $('#createFromProtocolsIo').click(function(e) {
        e.preventDefault();
        RS.trackEvent('CreateFromProtocolsIo');
        openProtocolsIoChooserDlg();
      });

    // Clicking on the document (directly) in the menu to create the document.
    $(document).on('click', '.directList', function (e){
      var form$=$("#createPopularSD"); 
      var input$=$(this).find('input');
      form$.append(input$);
      form$.submit();
      e.preventDefault();
    });

    //create from template
    initCreateFromTemplateDlg();
    $(document).on('click', '#createFromTemplate', function (e) {
      e.preventDefault();
      openCreateFromTemplateDlg(notebookId);
    });
  });

  // this is clicking on a create -> other document
  $(document).on('click','.createSDFromFormLink', function (e){
    e.preventDefault();
    $(this).closest('form').submit();
  });

  $(document).on('click', '#deleteEntry', function (e){
    e.preventDefault();
    deleteEntry();
  });

  $(document).on('click', '#signDocument', function (e){
    e.preventDefault();
    $.get('/vfpwd/ajax/checkVerificationPasswordNeeded', function(response) {
      if (response.data) {
        apprise("Please set your verification password in <a href=\"/userform\" target=\"_blank\">My RSpace</a> before signing.");
      } else {
        var entryId = getCurrentEntryId();
        $('#signDocumentDialog').data("recordId", entryId).dialog('open');
      }
    });
  });

  window.addEventListener("ReactToolbarMounted", () => {
    $(document).on('click', '#witnessDocument', function (e){
      e.preventDefault();
      $.get('/vfpwd/ajax/checkVerificationPasswordNeeded', function(response) {
        if (response.data) {
          apprise("Please set your verification password in <a href=\"/userform\" target=\"_blank\">My RSpace</a> before signing.");
        } else {
          var entryId = getCurrentEntryId();
          $('#witnessDocumentDialog').data("recordId", entryId ).dialog('open');
        }
      });
    });
  });

  $(document).on('click','.moreDeleteInfo', function (){
    $('#moreDeleteInfoContent').slideToggle("slow" );
    $('#moreDeleteInfoLnk').slideToggle( "slow" );
  });

  $(document).on('click', '#journalPage .journalPageContent a', function () {
      // if anchor points to uri fragment on the current page, then scroll to it (RSPAC-2105)
      var elemHref = $(this).attr('href');
      if (elemHref && elemHref.startsWith('#')) {
        $("#notebook").journal("scrollJournalPageToAnchor", elemHref.substring(1));
      }
  });
  
  $(window).bind('beforeunload', function() {
    if (wasAutosaved) {
      autoSave();
      return 'Are you sure you want to exit without saving changes?';
    }
  });

  displayStatus(editable);
});

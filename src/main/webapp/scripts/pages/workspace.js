/**
 * Global variables.
 */
var effect = 'fade';
var effectDuration = 200;
var isWorkspacePage = true;
var tableElement = ".mainTable tbody";

var nameFolderModal, nameNotebookModal, formListModal;

// Call this when doing a fresh search
function resetPageNumber() {
  workspaceSettings.pageNumber = 0;
}

function resetToDefaultSettings() {
  resetPageNumber();
}

function showRecordList(result) {
  // change the escaped highlighting hit elements

  var highlightReplace = {
    "&amp;lt;hit&amp;gt;": "<strong>",
    "&amp;lt;/hit&amp;gt;": "</strong>",
    "&lt;/hit&gt;": "</strong>",
    "&lt;hit&gt;": "<strong>"
  };

  for (var e in highlightReplace) {
    result = result.replace(new RegExp(e, 'g'), highlightReplace[e]);
  }

  $('#record_list').empty();
  var noOfRows = $("<div/>").html(result).find("#noOfRows").val();

  if (noOfRows > 0) {
    $('#record_list').html(result);

    // RSPAC-1212 Blocking: Setting the height to fit the height of the
    // previously displayed content and the blocking message
    if ($(tableElement).height() < parseInt(temporaryHeightOfBlockedElement)) {
      $(tableElement).css({height: temporaryHeightOfBlockedElement});
    }

    $(".rs-working-area, .tabularViewBottom").show();
    $('#record_list').show(effect, effectDuration);
    $("#searchModePanel").removeClass().addClass("searchSuccess");
    if (workspaceSettings.searchMode) {
      var message = "Showing results of your search.";
      $("#searchModePanel #message").text(message);
      $("#resetSearch").show();
      $("#searchModePanel").slideDown(fadeTime);
    } else {
      $("#searchModePanel").slideUp(fadeTime);
    }
  } else {
    $('#record_list').hide().html(result);

    // RSPAC-1212 Blocking: Setting the height to fit the height of the previously displayed content and the blocking message
    if ($(tableElement).height() < parseInt(temporaryHeightOfBlockedElement)) {
      $(tableElement).css({height: temporaryHeightOfBlockedElement});
    }

    $(".rs-working-area, .tabularViewBottom").hide();
    $('#record_list').show(effect, effectDuration);
    $("#searchModePanel").removeClass();
    if (workspaceSettings.searchMode) {
      $("#searchModePanel").addClass("searchError");
      $("#searchModePanel #message").text(
          "Your search returned no results. Please search again or");
      $("#resetSearch").show();
      $("#searchModePanel").slideDown(fadeTime);
    } else {
      $("#searchModePanel").addClass("emptyFolder");
      $("#searchModePanel #message").text("This folder is empty.");
      $("#resetSearch").hide();
      $("#searchModePanel").slideDown(fadeTime);
    }
  }
}

function hideRecordList() {
  $('#record_list').hide(effect, effectDuration);
}

function resetCrudops() {
  $('#crudops').hide(fadeTime);
  $('.record_checkbox').each(function () {
    $(this).attr('checked', false);
  });
}

// Takes two empty arrays and populates them with ids/names of selected records
function getSelectedIdsAndNames(ids, names) {
  $("input[class='record_checkbox']:checked").each(function () {
    var recordID = $(this).attr('id').split("_")[1];
    var name = $(this).closest('tr').children().find(
        "a.recordNameCell").text().trim();
    ids.push(recordID);
    names.push(name);
  });
}

// Gets ids, names and record types for selected records
function getSelectedIdsNamesAndTypes() {
  var selection = {ids: [], names: [], types: []};
  getSelectedIdsAndNames(selection.ids, selection.names);
  $.each(selection.ids, function () {
    var tpx = $("#type_" + this).val();
    selection.types.push(tpx);
  });
  return selection;
}

function getSelectedGlobalIds() {
  var selection = [];

  $("input[class='record_checkbox']:checked").each(function () {
    selection.push($(this).data('globalid'));
  });

  return selection;
}

function init() {
  resetCrudops();
  configurePermittedActions();

  /**
   * Updates the createXXX menu links with new record Id.
   * Replaces last element of request path with new record Id.
   */
  $('.createDocument').each(function () {
    var url = $(this).attr("action");
    // replace the last part of URL  with new recordId
    var newurl = url.replace(/[^\/]+$/, workspaceSettings.parentFolderId);
    $(this).attr("action", newurl);
  });
  $("form#wordImportForm").data("parentid", workspaceSettings.parentFolderId);
  document.getElementById("protocolsIoChooserDlgIframe").setAttribute("data-parentid", workspaceSettings.parentFolderId);
  displayOrderIcon();
}

function configurePermittedActions() {
  var canCreateRecord = $('#authzCreateRecord').val() === 'true';
  var canCreateFormRecord = $('#authzCreateFormRecord').val() === 'true';
  var isNotebook = $('#isNotebook').val() === 'true';
  var allowCreateNewEntryInNotebook = $('#allowCreateNewEntryInNotebook').val() === 'true';

  $('#createEntry').toggle(canCreateRecord && isNotebook && allowCreateNewEntryInNotebook);
  $('#createNotebook').toggle(canCreateRecord && !isNotebook);
  $('.directList').toggle(canCreateRecord);
  $("#templateMenuLnk").toggle(canCreateRecord);
  $('#createFromTemplate').toggle(canCreateRecord);

  var canCreateFolder = $('#authzCreateFolder').val() === 'true';
  $("#createFolder").toggle(canCreateFolder);
  $("#create").toggle(canCreateFolder || canCreateRecord);
  $('#createFromWord').toggle(canCreateRecord);
  $('#createFromEvernote').toggle(canCreateRecord);
  $('#createFromProtocolsIo').toggle(canCreateRecord);
  $('#createNewForm').toggle(canCreateRecord && canCreateFormRecord);
  // if permitted to create a record, there will be at least one option in the 2nd and 3rd menu sections therefore show the dividers
  $('.createMenuItemDivider').toggle(canCreateRecord);
}

// Open a specific folder.
function navigateToFolder(folderId) {
  workspaceSettings.url = "/workspace/ajax/view/" + folderId;
  workspaceSettings.pageNumber = 0;
  resetToolbar();
  var jqxhr = $.get(workspaceSettings.url, workspaceSettings,
      function (result) {
        hideRecordList();
        setTimeout(function () {
          showRecordList(result);
          workspaceSettings.grandparentFolderId = workspaceSettings.parentFolderId;
          workspaceSettings.parentFolderId = folderId;
          init();
        }, effectDuration);
      });
  jqxhr.fail(function () {
    RS.ajaxFailed("Opening folder ", false, jqxhr);
  });
}

function setUpWorkspaceBreadcrumbs() {
  var $breadcrumbTag = $('#breadcrumbTag_workspaceBcrumb');
  $breadcrumbTag.find(".breadcrumbLink")
  .attr('href', function () {
    var folderId = $(this).attr('id').split('_')[1];
    return '/workspace/' + folderId;
  }).click(function (e) {
    e.preventDefault();
    var folderId = $(this).attr('id').split('_')[1];
    navigateToFolder(folderId);
  });
  if ($breadcrumbTag.find(".breadcrumbLink").length <= 0) {
    $breadcrumbTag.addClass("empty");
    $breadcrumbTag.parent().hide();
  }
}

function initFormListDlg() {
  if (!RS.useBootstrapModals) {
    $('#formListDlg').dialog({
      modal: true,
      height: 500,
      autoOpen: false,
      title: "Choose a form",
      buttons: {
        Cancel: function () {
          $(this).dialog('close');
        },
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
  } else {
    formListModal = RS.apprise($('#formListDlg').html(), false, undefined,
        undefined, {
          title: "Choose a form",
          textOk: "Cancel",
          size: 'small',
          // TO-DO: Find out why using the Cancel button fails on undefined
          // current modal handle
          open: function (e) {
            // make sure at least 1 radio button is checked
            if (!$(this).find("input:checked").val()) {
              $(this).find("input").eq(0).attr('checked', 'checked');
            }
          }
        });
  }
}

function _displayWorkspaceSettingsAfterSimpleFilterClick() {
  resetPageNumber();
  getAndDisplayWorkspaceResults(workspaceSettings.url, workspaceSettings);
}

function toolbarButtonsEventHandler() {
  $('#viewableItemsView, #folderView').click(function (e) {
    workspaceSettings.viewableItemsFilter = (this.id !== 'folderView');
    _displayWorkspaceSettingsAfterSimpleFilterClick();
  });

  $('#createEntry').click(function (e) {
    e.preventDefault();
    var form = document.createElement('form');
    form.setAttribute('action',
        '/workspace/editor/structuredDocument/createEntry/'
        + workspaceSettings.parentFolderId);
    form.setAttribute('method', 'POST');
    document.body.appendChild(form);
    form.submit();
  });

  $(document).on('click', '#templateMenuLnk', function (e) {
    e.preventDefault();
    RS.trackEvent('CreateFromTemplate');
    initFormCreateMenuDialog(workspaceSettings.parentFolderId);
    $('#formListDlg').dialog('open');
  });

  $('#createFromWord').click(function (e) {
    e.preventDefault();
    RS.trackEvent('CreateFromWord');
    openWordChooserDlg(getSelectedIdsNamesAndTypes, {
      title: "Import from Word/Open Office",
      fileType: "Word or Open Office",
      showImportOptions: true,
      listNotebooks: true
    });
  });

  $('#createFromEvernote').click(function (e) {
    e.preventDefault();
    RS.trackEvent('CreateFromEvernote');
    openWordChooserDlg(getSelectedIdsNamesAndTypes, {
      title: "Import from Evernote",
      fileType: "Evernote XML", showImportOptions: false, listNotebooks: false
    });
  });

  $('#createFromProtocolsIo').click(function (e) {
    e.preventDefault();
    RS.trackEvent('CreateFromProtocolsIo');
    openProtocolsIoChooserDlg();
  });

  $('#createNewForm').click(function (e) {
    e.preventDefault();
    RS.trackEvent('CreateNewForm');
    $("#createNewFormForm").submit();
  });

  $('.createDocument').click(function (e) {
    e.preventDefault();
    var url = $(this).attr('href');
    var $form = $(this).closest('form');
    $form.attr('action', url);
    $form.submit();
  });

  $('body').on('click', '.directList', function (e) {
    e.preventDefault();
    var $form = $("#createPopularSD");
    var $input = $(this).find('input');
    $form.append($input);
    $form.submit();
  });

  $(document).on('click', '#createFromTemplate', function (e) {
    e.preventDefault();
    openCreateFromTemplateDlg(workspaceSettings.parentFolderId);
  });

  $(document).on('click', '.createSDFromFormLink', function (e) {
    e.preventDefault();
    $(this).closest('form').submit();
  });

  $("#list_view").click(function () {
    var id = this.id;
    list_view();
    history.pushState({data: id, title: "List View"}, "List View",
        "?list_view");
  });

  $("#tree_view").click(function () {
    RS.trackEvent('Workspace Tree Viewed');
    var id = this.id;
    tree_view();
    history.pushState({data: id, title: "Tree View"}, "Tree View",
        "?tree_view");
  });

  $('#createRequest').click(function () {
    $('#createRequestDlg').dialog('open');
    RS.trackEvent("user:open:create_request_dialog:workspace");
  });

  $('#createCalendarEntryDlgLink').click(function () {
    $('#createCalendarEntryDlg').dialog('open');
    RS.trackEvent("user:open:create_calendar_entry_dialog:workspace");
  });
}

function orderInfoEventHandler() {
  $(document).on('click', '.orderByTableHeader', function (e) {
    e.preventDefault();
    workspaceSettings.pageNumber = 0;
    var orderByLink = $(this).children('.orderByLink');
    var url = orderByLink.attr('id').split('_')[1];
    if (url.indexOf("orderBy=name") > -1 &&
        workspaceSettings.orderBy !== "name") {
      workspaceSettings.orderBy = "name";
      workspaceSettings.sortOrder = "DESC";
    } else if (url.indexOf("orderBy=modificationDateMillis") > -1 &&
        workspaceSettings.orderBy !== "modificationDateMillis") {
      workspaceSettings.orderBy = "modificationDateMillis";
      workspaceSettings.sortOrder = "DESC";
    } else if (url.indexOf("orderBy=creationDateMillis") > -1 &&
        workspaceSettings.orderBy !== "creationDateMillis") {
      workspaceSettings.orderBy = "creationDateMillis";
      workspaceSettings.sortOrder = "DESC";
    } else {
      if (workspaceSettings.sortOrder === "ASC") {
        workspaceSettings.sortOrder = "DESC";
      } else if (workspaceSettings.sortOrder === "DESC") {
        workspaceSettings.sortOrder = "ASC";
      }
    }
    getAndDisplayWorkspaceResults(workspaceSettings.url, workspaceSettings);
  });

  // Changes the number of records per page
  $(document).on('click', '#applyNumberRecords', function (e) {
    e.preventDefault();
    workspaceSettings.resultsPerPage = $('#numberRecordsId').val();
    resetPageNumber();
    getAndDisplayWorkspaceResults(workspaceSettings.url, workspaceSettings);
  });

  // Enables/disables the button to apply new value of number of records per page
  $(document).on('change', '#numberRecordsId', function (e) {
    e.preventDefault();
    var newValue = $(this).val();
    if (newValue != workspaceSettings.resultsPerPage) {
      $('#applyNumberRecords').show();
      $(this).addClass('submittable');
    } else {
      $('#applyNumberRecords').hide();
      $(this).removeClass('submittable');
    }
  });
}

var paginationEventHandler = function (source, e) {
  workspaceSettings.pageNumber = source.data("pagenumber") - 1;
  getAndDisplayWorkspaceResults(workspaceSettings.url, workspaceSettings);
};

/**
 * Reloads the workspace results by either searching or viewing a folder (depending on `url` being either
 * 'workspace/ajax/view' or 'workspace/ajax/search'). `data` should be the workspace settings, which all get added to the
 * URL so that the server knows what they are. Optional parameter onLoad(result) is a function that gets called when
 * the results load.
 *
 * Gets used when opening a folder, searching, changing sorting options, switching
 * pages, etc.
 **/
function getAndDisplayWorkspaceResults(url, data, onLoad = result => {
}) {
  // block page so that user knows something is loading
  RS.blockPage("Loading records...", false, $('.rs-working-area'));

  var jqxhr = $.get(url, data, function (result) {
    onLoad(result);
    hideRecordList();
    setTimeout(function () {
      showRecordList(result);
      init();
      displayOrderIcon();
      balanceTableColumns();
    }, effectDuration);
  }).fail(function () {
    RS.ajaxFailed("Display Workspace Settings", true, jqxhr);
  }).always(function () {
    RS.unblockPage($('.rs-working-area'));
  });
}

function displayOrderIcon() {
  if (workspaceSettings.sortOrder === "ASC") {
    $('.orderButtonClass').css('background-image', 'url(/images/arrow_up.png)');
  } else if (workspaceSettings.sortOrder === "DESC") {
    $('.orderButtonClass').css('background-image',
        'url(/images/arrow_down.png)');
  }

  $('.orderButtonClass').css("vertical-align", "middle");
  if (workspaceSettings.orderBy === "name") {
    $('#orderName').show();
  } else if (workspaceSettings.orderBy === "modificationDateMillis") {
    $('#orderDate').show();
  } else if (workspaceSettings.orderBy === "creationDateMillis") {
    $('#orderCreationDate').show();
  }
}

function saveWorkplaceSettings() {
  var jqxhr = $.post(
      "/workspace/ajax/saveWorkspaceSettings/?settingsKey=" + settingsKey,
      workspaceSettings,
      function (_) {
        console.log("Saved settings successfully")
      }
  );
  jqxhr.fail(function () {
    RS.ajaxFailed("Failed to save workspace settings", false, jqxhr);
  });
}

function initFileTree() {
  setUpFileTreeBrowser({
    fullSizeTree: true
  });
}

function list_view(id) {
  $('#fileBrowsing').hide(effect, effectDuration);
  setTimeout(function () {
    $('#record_list_frame').show(effect, effectDuration);
    $('#viewMenu .dropdown-toggle i').attr('class', 'icon-th-list');
  }, effectDuration);
  workspaceSettings.currentViewMode = "LIST_VIEW";
  saveWorkplaceSettings();
}

function tree_view() {
  resetCrudops();
  $('#record_list_frame').hide(effect, effectDuration);
  if ($('#fancyTree').html() === "") {
    initFileTree();
  }
  setTimeout(function () {
    $('#fileBrowsing').show(effect, effectDuration);
    $('#viewMenu .dropdown-toggle i').attr('class', 'icon-flow-split');
  }, effectDuration);
  workspaceSettings.currentViewMode = "TREE_VIEW";
  saveWorkplaceSettings();
}

function initWorkspaceRequestDlg() {
  initialiseRequestDlg({
    targetFinderPolicy: 'ALL',
    availableMessageTypes: 'SIMPLE_MESSAGE,GLOBAL_MESSAGE,REQUEST_EXTERNAL_SHARE'
  });
}

function showChemicalSearchIfIntegrationEnabled() {
  var jqxhr = $.get('/integration/integrationInfo', {name: 'CHEMISTRY'});
  jqxhr.done(function (response) {
    var integration = response.data;
    if (integration && integration.available && integration.enabled) {
      $('#chemicalSearchListItem').show();
    }
  });
}

function updateUIForWorkspaceSettingsFromServer() {
  displayOrderIcon();
  if (workspaceSettings.currentViewMode == "TREE_VIEW") {
    tree_view();
  } else {
    list_view();
  }
}

$(document).ready(function () {
  workspaceSettings.parentFolderId = recordId;
  workspaceSettings.fallbackFolderId = recordId;
  workspaceSettings.url = "/workspace/ajax/view/"
      + workspaceSettings.parentFolderId;
  if (typeof isWorkspaceSearch !== 'undefined' && isWorkspaceSearch === true) {
    workspaceSettings.url = "/workspace/ajax/search/";
  }

  init();
  initFormListDlg();
  initWordChooserDlg();
  initCreateCalendarEntryDlg();
  initAttachFileToCalendarEntryDlg();
  initCreateFromTemplateDlg();

  doPoll(); //poll immediately for notifications
  RS.pollRegistry.register(doPoll, 20000); // set up 20s poll interval for new notifications

  setUpCrudOps();
  initWorkspaceRequestDlg();
  window.addEventListener("ReactToolbarMounted", () => {
    toolbarButtonsEventHandler();
  });
  showChemicalSearchIfIntegrationEnabled();
  orderInfoEventHandler();

  $(document).on('click', '.folder', function (e) {
    e.preventDefault();
    var folderId = $(this).data("folderid");
    navigateToFolder(folderId);
  });

  updateUIForWorkspaceSettingsFromServer();

  $(document).on('click', '.workspaceRecordInfo', function () {
    var id = $(this).parents('tr').data('recordid');
    openRecordInfoDialog(id);
  });

  RS.setupPagination(paginationEventHandler);
  RS.addOrderByTooltips();
  balanceTableColumns();
});

var balanceTableColumns = function () {
  var dateCell = $("#file_table td:nth-child(4)").first();
  var longestOwnerName = getLongestCellContent(
      $("#file_table td:nth-child(7)"));
  var longestOwnerNameText = $("#file_table td:nth-child(7)").eq(
      longestOwnerName.index).text().trim();
  var ownerCell = $("#file_table td:nth-child(7)").first();
  var ownerMinWidth = $('<span>').hide().appendTo(document.body)
      .text(longestOwnerNameText)
      .css(RS.getFontProperties(dateCell))
      .outerWidth() +
      parseInt(ownerCell.css("padding-left")) + parseInt(
          ownerCell.css("padding-right")) +
      parseInt(ownerCell.css("border-left-width")) + parseInt(
          ownerCell.css("border-right-width")) + 2;

  if (ownerMinWidth < 200) {
    $("#file_table td:nth-child(7)").eq(longestOwnerName.index).css(
        {"white-space": "nowrap"});
  }
};

var getLongestCellContent = function (cells) {
  var max = 0;
  var maxPosition;
  cells.each(function (i) {
    if ($(this).text().trim().length > max) {
      max = $(this).text().trim().length;
      maxPosition = i;
    }
  });
  return {maxLength: max, index: maxPosition};
}

var resetToolbar;

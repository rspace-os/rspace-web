/* jshint maxerr: 100 */

/**
 * Provides JS for the crudops.tag file which contains the 'options' menu -
 * delete, rename, move,share etc
 *
 * Depends on workspace.js.
 */

/*
 * Default workspace refresh used when this page is inside Workspace page.
 */
var defaultRefresh = function (result) {
  setTimeout(function () {
    $('#record_list').hide(effect, effectDuration);
    $('#record_list').empty();
    var noOfRows = $("<div/>").html(result).find("#noOfRows").val();
    if (noOfRows > 0) {
      $('#record_list').html(result);
      $(".rs-working-area, .tabularViewBottom").show();
      $('#record_list').show(effect, effectDuration);
      $("#searchModePanel").removeClass("searchError").addClass("searchSuccess");
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
      $(".rs-working-area, .tabularViewBottom").hide();
      $('#record_list').show(effect, effectDuration);
      $("#searchModePanel").removeClass("searchSuccess").addClass("searchError");
      if (workspaceSettings.searchMode) {
        $("#searchModePanel #message").text("Your search returned no results. Please search again or");
        $("#resetSearch").show();
        $("#searchModePanel").slideDown(fadeTime);
      } else {
        $("#searchModePanel #message").text("No records have been found.");
        $("#resetSearch").hide();
        $("#searchModePanel").slideDown(fadeTime);
      }
    }
    RS.unblockPage($(".mainTable tbody"));
    reloadFileTreeBrowser();
    init();
  }, effectDuration);
};

/**
 * Takes an optional set of handlers as ondelete - a function that takes the
 * HTML response as a single argument oncopy - a function that takes the HTML
 * response as a single argument onmove - a function that takes the HTML
 * response as a single argument moveparams - additional arguments to be added
 * into the Ajax request for move.
 */
function setUpCrudOps(args) {
  args = args || {};
  var onDelete = args.ondelete || function (result) {
    defaultRefresh(result);
  };
  var oncopy = args.oncopy || function (result) {
    defaultRefresh(result);
  };
  var onmove = args.onmove || function (result) {
    defaultRefresh(result);
  };

  createMoveDialog(onmove, args.moveparams);
  createRenameDialog();
  createTagDialog();
  createCompareDialog();

  registerCopyRecordsHandler(oncopy);
  registerDeleteRecordsHandler(onDelete);

  registerFavoritesRecordsHandler();
  registerViewRevisionsHandler();
  registerOfflineWorkHandler();
  registerExternalMessagesHandler();

  setUpExportDialogs();
  setUpShareDialog();
  setUpUseTemplateDialog();

  setUpSelectAll();
  setUpCheckBoxClickHandlers();

  RS.emulateKeyboardClick('.crudopsAction');
}

function setUpUseTemplateDialog() {
  initUseTemplateDlg(function () {
    var selectedRecordIds = [];
    var selectedRecordNames = [];
    getSelectedIdsAndNames(selectedRecordIds, selectedRecordNames);
    return {
      id: selectedRecordIds[0],
      name: selectedRecordNames[0]
    };
  });
  $('#createDocFromTemplate').click(function (e) {
    e.preventDefault();
    $('#useTemplateDlg').dialog('open');
  });
}

function setUpShareDialog() {
  var idsToShareGetter = function () {
    var recordIdsToShare = [];
    getSelectedIdsAndNames(recordIdsToShare, []);
    return recordIdsToShare;
  };

  //update icons & img title
  var onshare = function (ids) {
    $.each(ids, function (index, item) {
      var img$ = $("tr[data-recordId='" + item + "'] img.sharedStatusImg");
      if (img$.attr("alt") !== 'Shared') {
        img$.attr("src", "/images/documentStatusShared.png").attr(
            "alt", "Shared").attr("title", "Shared");
      }
    });
  };

  createShareDialog("Publish", idsToShareGetter, onshare, '#publish-dialog');
  createShareDialog("Share", idsToShareGetter, onshare );
}

function setUpExportDialogs() {
  var getExportSelectionForExportDlg = function () {
    return getExportSelectionFromSelectedDocuments(getSelectedIdsNamesAndTypes());
  }
  $('.exportIcon.newExport').click(function (e) {
    e.preventDefault();
    RS.exportModal.openWithExportSelection(getExportSelectionForExportDlg());
  });
  $('.exportIcon.oldExport').click(function (e) {
    e.preventDefault();
    RS.getExportSelectionForExportDlg = getExportSelectionForExportDlg;
    openExportArchiveDlg();
  });
}

function registerViewRevisionsHandler() {
  $('body').on('click', '#viewRevisions', function (e) {
    e.preventDefault();
    var idsToRename = [];
    var namesToRename = [];
    getSelectedIdsAndNames(idsToRename, namesToRename);
    var url = createURL("/workspace/revisionHistory/list/" + idsToRename[0] + "?settingsKey=" + settingsKey);
    window.location.href = url;
  });
}

function registerOfflineWorkHandler() {
  $('body').on('click', '#startOfflineWork,#endOfflineWork', function (e) {
    e.preventDefault();
    var selectedIds = [];
    getSelectedIdsAndNames(selectedIds, []);

    var url;
    if (e.target.id == "startOfflineWork") {
      RS.blockPage("Marking for offline work...");
      url = createURL("/offlineWork/selectForOffline");
    } else {
      RS.blockPage("Ending offline work...");
      url = createURL("/offlineWork/removeFromOffline");
    }
    var jqxhr = $.post(url, { recordIds: selectedIds },
        function (result) {
          RS.unblockPage();
          $.get(createURL('/workspace/ajax/view/' + workspaceSettings.parentFolderId),
              function (result) {
                defaultRefresh(result);
              }
          );
        }
    );
    jqxhr.fail(function () {
      RS.ajaxFailed("Offline marking", true, jqxhr);
    });
  });
}

function registerExternalMessagesHandler() {
  if (typeof initialiseExtMessageChannelListButtonAndDialog === "function") {
    initialiseExtMessageChannelListButtonAndDialog(function () {
      var ids = [];
      getSelectedIdsAndNames(ids, []);
      return ids;
    }, ".sendToIcon");
  }
}

function isSystemFolderChecked() {
  var systemFolders = ["Shared", "Pdf Files", "Templates"];
  var selectedRecords = getSelectedIdsNamesAndTypes();
  var isSystemFolder = false;
  $.each(selectedRecords.types, function (indx, type) {
    if (type.indexOf('FOLDER') >= 0 && type.indexOf('SYSTEM') >= 0 &&
        $.inArray(selectedRecords.names[indx], systemFolders) != -1) {
      isSystemFolder = true;
      return false; // this return just breaks the each loop
    }
  });
  return isSystemFolder;
}

/**
 * Displays options based on checkbox selection
 */
function calculateOptionDisplay(chbox$) {

  var chboxTRs$ = chbox$.closest('tr');
  var chboxTDs$ = chbox$.closest('td');
  var chboxSize = chbox$.size();

  var isAtLeastOneFolder = chboxTRs$.find('a.structuredDocument').size() < chboxSize;
  var cantShare = chboxTDs$.find("input[name='isShareable'][value='false']").size() > 0;
  var cantTag = chboxTDs$.find("input[name='isTaggable'][value='false']").size() > 0;
  var cantPublish = chboxTDs$.find("input[name='isPublishable'][value='false']").size() > 0;
  var cantMove = chboxTDs$.find("input[name='isMoveable'][value='false']").size() > 0;
  var cantCopy = chboxTDs$.find("input[name='isCopyable'][value='false']").size() > 0;
  var cantDelete = chboxTDs$.find("input[name='isDeletable'][value='false']").size() > 0;
  var cantRename = chboxTDs$.find("input[name='isRenamable'][value='false']").size() > 0;
  var cantExport = chboxTDs$.find("input[name='isExportable'][value='false']").size() > 0;
  const isDocument = chboxTDs$.find("input[name='recordType'][value='NORMAL']").size() === chbox$.size();

  $('#viewRevisions').toggle(!isAtLeastOneFolder);
  $('#shareRecord').toggle(!cantShare);
  $('#publishRecord').toggle(!cantPublish);
  $('#moveRecords').toggle(!cantMove);
  $('#copyRecords').toggle(!cantCopy);
  $('#deleteRecords').toggle(!cantDelete);
  $('#renameRecords').toggle(!cantRename);
  $('.crudopsAction.exportIcon').toggle(!cantExport);
  $('#tagRecords').toggle(!cantTag);
  $('#compareRecords').toggle(isDocument);

  var isSingleTemplate = chboxSize === 1 && chboxTRs$.find("input[name='recordType']").val() === 'NORMAL:TEMPLATE';
  $('#createDocFromTemplate').toggle(isSingleTemplate);

  var countNoFavorites = chboxTDs$.find("input[name='favoriteStatus'][value='NO_FAVORITE']").size();
  var countFavorites = chboxTDs$.find("input[name='favoriteStatus'][value='FAVORITE']").size();
  $('#addToFavorites').toggle(countNoFavorites > 0 && countFavorites === 0);
  $('#removeFromFavorites').toggle(countFavorites > 0 && countNoFavorites === 0);

  var cantOffline = chboxTDs$.find("input[name='offlineStatus'][value='NOT_APPLICABLE']").size() > 0;
  var startOfflineWork = chboxTDs$.find("input[name='offlineStatus'][value='NOT_OFFLINE']").size() > 0 ||
      chboxTDs$.find("input[name='offlineStatus'][value='OTHER_EDIT']").size() > 0;
  $('#startOfflineWork').toggle(!cantOffline && startOfflineWork);
  $('#endOfflineWork').toggle(!cantOffline && !startOfflineWork);
}

/*
 * Displays 'revisions' link Argument is a jQuery wrapped clicked checkbox.
 */
function showRevisionsLinkIfSelectedChBoxIsForStructuredDoc(chbox$) { }

/* Probably not used anywhere */
function selectPdfFolder(folderId) {
  $.post(createURL('/workspace/ajax/view/') + folderId + "/0",
      function (result) {
        $('#record_list').hide(effect, effectDuration);
        setTimeout(function () {
          $('#record_list').empty();
          $('#record_list').html(result);
          $('#record_list').show(effect, effectDuration);
          // TODO Check if we should update workspaceSettings.parentFolderId instead !
          recordId = folderId;
          init();
        }, effectDuration);
      }
  );
}

function depositDataShare(fname) {
  var jqxhr = $.post(createURL("/workspace/ajax/depositArchive"), { deposit: fname },
      function (result) {
        var str = new String(result);
        str = "Deposit successfully at " + str;
        RS.confirm(str, "success", 3000);
      });

  jqxhr.fail(function () {
    RS.ajaxFailed("Archiving deposit", true, jqxhr);
  });
}

var depositName;
function checkLoad() {
  if (window.onLoad) {
    depositDataShare(depositName);
  } else {
    setTimeout(checkLoad(), 1000);
  }
}

function createMoveDialog(onmove, moveparams) {
  var targetFolder = "";
  $('#move-dialog').dialog({
    modal: true,
    autoOpen: false,
    title: "Select target folder",
    open: function () {
      var moveTargetRoot = $("#movetargetRoot").val()
      var scriptUrl = '/fileTree/ajax/directoriesInModel';
      if (moveTargetRoot === "/") {
        var types = $(this).data('toMoveTypes');
        if (onlyNormalDocsOnTypesList(types)) {
          scriptUrl += '?showNotebooks=true';
        }
      }
      $("#folder-move-path").html("");
      $('#movefolder-tree').fileTree({
            // custom argument! This is so we show the root folder of the tree
            // when it is not the actual root folder, e.g, a group shared folder
            initialLoad: true,
            root: moveTargetRoot,
            script: createURL(scriptUrl),
            expandSpeed: 500,
            collapseSpeed: 500,
            multiFolder: false
          },
          function (file, type) {
            if ("directory" == type) {
              var currDir$ = $('#movefolder-tree').find("a[rel='" + file + "']");
              // get parents, then reverse order to construct the path
              var parents$ = $(currDir$.parents("li").get().reverse());
              var path = "/";
              parents$.each(function () {
                path = path + $(this).children("a").text() + "/";
              });
              var safePath = RS.escapeHtml(path);
              $("#folder-move-path").html(safePath);
              $('#selectedMoveTargetId').val(file);
              targetFolder = file;
            }
          });
    },
    buttons: {
      Cancel: function () {
        $(this).dialog('close');
      },
      Move: function () {
        $(this).dialog('close');
        var data = $.extend({}, workspaceSettings,
            {
              settingsKey: settingsKey,
              toMove: $(this).data('toMoveIds'),
              target: $("#selectedMoveTargetId").val(),
              // source folder from which to move
            });

        if (moveparams) {
          for (var prop in moveparams) {
            data[prop] = moveparams[prop];
          }
        }
        var jqxhr = $.post(createURL('/workspace/ajax/move'), data,
            function (result) {
              onmove(result);
            });
        jqxhr.fail(function () {
          RS.ajaxFailed("Move", true, jqxhr);
        });
      }
    }
  });

  $('body').on('click', '#moveRecords', function (e) {
    e.preventDefault();

    var selected = getSelectedIdsNamesAndTypes()
    $('#move-dialog').data('toMoveIds', selected.ids)
    .data('toMoveTypes', selected.types)
    .dialog('open');
  });
}

function createRenameDialog() {
  $('#renameRecord').dialog({
    modal: true,
    autoOpen: false,
    title: "Rename",
    open: function (event, ui) {
      var name = $(this).data('recordNameWithoutExt');
      $('#nameField').val(name);
    },
    buttons: {
      Cancel: function () {
        $(this).dialog('close');
      },
      Rename: function () {
        $(this).dialog('close');
        // recordId is defined local, it is not using workspaceSettings.
        var recordId = $(this).data('selectedId');
        var extension = $(this).data('recordExt');
        var newName = $('#nameField').val() + extension;

        var data = {
          settingsKey: settingsKey,
          recordId: recordId,
          newName: newName
        };
        var jqxhr$ = $.post(createURL("/workspace/editor/structuredDocument/ajax/rename"),
            data,
            function (response) {
              if (response.errorMsg !== null) {
                apprise(getValidationErrorString(response.errorMsg));
              } else {
                // update document title
                var idStr = "_" + recordId;
                var match = "a[id$=" + idStr + "]";
                $(match).text(newName);
                reloadFileTreeBrowser();
              }
            });
        jqxhr$.fail(function () {
          RS.ajaxFailed("Renaming", false, jqxhr$);
        });
      }
    }
  });

  RS.onEnterSubmitJQueryUIDialog('#renameRecord');

  $('body').on('click', '#renameRecords', function (e) {
    e.preventDefault();

    var selectedChkBxes = $("input[class='record_checkbox']:checked");
    var $selected = $(selectedChkBxes[0]);
    var selectedId = $selected.attr('id').split("_")[1];
    var recordName = $selected.closest('tr').children().find("a.recordNameCell").text().trim();
    var recordExt = "";

    var type = $selected.siblings('[name="recordType"]').val();
    var isMediaFile = type === 'MEDIA_FILE';
    if (isMediaFile && recordName.indexOf('.') >= 0) {
      recordExt = recordName.substring(recordName.lastIndexOf('.'));
    }
    var recordNameWithoutExt = recordName.substring(0, recordName.length - recordExt.length);

    // this parameterizes the dialog
    $("#renameRecord")
    .data('selectedId', selectedId)
    .data("recordNameWithoutExt", recordNameWithoutExt)
    .data("recordExt", recordExt)
    .dialog('open');
  });
}

function createTagDialog() {
  $('body').on('click', '#tagRecords', function (e) {
    e.preventDefault();

    var selected = getSelectedIdsNamesAndTypes();
    window.dispatchEvent(new CustomEvent("OPEN_TAG_DIALOG", { detail: { ids: selected.ids }}));
  });
}

function createCompareDialog() {
  $('body').on('click', '#compareRecords', function (e) {
    e.preventDefault();

    var selected = getSelectedIdsNamesAndTypes();
    window.dispatchEvent(new CustomEvent("OPEN_COMPARE_DIALOG", { detail: { ids: selected.ids }}));
  });
}

function setUpSelectAll() {
  $(document).on("click", "#selectAllToggle", function (e) {
    var isAllSelected = $("#selectAllToggle").prop("checked");
    var $recordsCheckBoxes = $("input[class='record_checkbox']");
    $recordsCheckBoxes.prop("checked", isAllSelected);
    updateCrudopsMenu();
  });
}

function setUpCheckBoxClickHandlers() {
  $(document).on("click", ".record_checkbox", function (e, extra) {
    //update crudops menu here only if it was a single click
    //It allows to prevent redundant updating when the selecet all or select none
    //is clicked (in that case only one update in the end is needed).
    updateCrudopsMenu();
  });
}

function updateCrudopsMenu() {
  var $selectedCheckBoxes = $("input[class='record_checkbox']:checked");
  var $allCheckBoxes = $("input[class='record_checkbox']");
  var numSelectedCheckBoxes = $selectedCheckBoxes.size();
  var numAllCheckBoxes = $allCheckBoxes.size();

  var showSelectAllChecked = numSelectedCheckBoxes === numAllCheckBoxes;
  var showCrudops = numSelectedCheckBoxes > 0;
  var hideExtra = isSystemFolderChecked();
  var hideOneItemOptions = numSelectedCheckBoxes !== 1;

  if (!showCrudops) {
    $('#crudops').hide(fadeTime);
  }

  $("#selectAllToggle").prop("checked", showSelectAllChecked);

  calculateOptionDisplay($selectedCheckBoxes);

  if (hideExtra) {
    $('#renameRecords, #shareRecord, #deleteRecords, #moveRecords, #copyRecords').hide();
  }
  if (hideOneItemOptions) {
    $('#renameRecords, #viewRevisions').hide();
  }

  if (showCrudops) {
    $('#crudops').show(fadeTime);
  }
}

function registerCopyRecordsHandler(oncopy) {
  $('body').on('click', '#copyRecords', function (e) {
    e.preventDefault();
    var idsToCopy = [];
    var namesToCopy = [];
    getSelectedIdsAndNames(idsToCopy, namesToCopy);
    namesToCopy = $.map(namesToCopy, function (name) {
      return name + "_Copy";
    });
    var data = $.extend({}, workspaceSettings, {
      settingsKey: settingsKey,
      newName: namesToCopy,
      idToCopy: idsToCopy
    });
    RS.blockPage("Copying...");
    var jqxhr = $.post(createURL("/workspace/ajax/copy"), data, function (result) {
      RS.unblockPage();
      oncopy(result);
    });

    jqxhr.fail(function () {
      RS.ajaxFailed("Copy", true, jqxhr);
    });
  });
}

function registerDeleteRecordsHandler(onDelete) {
  $('body').on('click', '.moreDeleteInfo', function () {
    $('#moreDeleteInfoContent').toggle('fast');
    $('#moreDeleteInfoLnk').toggle('fast');
  });

  $('body').on('click', '#deleteRecords', function (e) {
    e.preventDefault();
    var idsToDelete = [];
    var namesToDelete = [];
    var callback = function () {
      RS.blockingProgressBar.show({
        progressType: "rs-deleteRecord",
        msg: "Deleting..."
      });
      var data = $.extend({}, workspaceSettings, {
        settingsKey: settingsKey,
        toDelete: idsToDelete
      });
      var jqxhr = $.post(
          createURL("/workspace/ajax/delete"), data,
          function (result) {
            RS.blockingProgressBar.hide();
            onDelete(result);
          });
      jqxhr.fail(function () {
        RS.blockingProgressBar.hide();
        RS.ajaxFailed("Delete", true, jqxhr);
      });
    }
    getSelectedIdsAndNames(idsToDelete, namesToDelete);
    var event = new CustomEvent('confirm-action', { 'detail': {
        title: "Confirm deletion",
        consequences: `Do you want to delete the following document(s)? - ${RS.escapeHtml(namesToDelete.join(", "))} - Deleting documents that you <em>own</em> will also delete them from the view of those you're sharing with. Deleting a document <em>shared with you</em> will only delete it from your view.`,
        variant: "warning",
        callback: callback
      }});
    document.dispatchEvent(event);
    RS.focusAppriseDialog(false);
  });
}

function registerFavoritesRecordsHandler() {
  $('body').on('click', '#addToFavorites', function (e) {
    e.preventDefault();

    let recordsIds = [];
    let namesToStar = [];
    getSelectedIdsAndNames(recordsIds, namesToStar);

    const url = "/workspace/ajax/favorites/add";
    const data = {
      recordsIds: recordsIds,
    };

    const jqxhr = $.post(url, data, function (result) {
      const favIds = result.data;

      if (favIds) {
        const favImg = ' <img class="favoriteImg" src="/images/favorite.svg" width="20" height="20" alt="Favorite" title="Favorite">';

        $.each(favIds, function (index, id) {
          const row$ = $("tr[data-recordId='" + id + "']");

          if (row$.find(".favoriteImg").size() === 0) {
            const recordNameBlock$ = row$.find("td:eq(2)");
            const templateIcon$ = recordNameBlock$.find("span.templateSpan");

            if (templateIcon$.size() === 1) {
              templateIcon$.after(favImg);
            } else {
              recordNameBlock$.find("a.recordNameCell").after(favImg);
            }

            row$.find("input[name='favoriteStatus']").val("FAVORITE");
          }
        });

        updateCrudopsMenu();

        if (favIds.length === 1) {
          RS.confirm("Document marked as favorite", "success", 3000);
        } else {
          RS.confirm(favIds.length + " documents marked as favourite", "success", 3000);
        }
      } else {
        apprise("Failed to favorite: " + getValidationErrorString(result.errorMsg));
      }
    }).fail(function () {
      RS.ajaxFailed("Adding to favorites", true, jqxhr);
    });
  });

  $('body').on('click', '#removeFromFavorites', function (e) {
    e.preventDefault();
    var recordsIds = [];
    var namesToStar = [];
    getSelectedIdsAndNames(recordsIds, namesToStar);
    var url = "/workspace/ajax/favorites/remove";
    var data = {
      recordsIds: recordsIds,
    };

    var jqxhr = $.post(url, data, function (result) {
      var favIds = result.data;
      if (favIds) {
        $.each(favIds, function (index, item) {
          var row$ = $("tr[data-recordId='" + item + "']");
          row$.find(".favoriteImg").remove();
          row$.find("input[name='favoriteStatus']").val("NO_FAVORITE");
        });
        updateCrudopsMenu();
        if (favIds.length == 1) {
          RS.confirm("Item removed from favorites", "success", 3000);
        } else {
          RS.confirm(favIds.length + " documents removed from favorites", "success", 3000);
        }
      } else {
        apprise("Favorites did not complete: " + getValidationErrorString(result.errorMsg));
      }
    }).fail(function () {
      RS.ajaxFailed("Deleting from favorites", true, jqxhr);
    });
  });
}

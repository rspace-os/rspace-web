
$(document).ready(function () {
  $('.c_action').hide();
  $('.actionCbox').prop('checked', false);
  $('body').on('click', '.actionCbox', function (e) {
    var selectedChkBxes$ = $("input[class='actionCbox']:checked");
    if (selectedChkBxes$.size() == 1) {
      $('#deleteGroup').show();
      $('#exportGroupRecord').show();
    } else {
      $('.c_action').hide();
    }
  });
  $('body').on('click', '#deleteGroup', function (e) {
    var selected = _getSelectedGroupIds();
    var trToRemove$ = $("input[class='actionCbox']:checked").closest('tr');
    var callback = function () {
      var jxqr = $.post(createURL('/groups/admin/removeGroup/' + selected[0].id), function (xhr) {
        RS.confirm(RS.msg("legacyjs.system.groupList.groupRemoved"), 'success', 5000);
        trToRemove$.remove();
      });
      jxqr.fail(function () {
        RS.ajaxFailed(RS.msg("legacyjs.system.groupList.removingGroupAction"), true, jqxhr);
      });
    }
    RS.createConfirmationDialog({
      title: RS.msg("legacyjs.system.common.confirmDeletionTitle"),
      consequences: RS.msg(
        "legacyjs.system.groupList.confirmDeletionConsequences",
        RS.escapeHtml(selected[0].displayName)
      ),
			variant: "warning",
      callback: callback
    });
  });

  var paginationEventHandler = function (source, e) {
    var url = source.attr('id').split("_")[1];
    $.get(createURL(url),
      function (data) {
        $('#groupListContainer').html(data);
      }
    );
  };
  RS.setupPagination(paginationEventHandler);

  $('body').on('click', '.orderBy', function (e) {
    RS.toggle({ link: $(this), event: e, resultsContainer: '#groupListContainer' });
  });

  $("body").click(function () {
    $(".visiblePanel").fadeOut(200, function () {
      $(this).removeClass("visible");
    });
  });

  $('#exportGroupRecord').on('click', function (e) {
    RS.getExportSelectionForExportDlg = function () {
      var selected = _getSelectedGroupIds();
      return getExportSelectionFromGroupId(selected[0].id, selected[0].displayName);
    }
    RS.exportModal.openWithExportSelection(RS.getExportSelectionForExportDlg());
    return false;
  });

  $('#storageAlertForm').on('submit', function (e) {
    e.preventDefault();
    var focused = document.activeElement;
    $('#storageAlertSave').prop('disabled', true);
    RS.blockPage(RS.msg("legacyjs.system.common.saving"));
    var jqxhr = $.post(createURL('/system/groups/ajax/storageAlertThreshold'), {
      thresholdMb: $('#storageAlertThresholdMb').val()
    });
    jqxhr.done(function (result) {
      if (result.errorMsg) {
        apprise(getValidationErrorString(result.errorMsg));
      } else if (result.data) {
        RS.confirm(RS.msg("legacyjs.system.groupList.storageAlertSaved", result.data), 'success', 5000);
      } else {
        RS.confirm(RS.msg("legacyjs.system.groupList.storageAlertRemoved"), 'success', 5000);
      }
    });
    jqxhr.fail(function () {
      RS.ajaxFailed(RS.msg("legacyjs.system.groupList.savingStorageAlertAction"), true, jqxhr);
    });
    jqxhr.always(function () {
      RS.unblockPage();
      $('#storageAlertSave').prop('disabled', false);
      // Chromium drops focus to <body> when the focused button is disabled
      if (document.activeElement === document.body) {
        $(focused).focus();
      }
    });
  });
});

function _getSelectedGroupIds() {
  var selectedIds = [];
  $("input[class='actionCbox']:checked").each(function () {
    var id = $(this).attr('data-id');
    selectedIds.push({
      "id": id,
      "displayName": $(this).parent().next().text()
    });
  });
  return selectedIds;
}

function doSearch() {
  var term = $('#searchGroupListInput').val();
  var url = "/system/groups/ajax/list";
  var jxqr = $.get(url, { "displayName": term }, function (data) {
    $('#groupListContainer').html(data);
  });

  jxqr.fail(function () {
    RS.ajaxFailed(RS.msg("legacyjs.system.groupList.searchingGroupsAction"), false, jxqr);
  });
}
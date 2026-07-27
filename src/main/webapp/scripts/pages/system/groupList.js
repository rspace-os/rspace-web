
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
      consequences: RS.msg("legacyjs.system.groupList.confirmDeletionConsequences", selected[0].displayName),
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
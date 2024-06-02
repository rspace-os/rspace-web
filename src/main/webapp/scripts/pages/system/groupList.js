
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
        RS.confirm("Group removed", 'success', 5000);
        trToRemove$.remove();
      });
      jxqr.fail(function () {
        RS.ajaxFailed("Removing group", true, jqxhr);
      });
    }

		var event = new CustomEvent('confirm-action', { 'detail': {
      title: "Confirm deletion",
      consequences: `Are you sure you want to delete the following group: <b>${selected[0].displayName}</b>?`,
			variant: "warning",
      callback: callback
    }});
    document.dispatchEvent(event);
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
    RS.ajaxFailed("Searching groups list", false, jxqr);
  });
}
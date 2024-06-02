var parentFolderId;

function onPageClick(event) {
  event.preventDefault();

	var pageId = $(this).attr('id').split('_')[1];
	// pageId will contain ajax/listForCreateMenu/...
	var url = "/workspace/editor/form/" + pageId +"&parentFolderId=" + parentFolderId;
	//cache listing for 60seconds for browsing through paginated list.
	if (RS.webResultCache.get(url) !== undefined) {
		$('#formDetails').html('');
		$('#formDetails').html(RS.webResultCache.get(url));
	} else {
		var jxqr = $.get(createURL(url), function (data, xhr) {
			RS.webResultCache.put(url, data, 60 * 1000 ); // cache for 60s
			$('#formDetails').html('');
			$('#formDetails').html(data);
		}).fail(function() {
			RS.ajaxFailed("Form navigation", false, jxqr);
		});
	}
}

function initFormCreateMenuDialog(parentId) {
  parentFolderId = parentId;
  jxqr = $.get(`/workspace/editor/form/ajax/generateFormCreateMenu?parentFolderId=${parentFolderId}`, function(data) {
    $('#formDetails').html('');
    $('#formDetails').html(data);
  }).fail(function () {
    RS.ajaxFailed("Failed to load form list for create form dialog", false, jxqr);
  });
}

// handler for paginated form links in create-from-form dialog
$("body").on("click", ".form-create-menu-page-link", onPageClick);
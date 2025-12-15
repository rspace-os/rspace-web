/* jshint maxerr: 100 */

/**
 * Contains browser scripting for search dialog in workspace
 * Will only work in workspace
 * Depends on workspace.js
 */
/*
 * id = search type identifier used on server; displayName = menu name
 */
$(document).on("click", ".search-highlight-toggle", function (e) {
  // switch the label
  var altLabel = $(this).attr("data-altLabel");
  $(this).attr("data-altLabel", $(this).html());
  $(this).html(altLabel);

  $(this).data("on", !Boolean($(this).data("on")));

  // change all the highlighted fields
  $("#file_table tbody tr *[data-highlight]").each(function (i, node) {
    var altLabel = $(node).attr("data-highlight");
    $(node).attr("data-highlight", $(node).html());
    $(node).html(altLabel);
  });
});

function resetSearch() {
  workspaceSettings.options = [];
  workspaceSettings.terms = [];
  $("#searchModePanel").hide();
  workspaceSettings.searchMode = false;
}

// Resets search and all orderings/other parameters imposed by the search.
// Called when user manually resets search, and when they go from search mode
// with an empty search term (in simple search, not in advanced one).
function abandonSearch() {
  resetSearch();
  RS.blockPage("Abandoning search...", false, $(".mainTable tbody"));
  workspaceSettings.parentFolderId = $('#currFolderId').val();
  workspaceSettings.url = "/workspace/ajax/view/" + workspaceSettings.parentFolderId;
  resetPageNumber();
  getAndDisplayWorkspaceResults(workspaceSettings.url, workspaceSettings);
}

/**
* Performs a search – blocks the page, retrieves workspace results and shows them, then updates workspaceSettings
* accordingly.
**/
function doWorkspaceSearch(url, data) {
  getAndDisplayWorkspaceResults(url, data, function (result) {
    // Configure workspaceSettings
    workspaceSettings.url = "/workspace/ajax/search"

    // for search results we set root folder as parent
    workspaceSettings.parentFolderId = $('#currFolderId').val();
    workspaceSettings.grandparentFolderId = null;
  });
}

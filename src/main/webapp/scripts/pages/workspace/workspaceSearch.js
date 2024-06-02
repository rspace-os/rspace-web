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

//Loads chemical search dialog
function loadChemSearcher() {
  $("#chemical-Searcher").attr('mode', 'search');
  $("#chemical-Searcher").attr('pageNumber', 0);

  $(document).ready(function () {
    RS.switchToBootstrapButton();
    $("#chemical-Searcher").dialog({
      title: "Chemical Searcher",
      resizable: true,
      width: 900,
      height: 500,
      closeOnEscape: false,
      autoOpen: true,
      modal: true,
      zindex: 300,
      buttons: {
        'Cancel': function () {
          $(this).dialog('close');
        },
        'Previous': {
          text: 'Previous',
          id: 'chemicalSearchPreviousButton',
          disabled: true,
          click: function () {
            var currentPage = $("#chemical-Searcher").attr('pageNumber');
            $("#chemical-Searcher").attr('pageNumber', (parseInt(currentPage) - 1));
            doChemicalSearch();
          }
        },
        'Next': {
          text: 'Next',
          id: 'chemicalSearchNextButton',
          disabled: true,
          click: function () {
            var currentPage = $("#chemical-Searcher").attr('pageNumber');
            $("#chemical-Searcher").attr('pageNumber', (parseInt(currentPage) + 1));
            doChemicalSearch();
          }
        },
        'Search': {
          text: 'Search',
          id: 'chemicalSearchButton',
          width: '110',
          click: function () {
            if ($("#chemical-Searcher").attr('mode') == 'search') {
              // Call into our iframe to get the content as mol format
              var $f = $("#chemicalSearchFrame");
              $f[0].contentWindow.getMol().then(function (res) {
                $("#chemical-Searcher").attr('mol', res);
                $("#chemical-Searcher").attr('pageNumber', 0);

                doChemicalSearch();
              });

            } else if ($("#chemical-Searcher").attr('mode') == 'results') {
              RS.disableJQueryDialogButtonWithLabel('Previous');
              RS.disableJQueryDialogButtonWithLabel('Next');
              $('#chemical-Searcher').empty();
              $('#chemical-Searcher').attr('mode', 'search');
              $('#chemicalSearchButton').text('Search');
              $('#chemical-Searcher').dialog('option', 'title', 'Chemical Search');
            }
          }
        }
      },
      close: function (event, ui) {
        $('#chemical-Searcher').empty();
        $('.searchOptions').remove();
      }
    });
    RS.switchToJQueryUIButton();
  });

  RS.switchToBootstrapButton();

  RS.disableJQueryDialogButtonWithLabel('Previous');
  RS.disableJQueryDialogButtonWithLabel('Next');

  RS.switchToJQueryUIButton();

  //Add search options
  var searchOptions = $('<div class="bootstrap-custom-flat searchOptions" id="chemicalSearchOptions"> \
                            <div class="row"> \
                                <div class="col-md-12" style="text-align: right;"> \
                                    <span class="searchType">Search type:</span> \
                                    <input type="radio" name="searchOptions" id="radioSubstructure" value="SUBSTRUCTURE" checked title="Substructure search finds all structures that contain the query structure as a subgraph." /> \
                                    <label for="radioSubstructure" title="Substructure search finds all structures that contain the query structure as a subgraph.">Substructure</label> \
                                    <input type="radio" name="searchOptions" id="radioExactMatch" value="DUPLICATE" title="Retrieves the same molecule as the query. Use it to check whether a chemical structure already exists." /> \
                                    <label for="radioExactMatch" title="Retrieves the same molecule as the query. Use it to check whether a chemical structure already exists.">Exact Match</label> \
                                    <input type="radio" name="searchOptions" id="radioFull" value="FULL" title="A full structure search finds molecules that are equal (in size) to the query structure." /> \
                                    <label for="radioFull" title="A full structure search finds molecules that are equal (in size) to the query structure.">Full Structure</label> \
                                    <input type="radio" name="searchOptions" id="radioSuperstructure" value="SUPERSTRUCTURE" title="Superstructure search finds all molecules where the query is superstructure of the target." /> \
                                    <label for="radioSuperstructure" title="Superstructure search finds all molecules where the query is superstructure of the target.">Superstructure</label> \
                                </div> \
                            </div> \
                          </div>');
  $('#chemical-Searcher').after(searchOptions);
}

function doChemicalSearch() {
  $('#chemical-Searcher').empty();
  $('#chemical-Searcher').html('Searching...');
  RS.disableJQueryDialogButtonWithLabel('Previous');
  RS.disableJQueryDialogButtonWithLabel('Next');

  var data = {
    chem: $('#chemical-Searcher').attr('mol'),
    searchType: $('[name=searchOptions]:checked').val(),
    pageSize: 10,
    pageNumber: $("#chemical-Searcher").attr('pageNumber')
  };

  var jqxhr = $.post(createURL("/chemical/ajax/searchChemElement"), data);

  jqxhr.fail(function () {
    alert('Chemical search failed.');
    $('#chemical-Searcher').empty();
    $('#chemical-Searcher').attr('mode', 'search');
    $('#chemicalSearchButton').text('Search');
  });

  jqxhr.done(function () {
    $('#chemical-Searcher').html(jqxhr.responseText);
    $('#chemical-Searcher').attr('mode', 'results');
    $('#chemicalSearchButton').text('New Search');

    $('#chemical-Searcher').find(".breadcrumbLink")
      .attr('href', function () {
        var folderId = $(this).attr('id').split('_')[1];
        return createURL('/workspace/' + folderId);
      })
      .click(function (e) {
        e.preventDefault();
        $('#chemical-Searcher').empty();
        $('#chemical-Searcher').dialog('close');

        var folderId = $(this).attr('id').split('_')[1];
        navigateToFolder(folderId);
      });

    var currentPage = $("#chemical-Searcher").attr('pageNumber');
    var totalPageCount = parseInt($("#chemSearchResultsPropertiesHolder").attr('data-totalPageCount'));
    var totalHitCount = parseInt($("#chemSearchResultsPropertiesHolder").attr('data-totalHitCount'));

    if (currentPage > 0) {
      RS.enableJQueryDialogButtonWithLabel('Previous');
    } else {
      RS.disableJQueryDialogButtonWithLabel('Previous');
    }

    if (currentPage < totalPageCount - 1) {
      RS.enableJQueryDialogButtonWithLabel('Next');
    } else {
      RS.disableJQueryDialogButtonWithLabel('Next');
    }

    if (totalHitCount === 0) {
      $('#chemical-Searcher').dialog('option', 'title', 'Chemical Search - No Results');
    } else {
      var startHit = (currentPage * 10 + 1);
      var endHit = Math.min(startHit + 9, totalHitCount);
      $('#chemical-Searcher').dialog('option', 'title', 'Chemical Search - Results ' + startHit + '-' + endHit + " of " + totalHitCount);
    }
  });
}
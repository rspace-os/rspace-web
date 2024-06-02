/**
* This scrips calls `fileSelected(globalId, fileName)` and `fileUnselected()` which must be implemented elsewhere.
**/

$(document).ready(function() {

     /*
     * search input code
     */
    var searchDocHandler = function() {

        var searchQuery = $('#searchQueryInput').val();
        if (!searchQuery || searchQuery.length < 2) {
            apprise("Search term must be at least 2 characters.");
            return false;
        }

        RS.blockPage();
        var jqxhr = $.get("/workspace/ajax/simpleSearch", {
            searchQuery : searchQuery
        });
        jqxhr.always(function() {
            RS.unblockPage();
            fileUnselected();
        });
        jqxhr.done(function(data) {
            var html = "";
            var foundDocs = 0;
            var searchResultTemplate = $('#searchResultTemplate')
                    .html();

            if (data.data) {
                var milliseconds = new Date().getTime();
                $.each(data.data, function() {
                    var globalId = this.oid.idString;
                    if (!onlyDocuments || globalId.startsWith("SD")) {
                        var iconUrl;
                        if (globalId.startsWith("FL") || globalId.startsWith("GF"))
                            iconUrl = "/images/icons/folder.png"
                        else if (globalId.startsWith("NB"))
                            iconUrl = "/images/icons/notebook.png"
                        else if (globalId.startsWith("GL"))
                            iconUrl = "/gallery/getThumbnail/" + globalId.substring(2) + "/" + milliseconds;
                        else
                            iconUrl = "/image/getIconImage/" + this.iconId;
                        var resultData = {
                            iconUrl : iconUrl,
                            docName : this.name,
                            globalId : globalId,
                            ownerFullName : this.ownerFullName,
                            ownerUsername : this.ownerUsername
                        };
                        html += Mustache.render(searchResultTemplate,
                                resultData);
                        foundDocs++;
                    }
                });
            }

            var header = "<div id='resultsDiv'>";
            if (foundDocs === 0) {
                header += "No documents found.";
            } else if (foundDocs === 1) {
                header += "Found 1 document:";
            } else {
                header += "Found " + foundDocs + " documents:";
            }
            header += "</div>";

            html = header + html;
            $('#searchResultsDiv').empty().append(html);

        });
        jqxhr.fail(function() {
            RS.ajaxFailed("Search", false, jqxhr);
        });

        return false;
    };

    RS.addOnEnterHandlerToDocument("#searchQueryInput", searchDocHandler);

    $('#searchBtn').click(searchDocHandler);

    $(document).on('click', '.searchResultDiv', function() {
        $('.selectedSearchResult').removeClass('selectedSearchResult');
        $(this).addClass('selectedSearchResult');
        fileSelected($(this).data('globalid'), $(this).data('name'));
        return false;
    });

    /*
     * file tree code
     */
    var clickHandler = function(clickedNode) {
        if (!onlyDocuments || !clickedNode.folder) {
            fileSelected(clickedNode.data.globalId, clickedNode.data.name);
        } else {
            fileUnselected();
        }
    };

    setUpFileTreeBrowser({
        nodeClickHandler : clickHandler,
        showGallery : false,
        fullSizeTree : true
    });

});
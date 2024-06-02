
/**
 * Handler for retrieving results when clicking on a table header link when a listing is reloaded
 * Takes a mandatory settings object with 3 properties:
 * - link the jquery <a> link object clicked
 * - event the click event
 * - resultsContainer a jquery selector for the container in which to place the new search results
 * @param settings
 */
RS.toggle = function toggle(settings) {
	var link$ = settings.link;
	var e = settings.event;
	var resultsContainer = settings.resultsContainer;
	var linkText = link$.attr('href');
	var linkId = link$.attr('id');

	var switchTo = 'DESC';
	if (linkText.indexOf('ASC') > -1) {
		switchTo = 'DESC';
	} else {
		switchTo = 'ASC';
	}
	e.preventDefault();
	var url = createURL(link$.attr('href'));
	if (RS.webResultCache.get(url) != undefined) {
		$(resultsContainer).html(RS.webResultCache.get(url));
		_toggleSortOrder(linkText, switchTo, linkId);
	} else {
		var jxqr = $.get(url, function(data) {
			$(resultsContainer).html(data);
			_toggleSortOrder(linkText, switchTo, linkId);
			RS.webResultCache.put(url, data, 30 * 1000);
		});
		jxqr.fail(function() {
			RS.ajaxFailed("Getting user  information", false, jxqr);
		});
	}
};

function _toggleSortOrder(linkText, switchTo, linkId) {
	if (linkText.match(/sortOrder=/)) {
		linkText = linkText.replace(/sortOrder=[^&]+/, "sortOrder=" + switchTo);
	} else {
		linkText = linkText + "orderBy=" + switchTo;
	}
	$('#' + linkId).attr('href', linkText);
	if(switchTo === 'ASC') {
		_appendDownIcon(linkId);
    } else if(switchTo === 'DESC') {
    	_appendUpIcon(linkId);
    }
 }

function _appendDownIcon(linkId) {
	_setSortOrderIndicator('arrow_down.png', linkId);
}

function _appendUpIcon(linkId) {
	_setSortOrderIndicator('arrow_up.png', linkId);
}

function _setSortOrderIndicator (icon, linkId) {
	$('#' + linkId).parent().append("&nbsp; <image src='/images/" + icon +"' style='vertical-align: middle;'/>");
}
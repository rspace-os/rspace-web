/**
 * Based on jqueryFileTreeSorter.js.
 * 
 * Sorts nodes in File Tree Browser according to their data attributes returned from back-end.
 */

function sortFileTreeBrowser(orderBy, sortOrder) {
	$('#fancyTree ul').each(function() {
    var fileTreeLi = $(this).children('li');
    fileTreeLi.sort(function(a, b) {
      var an = String(_getDataFromFileTreeBrowserNode(a, orderBy)).toLowerCase(),
          bn = String(_getDataFromFileTreeBrowserNode(b, orderBy)).toLowerCase();
  
      if (an > bn) {
        if (sortOrder == 'ASC') {
          return 1;
        } else {
          return -1;
        }
      } else if (an < bn) {
        if (sortOrder == 'ASC') {
          return -1;
        } else {
          return 1;
        }
      } else {
        return 0;
      }
    });
    fileTreeLi.detach().appendTo(this);
	});
}

function _getDataFromFileTreeBrowserNode(node, dataName) {
  return $(node).data(dataName);
}


/**
 * Sorts files in jQuery File Tree according to their data attributes returned
 * from back-end.
 * 
 * This implementation assumes that the jQuery File Trees have a class jqueryFileTree
 * and user preferences are 'select' tags that can be found by ids 'orderBy' which
 * options contain data attribute name and 'sortOrder' which options are either 'ASC'
 * or 'DESC'.
 */

// Global variables
var fileTreeViewObserver = undefined;

function startFileTreeDOMObserving() {
	if (fileTreeViewObserver == undefined) {
		MutationObserver = window.MutationObserver || window.WebKitMutationObserver;
		fileTreeViewObserver = new MutationObserver(function(mutations, observer) {
			sortTreeView();
		});
	}
	var config = {
		subtree: true,
		attributes: true,
		childList: true,
		characterData: true
	};
	var fileTree = document.querySelector('#file_tree');
	var fileTree2 = document.querySelector('#movefolder-tree');
	
	// If this is in gallery, then the id is different
	// TODO: might be worth changing the id in galleriesCrudops.tag
	if (fileTree == undefined) {
		fileTree = document.querySelector('#galleries-folder-tree');
	}
	
	fileTreeViewObserver.observe(fileTree || fileTree2, config);
}

function stopFileTreeDOMObserving() {
	if (fileTreeViewObserver != undefined) {
		fileTreeViewObserver.disconnect();
	}
}

function getDataFromRecord(record, dataName) {
	var a = $(record).children('a');
	if (typeof(a.data) == "function")
		return a.data(dataName);
	else
		return "";
}

function sortTreeView() {
	orderBy = $('.orderBy:visible').val();
	sortOrder = $('.sortOrder:visible').val();
	
	// If user preferences cannot be found, just use the default values.
	if (orderBy == undefined) {
		orderBy = "name";
	}
	if (sortOrder == undefined) {
		sortOrder = "ASC";
	}
	
	stopFileTreeDOMObserving();
	
	$('.jqueryFileTree').each(function() {
		var fileTreeLi = $(this).children('li');
		fileTreeLi.sort(function(a, b) {
			var an = String(getDataFromRecord(a, orderBy)).toLowerCase(),
				bn = String(getDataFromRecord(b, orderBy)).toLowerCase();

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
	
	startFileTreeDOMObserving();
}

$(document).ready(function() {
	// Sorting for the first time
	sortTreeView();
	// Let's observe changes in the file tree view DOM subtree or user
	// sorting preferences.
	// If there is a change, let's sort the tree according to the user
	// choices (the sorting works completely in front-end).
	$('.orderBy').on("change", sortTreeView);
	$('.sortOrder').on("change", sortTreeView);
	startFileTreeDOMObserving();
})
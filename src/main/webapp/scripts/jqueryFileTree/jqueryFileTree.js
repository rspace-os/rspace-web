// jQuery File Tree Plugin
//
// Version 1.01_rs3
//
// Cory S.N. LaViska
// A Beautiful Site (http://abeautifulsite.net/)
// 24 March 2008
//
// Visit http://abeautifulsite.net/notebook.php?article=58 for more information
//
// Usage: $('.fileTreeDemo').fileTree( options, folderEventCallback, subtreeShownCallback )
//
// Options:  root           - root folder to display; default = /
//           script         - location of the serverside AJAX file to use; default = jqueryFileTree.php
//           folderEvent    - event to trigger expand/collapse; default = click
//           expandSpeed    - default = 500 (ms); use -1 for no animation
//           collapseSpeed  - default = 500 (ms); use -1 for no animation
//           expandEasing   - easing function to use on expand (optional)
//           collapseEasing - easing function to use on collapse (optional)
//           multiFolder    - whether or not to limit the browser to one subfolder at a time
//           loadMessage    - Message to display while initial tree loads (can be HTML)
//
// History:
//
// 1.01_rs3 - optional third callback called after a new subtree is shown (29 Jan 2018)
// 1.01_rs2 - passing clicked node to a callback & refactoring excessive $(this) usage (01 Nov 2017)
// 1.01_rs1 - handling connection error (28 July 2014) 
// 1.01 - updated to work with foreign characters in directory/file names (12 April 2008)
// 1.00 - released (24 March 2008)
//
// TERMS OF USE
// 
// This plugin is dual-licensed under the GNU General Public License and the MIT License and
// is copyright 2008 A Beautiful Site, LLC. 
// 2 changes made to config! 
        // if( o.initialLoad == undefined ) o.initialLoad = false;
		//	if( o.showGallery == undefined ) o.showGallery = true;
//

if(jQuery) (function($){
	$.extend($.fn, {
		fileTree: function(o, h, ssc) {
			// Defaults
			if( !o ) var o = {};
			if( o.root == undefined ) o.root = '/';
			if( o.script == undefined ) o.script = 'jqueryFileTree.php';
			if( o.folderEvent == undefined ) o.folderEvent = 'click';
			if( o.expandSpeed == undefined ) o.expandSpeed= 500;
			if( o.collapseSpeed == undefined ) o.collapseSpeed= 500;
			if( o.expandEasing == undefined ) o.expandEasing = null;
			if( o.collapseEasing == undefined ) o.collapseEasing = null;
			if( o.multiFolder == undefined ) o.multiFolder = true;
			if( o.loadMessage == undefined ) o.loadMessage = 'Loading...';
			// this is new! and not part of the library
			if( o.initialLoad == undefined ) o.initialLoad = false;
			if( o.showGallery == undefined ) o.showGallery = true;
			
			$(this).each( function() {
				
				function showTree(c, t) {
					$(c).addClass('wait');
					$(".jqueryFileTree.start").remove();
					
					var params = { dir: t, initialLoad:o.initialLoad, showGallery:o.showGallery};
					if (o.order) { params.order = o.order; }; // added for RSpace, not a part of the library
					if (o.fileSystemId) { params.fileSystemId = o.fileSystemId; }; // added for RSpace
					if (o.fileStoreId) { params.fileStoreId = o.fileStoreId; }; // added for RSpace
					
					$.post(o.script, params, function(data) {
						o.initialLoad = false;
						$(c).find('.start').html('');
						$(c).removeClass('wait error').append(data);
						if( o.root == t ) $(c).find('UL:hidden').show(); else $(c).find('UL:hidden').slideDown({ duration: o.expandSpeed, easing: o.expandEasing });
						bindTree(c);
						typeof ssc === 'function' && ssc();
					}).fail(function(data) {
					    $(c).removeClass('wait').addClass('error');
					});
				}
				
				function bindTree(t) {
					$(t).find('LI A').bind(o.folderEvent, function() {
					    var $node = $(this);
						if( $node.parent().hasClass('directory') ) {
							if( $node.parent().hasClass('collapsed') ) {
								// Expand
								if( !o.multiFolder ) {
									$node.parent().parent().find('UL').slideUp({ duration: o.collapseSpeed, easing: o.collapseEasing });
									$node.parent().parent().find('LI.directory').removeClass('expanded error').addClass('collapsed');
								}
								$node.parent().find('UL').remove(); // cleanup
								showTree( $node.parent(), encodeURIComponent($node.attr('rel').match( /.*\// )) );
								$node.parent().removeClass('collapsed').addClass('expanded');
							} else {
								// Collapse
								$node.parent().find('UL').slideUp({ duration: o.collapseSpeed, easing: o.collapseEasing });
								$node.parent().removeClass('expanded error').addClass('collapsed');
							}
							// lg custom call to react to folder events
						    h($node.attr('rel'),"directory", $node);
						} else {
							h($node.attr('rel'),"file", $node);
						}
						return false;
					});
					// Prevent A from triggering the # on non-click events
					if( o.folderEvent.toLowerCase != 'click' ) $(t).find('LI A').bind('click', function() { return false; });
				}
				// Loading message
				$(this).html('<ul class="jqueryFileTree start"><li class="wait">' + o.loadMessage + '<li></ul>');
				//Jquery html function has previously been used to save data to nfsFileStore table. This function will decode those strings.
				//File paths that were not encoded by jquery html function will remain untouched.
				function htmlDecode(input) {
					var doc = new DOMParser().parseFromString(input, "text/html");
					return doc.documentElement.textContent;
				}
				showTree( $(this), encodeURIComponent(htmlDecode(o.root)));
			});
		}
	});
	
})(jQuery);
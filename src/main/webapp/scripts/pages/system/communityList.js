
$(document).ready(function() {
	addCrudops();
	addRemoveCommunityHandler();
	RS.setupPagination(paginationEventHandler);
});

function getSelectedCommunities(){
	var selectedCommunities = {};
	$("input[class='commactionCbox']:checked").each (function () {
		var id =$(this).attr('id').split("_")[1];
		var name = $(this).data("name");
		selectedCommunities[id] = name;
	});
	return selectedCommunities;
}

function addRemoveCommunityHandler(){
	$('body').on('click', '#removeCommunity', function (e){
		var data = getSelectedCommunities();

		if(Object.keys(data).includes("-1")) {
			apprise ("You can't delete the default all-labs community, please remove this from the selection. ");
			return;
		}

		var callback = function() {
			RS.blockPage("Removing communities...")
			var jxqr= $.post(createURL('/community/admin/ajax/removeCommunity'), {"ids[]":Object.keys(data)}, function (e) {
				RS.confirm(`Communit${Object.values(data).length > 1 ? 'ies' : 'y'} deleted.`, 'success', 5000);
				RS.unblockPage();
				window.location.href=createURL('/community/admin/list');
			});
			jxqr.fail(function(){
				RS.ajaxFailed("Removing community",true,jxqr);
			});
		}

		var event = new CustomEvent('confirm-action', { 'detail': {
      title: "Confirm deletion",
      consequences: `Are you sure you want to delete the following communit${Object.values(data).length > 1 ? 'ies' : 'y'}: <b>${Object.values(data).join(', ')}</b>?`,
			variant: "warning",
      callback: callback
    }});
    document.dispatchEvent(event);
	});
}

/**
 * Handles crudops menu for table listing
 */
function addCrudops (){
	$('body').on('click', '.commactionCbox', function (){
		var selectedChkBxes$= $("input[class='commactionCbox']:checked");
		if(selectedChkBxes$.size() > 0){
			$('#removeCommunity').show();
		}else{
			$('#removeCommunity').hide();
		}
	});
	$('.commcrudops').hide();// hide initially
}

var paginationEventHandler = function (source, e){
	var url = source.attr('id').split("_")[1];
	$.get(createURL(url),
			function (data) {
				$('#communityListContainer').html(data);
			}
	);
}
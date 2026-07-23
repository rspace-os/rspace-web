
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
			apprise (RS.msg("legacyjs.system.communityList.cannotDeleteDefault"));
			return;
		}

		var callback = function() {
			RS.blockPage(RS.msg("legacyjs.system.communityList.removing"))
			var jxqr= $.post(createURL('/community/admin/ajax/removeCommunity'), {"ids[]":Object.keys(data)}, function (e) {
				RS.confirm(RS.msg("legacyjs.system.communityList.deleted", Object.values(data).length), 'success', 5000);
				RS.unblockPage();
				window.location.href=createURL('/community/admin/list');
			});
			jxqr.fail(function(){
				RS.ajaxFailed(RS.msg("legacyjs.system.communityList.removingCommunityAction"),true,jxqr);
			});
		}

		RS.createConfirmationDialog({
			title: RS.msg("legacyjs.system.common.confirmDeletionTitle"),
			consequences: RS.msg("legacyjs.system.communityList.confirmDeletionConsequences", Object.values(data).length, RS.formatList(Object.values(data))),
			variant: "warning",
			callback: callback
		});
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


$(document).ready(function() {
  setUpCheckboxHandlers ();
  setUpRemoveGroupHandler();
  setUpMoveToOtherCommunityHandler();
  setUpEditProperty();
  setUpRemoveAdmin();
  setUpAddGroup();
  setUpAddAdmin();
  setUpAppsSettingsDialog();
  if (view == false) {
    showEdit();
  }
});

function setUpAppsSettingsDialog() {
  $(document).ready(function() {
    $('#communityAppsSettingsDialog').dialog({
      resizable: false,
      autoOpen:false,
      height:600,
      width:600,
      modal: true,
      zIndex: 3002,
      buttons: {
        [RS.msg("legacyjs.system.common.ok")]: function() {
            $(this).dialog("close");
        }
      }
    });
  });

  $('#editCommunityAppsSettings').click(function(e) {
    loadSettingsFromServer();
    $('#communityAppsSettingsDialog').dialog('open');
  });
}

function toggleAddGroupView(showHide) {
    $('#defaultCommunityListing').toggle(showHide);
    $('#currentCommunityListing').toggle(!showHide);
}

function setUpAddGroup() {
  $('body').on('click', '#addGroup', function (e) {
    e.preventDefault();
    toggleAddGroupView(true);
  });

  $('body').on('click', '#addGroupSubmit', function (e) {
    e.preventDefault();
    if ($("input[class='groupcbox']:checked").size() == 0) {
      apprise(RS.msg("legacyjs.system.community.selectGroupsToAdd"));
      RS.focusAppriseDialog();
      return;
    }

    // put selected groups into a form field
    var groupIds = [];
    $("input[class='groupcbox']:checked").each(function () {
      groupIds.push($(this).data('groupid'));
    });
    $('#addGroupIds').val(groupIds.join(','));
    var data = $('#addGroupsForm').serialize();

    var jxqr = $.post(createURL('/community/admin/ajax/move'), data, function(response) {
        if (response.data != null) {
            // redirect after post (will reload current community)
            window.location.href = createURL('/system/community/' + response.data);
        } else {
            apprise(getValidationErrorString(response.errorMsg));
            RS.focusAppriseDialog();
        }
    });
    jxqr.fail(function() {
        RS.ajaxFailed(RS.msg("legacyjs.system.community.movingGroupAction"), false, jxqr);
    });
  });

  $('body').on('click', '#addGroupCancel', function (e){
    e.preventDefault();
    toggleAddGroupView(false);
  });
}


function setUpAddAdmin() {
  $('body').on('click', '#addAdminLink', function (e){
    $('#adminsList').hide();
    
    if($('#adminForm').size()==0) {
    // this is 1st time we've accessed this form,load from server
    $.get(createURL('/community/admin/ajax/availableAdmins'), {id:RS.communityId}, function (html) {
        $('#adminContainer').append(html);
        if( $('.admincbox').size() >0) { // are there some availabe admins??
          $('#adminForm').show();
        } else {
          // there are no admins
          $('#adminForm').hide();
          $('#adminsList').show();
          apprise(RS.msg("legacyjs.system.community.noAvailableAdmins"));
          RS.focusAppriseDialog();
        }
    });
    // it's on the page, just show it
    } else {
      $('#adminForm').show();
    }
  });

  $('body').on('click', '#addAdminSubmit', function (e){
    e.preventDefault();
    if(  $("input[class='admincbox']:checked").size()==0){
      apprise(RS.msg("legacyjs.system.community.selectAdminsToAdd"));
      RS.focusAppriseDialog();
    }else {
      $(this).closest('form').submit();
    }
  });

  $('body').on('click', '#addAdminCancel', function (e){
    e.preventDefault();
    $('#adminForm').hide();
    $('#adminsList').show();
  });
}

function setUpRemoveAdmin(){
  $('body').on('click', '.removeAdminLink', function (e){
    e.preventDefault();
    var admin = $(this).attr('id').split("_")[1];
    var data = {
    adminToRemove:admin,
    commId:RS.communityId
    }
    var jxqr=$.post(createURL('/community/admin/ajax/removeAdmin'),data, function (response) {
        if (response.data) {
          window.location.href=createURL('/system/community/'+RS.communityId);
        } else if(response.errorMsg) {
          apprise(getValidationErrorString(response.errorMsg));
          RS.focusAppriseDialog();
        }
      
    });
    jxqr.fail(function(){
      RS.ajaxFailed(RS.msg("legacyjs.system.community.removingAdminAction"),false,jxqr);
    });
    
  });
}

/**
 * Swaps div view for editable form view
 */
function showEdit(){
  $('#editPropertyForm').show();
  $('#propertyView').hide();
  $('#editCommunityProps').hide();
}

/**
 * Shows read-only div view
 */
function showView(){
  $('#editPropertyForm').hide();
  $('#propertyView').show();
  $('#editCommunityProps').show();
}

function setUpEditProperty(){
  $('body').on('click','#editCommunityProps', function (){
    showEdit();
  });
  $('body').on('click','#editProfileCancel', function (){
    showView();
  });
}

function setUpMoveToOtherCommunityHandler(){
  $('body').on('click','#moveGroup', function (){
    $('#labGroupList').hide();
    RS.selectedIds=getSelectedIdArray();
      //$('#moveToOtherCommunity').show();
      if( $('#labGroupForm').size() ==0) {
        $.get(createURL('/community/admin/ajax/list'),{viewType:"moveCommunity"}, function (data){
          $('#labGroupContainer').append(data);
          $('#labGroupForm').show();
          $('#moveCommunityForm').append('<input  type="hidden" name="from" value="'+RS.communityId+'"/>');
        });
      }else {
        $('#labGroupForm').show();
      }
  });
}

function setUpCheckboxHandlers() {
  $('body').on('click', '.actionCbox',function (e){
    var selectedChkBxes$= $("input[class='actionCbox']:checked");
    if(selectedChkBxes$.size() > 0){
      $('#removeGroup').show();
      $('#moveGroup').show();
      $('#addGroup').hide();
    }else{
      $('#removeGroup').hide();
      $('#moveGroup').hide();
      $('#addGroup').show();
    }
  });
  hideCommunityCrudops();
  $('body').on('click','#moveCancel', function (e) {
    e.preventDefault();
    $('#labGroupForm').hide();
    $('#labGroupList').show();
    
  });
  $('body').on('click', '#moveSubmit', function (e){
    e.preventDefault();
    $('#groupIds').val(RS.selectedIds);
    var data=$('#moveCommunityForm').serialize();
    var jxqr= $.post(createURL('/community/admin/ajax/move'),data,function(response){
      $('#moveToOtherCommunity').hide();
      if(response.data!=null){
        //redirect after post
        window.location.href=createURL('/system/community/'+response.data);
      }else {
        apprise(getValidationErrorString(response.errorMsg));
        RS.focusAppriseDialog();
      }    
    });
    jxqr.fail(function(){
      RS.ajaxFailed(RS.msg("legacyjs.system.community.movingGroupAction"),false,jxqr);
      });
  });
  
}

function hideCommunityCrudops() {
  $('.crudops').hide();
}

function setUpRemoveGroupHandler() {
  $('body').on('click','#removeGroup', function (){
  
    apprise("<div style='line-height:1.3em'>" + RS.msg("legacyjs.system.community.confirmRemoveGroups") + "</div>",
      {confirm:true, textOk: RS.msg("legacyjs.system.common.remove") }, function () {
        var ids = getSelectedIdArray();
        var params ={
            "ids[]":ids,
            communityId:RS.communityId
            };
          var jxqr = $.post(createURL('/community/admin/ajax/remove'), params, function(response){ 
              if (response.data!=null) {
                // reload if successful
                window.location.href='/system/community/'+response.data;
              } else { 
                apprise(getValidationErrorString(response.errorMsg));
                  }
           });
          jxqr.fail(function(){
            RS.ajaxFailed(RS.msg("legacyjs.system.community.removingGroupAction"),false,jxqr);
           });
        });
    RS.focusAppriseDialog();
  });
}

/**
 * Based on checkbox selection, returns a Javascript array (not a jquery wrapped set)
 *  of {uname:  id: role: } property sets representing
 * the username , id, and role strings, respectively of the selected user(s).
 */
function getSelectedIdNames() {
  var selectedGroups = new Array();
  $("input[class='actionCbox']:checked").each(function () {
    var id =$(this).attr('id').split("_")[1];
    var name = $(this).parent().siblings('.name').text();
    selectedGroups.push ( {
      "id":id,
      "name":name
    });
  });
  return selectedGroups;
}

function getSelectedIdArray() {
  var selected = getSelectedIdNames();
  var ids = $.map(selected, function(data) {
    return data.id;
  });
  return ids;
}

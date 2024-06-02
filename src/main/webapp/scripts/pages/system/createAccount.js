var labGroups = [], 
    displayedLabgroups = [], 
    sortedBy = "", 
    labgroupSearchTerm = "",
    alwaysShowPasswordFields = true;

$(document).ready(function () {

  $(document).on('click', '#newUserAccountButton', function (e) {
    e.preventDefault();
    var url = "/system/ajax/createAccountForm";
    var jqxhr = $.get(url, { "role": "ROLE_USER" }, function (data) {
      $('#buttonDescriptions').html(data);
    });
    jqxhr.fail(function () {
      RS.ajaxFailed("Getting create user account form", false, jqxhr);
    });
  });

  $(document).on('click', '#newPiAccountButton', function (e) {
    e.preventDefault();
    var url = "/system/ajax/createAccountForm";
    var jqxhr = $.get(url, { "role": "ROLE_PI" }, function (data) {
      $('#buttonDescriptions').html(data);
    });
    jqxhr.fail(function () {
      RS.ajaxFailed("Getting create user account form", false, jqxhr);
    });
  });

  $(document).on('click', '#newAdminAccountButton', function (e) {
    e.preventDefault();
    var url = "/system/ajax/createAccountForm";
    var jqxhr = $.get(url, { "role": "ROLE_ADMIN" }, function (data) {
      $('#buttonDescriptions').html(data);
    });
    jqxhr.fail(function () {
      RS.ajaxFailed("Getting create user account form", false, jqxhr);
    });
  });

  $(document).on('click', '#newSysAdminAccountButton', function (e) {
    e.preventDefault();
    var url = "/system/ajax/createAccountForm";
    var jqxhr = $.get(url, { "role": "ROLE_SYSADMIN" }, function (data) {
      $('#buttonDescriptions').html(data);
    });
    jqxhr.fail(function () {
      RS.ajaxFailed("Getting create user account form", false, jqxhr);
    });
  });

  $(document).on('submit', '#createUserAccountForm', function (e) {
    e.preventDefault();
    var isChecked = $("#checkCreateRepeatUserAccount").is(':checked');

    RS.blockPage("Creating account...");
    var data = $('#createUserAccountForm').serialize();
    var jqxhr = $.post('/system/ajax/createUserAccount', data);
    jqxhr.always(function () {
      RS.unblockPage();
    });
    jqxhr.done(function (response) {
      if (response.data == "Success") {
        if (response.errorMsg.errorMessages.length !== 0) {
          $().toastmessage('showNoticeToast', response.errorMsg.errorMessages);
        }
        $().toastmessage({
          text: 'New Account has been created successfully',
          sticky: false,
          position: 'top-right',
          type: 'success',
          close: function () {
            if (isChecked) {
              $(':input', '#createUserAccountForm').not(':button, :submit, :reset, :hidden, :radio, :checkbox')
                .val('')
                .removeAttr('checked')
                .removeAttr('selected');
            } else {
              window.location.href = "/system/createAccount";
            }
          }
        });
        $().toastmessage('showSuccessToast');
      } else if (response.errorMsg.errorMessages.length !== 0) {
        $("#warningDisplay strong").html(response.errorMsg.errorMessages);
        $('#warningDisplay')
          .fadeIn(1000, function () { $('#warningDisplay'); })
          .fadeOut(7000, function () { $('#warningDisplay'); });
      }
    });
    jqxhr.fail(function () {
      RS.ajaxFailed("Create user account", false, jqxhr);
    });
  });

  $(document).on('click', '#generatePasswordButton', function (e) {
    e.preventDefault();

    var url = "/system/ajax/generateRandomPassword";
    var jqxhr = $.get(url, function (response) {
      var generatedPassword = response.data;
      $("input[name='password']").val(generatedPassword);
      $("input[name='passwordConfirmation']").val(generatedPassword);
    });
    jqxhr.fail(function () {
      RS.ajaxFailed("Getting generated password", false, jqxhr);
    });
  });

  $(document).on('click', '#showPassword', function () {
    if ($("#showPassword").is(':checked')) {
      $("input[name='password']").attr('type', 'text');
      $("input[name='passwordConfirmation']").attr('type', 'text');
    } else {
      $("input[name='password']").attr('type', 'password');
      $("input[name='passwordConfirmation']").attr('type', 'password');
    }
  });

  $(document).on('click', '#getLdapDetails', function (e) {
    e.preventDefault();

    var username = $("input[name='username']").val();
    if (!username) {
      $().toastmessage('showNoticeToast', "Please provide the username");
      return;
    }

    var $firstName = $("input[name='firstName']");
    var $lastName = $("input[name='lastName']");
    var $email = $("input[name='email']");
    retrieveUserLdapDetails(username, $firstName, $lastName, $email);
  });

  $(document).on('change', 'input[type=radio]', function (e) {
    e.stopPropagation();
    var inputClass = $(this).attr('class');
    if ($(this).is(":checked") && inputClass === "communitiesListOption") {
      var communityId = this.value;
      if (communityId === "-10") { // When not including the user in a group
        $('#groupsList tbody').html("");
      } else {
        var url = "/system/ajax/getLabGroups";
        var jqxhr = $.get(url, { "communityId": communityId }, function (response) {
          labGroups = response.data;
          filterGroups();
        });
        jqxhr.fail(function () {
          RS.ajaxFailed("Getting group list", false, jqxhr);
        });
      }
    } else if (inputClass === "communitiesListOption") {
      $('#groupsList tbody').html("");
    }
  });
  
  $(document).on('change', 'input.ssoBackdoorAccountRadioInput', function (e) {
    e.stopPropagation();
    var backdoorAccountSelected = $("input.ssoBackdoorAccountRadioInput:checked").val() == "true";
    togglePasswordField(backdoorAccountSelected);
    $('.backdoorAccountAdditionalText').toggle(backdoorAccountSelected);
  });

  $(document).on('change', 'input.ldapAuthChoice', function (e) {
    e.stopPropagation();
    var ldapAccountSelected = $("#ldapAuthYes").is(":checked");
    togglePasswordField(!ldapAccountSelected);
  });
  
});

function togglePasswordField(showFields) {
    if (alwaysShowPasswordFields) {
        return; // fields always shown, no extra logic
    }
    $(".createPasswordRow").toggle(showFields);
    $(".createPasswordRow input[type='password']").prop("required", showFields);
    // either show user empty password inputs, or submit dummy one
    $(".createPasswordRow input[type='password']").val(showFields ? '' : 'dummy_pass');
}

function populateLabGroups() {
  var groupListHtml = "";
  for (var i = 0; i < displayedLabgroups.length; i++) {
    groupListHtml += `
      <tr>
        <td>
          <input 
            class='groupsListOption' 
            type='radio' 
            value='${displayedLabgroups[i].id}'  
            name='labGroupId' 
            required 
          />
        </td>
        <td><a target="_blank" href="/groups/view/${displayedLabgroups[i].id}">${RS.escapeHtml(displayedLabgroups[i].displayName)}</a></td>
        <td><label>${RS.escapeHtml(displayedLabgroups[i].piFullname)}</label></td>
        <td><label>${displayedLabgroups[i].groupSize}</label></td>
      </tr>
    `;
  }
  $('#groupsList tbody').html(groupListHtml);
}

$(document).on('click', '#sortGroupsByName', function() {
  sortLabgroupsByProperty('displayName');
});

$(document).on('click', '#sortGroupsBySize', function() {
  sortLabgroupsByProperty('groupSize');
});

$(document).on('click', '#sortGroupsByPI', function() {
  sortLabgroupsByProperty('piFullname');
});

$(document).on('input', "#searchLabGroup", function() {
  labgroupSearchTerm = $(this).val();
  filterGroups();
});

function sortLabgroupsByProperty(property) {
  if (sortedBy == property) {
    labGroups = labGroups.reverse();
    sortedBy = "";
  } else {
    labGroups = labGroups.sort(dynamicSort(property));
    sortedBy = property;
  }
  filterGroups();
}

function filterGroups() {
  displayedLabgroups = labGroups.filter(group => 
    `${group.displayName} ${group.piFullname}`.toUpperCase().includes(labgroupSearchTerm.toUpperCase())
  );
  populateLabGroups();
}

function dynamicSort(property) {
  if(property == "groupSize") {
    return function (a,b) {
      return (a[property] < b[property]) ? -1 : (a[property] > b[property]) ? 1 : 0;
    }
  } else {
    return function (a,b) {
      return (a[property].toUpperCase() < b[property].toUpperCase()) ? -1 : (a[property].toUpperCase() > b[property].toUpperCase()) ? 1 : 0;
    }
  }
}

//ldap method common for createAccount.js and batchUserRegistration.js
function retrieveUserLdapDetails(username, $firstName, $lastName, $email) {
  if ($firstName.val() && $lastName.val() && $email.val()) {
    $().toastmessage('showNoticeToast', "First name, Last name and Email are already provided, skipping call to LDAP");
    return;
  }

  RS.blockPage("Retrieving LDAP details...");
  var url = "/system/ldap/ajax/getUserLdapDetails";
  var jqxhr = $.get(url, { username: username });
  jqxhr.always(function () { RS.unblockPage(); });
  jqxhr.done(function (data) {
    if (!data) {
      $().toastmessage('showNoticeToast', "No LDAP details found for user '" + username + "'");
      return;
    }
    if (!$firstName.val()) { $firstName.val(data.firstName); }
    if (!$lastName.val()) { $lastName.val(data.lastName); }
    if (!$email.val()) { $email.val(data.email); }

    $().toastmessage('showSuccessToast', "Found LDAP details, updated empty fields");
  });
  jqxhr.fail(function () {
    RS.ajaxFailed("Getting LDAP details", false, jqxhr);
  });
}
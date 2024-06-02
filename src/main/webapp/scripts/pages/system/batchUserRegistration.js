$(document).ready(function() {
    
    $(document).on('click', '#batchUserRegistrationButton', function(e) {
        e.preventDefault();
        
        var jqxhr = $.get("/system/ajax/batchUserRegistration");
        jqxhr.done(function(data) {
            $('#buttonDescriptions').html(data);
        });
        jqxhr.fail(function() {
            RS.ajaxFailed("Getting batch user registration page", false, jqxhr);
        });
    });
    
    if (typeof ldapEnabled != 'undefined') {
        RS.addOnEnterHandlerToDocument('.batchUsernameInput', function(e){
            var username = this.value;
            if (username) {
               var $thisRow = $(this).parents("tr:first");

               var $firstName = $thisRow.find("input[data-fieldname='firstName']");
               var $lastName = $thisRow.find("input[data-fieldname='lastName']");
               var $email = $thisRow.find("input[data-fieldname='email']");

               retrieveUserLdapDetails(username, $firstName, $lastName, $email);
            }
        });
    }
    
    $(document).on('click', '#batchManualInput', function() {
        $('#chooseInputModeDiv').hide();
        $('#batchCreateForm').show();
        $('#manualRegistrationTablesHelpText').show();
    });
    
    $(document).on('click', '#batchCSVInput', function() {
        $('#chooseInputModeDiv').hide();
        $('#csvInputContent').show();
    });
    
    $(document).on('click', '#csvParseInputContentBtn', function() {
        var jqxhr = submitBatchCsvInput();
        addCsvUploadHandlers(jqxhr);
    });
    
    $(document).on('click', '#csvUploadBtn', function (e){
        e.preventDefault();
        $('#batchUploadUserDlg').dialog('open');
    });

    $(document).ready(function() {
        $('#batchUploadUserDlg').dialog({
            modal : true,
            autoOpen:false,
            width: 420,
            title: "Batch upload users",
            buttons :{
                Cancel: function (){
                    $(this).dialog('close');
                },
                "Upload" : function () {

                    var url = "/system/userRegistration/csvUpload";
                    var jqxhr = submitBatchCsvFile(url);

                    addCsvUploadHandlers(jqxhr);
                    
                    jqxhr.always(function() {
                        $('#batchUploadUserDlg').dialog('close');
                    });
                }
            }
        });
    });
    
    // disabling 'upload' button on a dialog until file is selected
    (function() {
        var $uploadSelectedCsvButton = $('#batchUploadUserDlg').parent().find('button:contains("Upload")');
        $uploadSelectedCsvButton.prop('disabled', true);
        $("#csvFileInput").change(function() {
            $uploadSelectedCsvButton.prop('disabled', false);
        });
    })();

    function submitBatchCsvInput() {

        var input = $('#csvInputContentArea').val();
        
        RS.blockPage("Uploading...");
        var jqxhr = $.post("/system/userRegistration/parseInputString",
                    {usersAndGroupsCsvFormat : input});
        
        jqxhr.always(function() {
            RS.unblockPage();
        });
        
        clearServerMessageAreas();
        return jqxhr;
    } 
    
    function submitBatchCsvFile(url) {
 
        RS.blockPage("Uploading...");

        var data = new FormData($('#csvUploadForm')[0]);
        var jqxhr = $.ajax({
            url: createURL(url),
            data: data,
            cache: false,
            contentType: false,
            processData: false,
            type: 'POST'
        });

        jqxhr.always(function() {
            RS.unblockPage();
        });
        
        clearServerMessageAreas();
        return jqxhr;
    }

    function addCsvUploadHandlers(jqxhr) {
        jqxhr.done(function(result) {
            var validationErrors = result.errors && result.errors.errorMessages
            if (validationErrors.length > 0) {
                $().toastmessage('showErrorToast', 'Errors in CSV content');
                displayServerMessages($("#batchServerErrorMsgs"), result.errors.errorMessages);
                return;
            } 
            $().toastmessage('showSuccessToast', 'CSV content loaded fine');
            $('#csvInputContent').slideUp();
            displayUserImportResults(result);
        });
    }
    
    function displayUserImportResults(results) {

        var users = results.parsedUsers;
        var groups = results.parsedGroups;
        var communities = results.parsedCommunities;
        
        $('#batchCreateForm').show();
        $('#csvInputTablesHelpText').show();

        addImportResultsToBatchTable(users, getBatchUserRowTemplate(), $('#usersToCreateTableBody'));
        addImportResultsToBatchTable(groups, getBatchGroupRowTemplate(), $('#groupsToCreateTableBody'));
        addImportResultsToBatchTable(communities, getBatchCommunityRowTemplate(), $('#communitiesToCreateTableBody'));
        
        toggleTables();
    }

    function addImportResultsToBatchTable(results, rowTemplate, $tableBody) {
        $.each(results, function(i, entity) {
            addBatchTableRow($tableBody, rowTemplate, entity);
        });
    }
    
    function toggleTables() {
        var $userRows = $('#usersToCreateTableBody tr');
        var $groupRows = $('#groupsToCreateTableBody tr');
        var $communityRows = $('#communitiesToCreateTableBody tr');

        $('#usersToCreate').toggle($userRows.size() > 0);
        $('#groupsToCreate').toggle($groupRows.size() > 0);
        $('#communitiesToCreate').toggle($communityRows.size() > 0);
        
        toggleCreateAllButton();
    }
    
    function toggleCreateAllButton(andOtherCreateButtons) {
        var show = false;
        $('#usersToCreateTableBody tr, #groupsToCreateTableBody tr, #communitiesToCreateTableBody tr')
            .each(function () {
                if (!$(this).data('created')) {
                    // show buttons if at least one row not marked as created
                    show = true;
                    return false;
                }
        });

        $('#batchCreateAllBtn').toggle(show);
        if (andOtherCreateButtons) {
            $('#addBatchUserLnk, #addBatchGroupLnk, #addBatchCommunityLnk').toggle(show);
        }
    }
    
    function getBatchUserRowTemplate() {
        return $('#userToCreateRowTemplate table tbody').html();
    }
    
    function getBatchGroupRowTemplate() {
        return $('#groupToCreateRowTemplate table tbody').html();
    }

    function getBatchCommunityRowTemplate() {
        return $('#communityToCreateRowTemplate table tbody').html();
    }

    $(document).on('click', '#addBatchUserLnk', function() {
        addBatchTableRow($('#usersToCreateTableBody'), getBatchUserRowTemplate(), {}); 
        toggleTables();
        return false;
    });
    
    $(document).on('click', '#addBatchGroupLnk', function() {
        var group = { uniqueName : "Group" + RS.randomAlphanumeric(4) }; 
        addBatchTableRow($('#groupsToCreateTableBody'), getBatchGroupRowTemplate(), group);
        toggleTables();
        return false;
    });

    $(document).on('click', '#addBatchCommunityLnk', function() {
        var community = { uniqueName : "Comm" + RS.randomAlphanumeric(4) }; 
        addBatchTableRow($('#communitiesToCreateTableBody'), getBatchCommunityRowTemplate(), community);
        toggleTables();
        return false;
    });

    $(document).on('click', '.removeBatchRowLnk', function() {
        $(this).parents("tr:first").remove();
        toggleTables();
        return false;
    });
    
    function addBatchTableRow($tableBody, rowTemplate, entity) {
        var rowHtml = Mustache.render(rowTemplate, entity);

        if (entity.role) {
            // selecting the role from option list
            rowHtml = rowHtml.replace('value="' + entity.role + '"', 'value="' + entity.role + '" selected ');
        }   
        $tableBody.append(rowHtml);
    }

    $(document).on('click', '#batchCreateAllBtn', function() {
        /* 
         * adding temporary submit button and clicking it. this is done because html
         * validation is only run after click on submit button within the form, but we
         * don't want a permanent button because it would be triggered after hitting Enter
         * on any field within a form.
         */
        $('<input type="submit">').hide().appendTo('#batchCreateForm').click().remove();
    });
    
    $(document).on('submit', '#batchCreateForm', function(e) {
        e.preventDefault();
        
        var toCreateData = getDataFromBatchTables();
        if (!toCreateData) {
            return;
        }
        
        var jqxhr = submitUsersAndGroups(toCreateData);
        addBatchCreationHandlers(jqxhr);
        RS.blockingProgressBar.show({
        	progressType:"rs-userBatchRegistration",
        	msg:"Starting user creation"} );
    });
    
    function getDataFromBatchTables() {
        var data = { parsedUsers : [], parsedGroups : [], parsedCommunities : [] };
        var seenUsernames = [];
        
        $('#usersToCreateTableBody tr').each(function(i, row) {
            if ($(row).data('created')) {
                return; 
            }
            
            var user = {};
            $(row).find(':input').each(function(i, input) {
               var fieldName = $(input).data('fieldname');
               user[fieldName] = $(input).val();
            });
            // skipping confirm password field on batch registration screen
            user.confirmPassword = user.password;
            data.parsedUsers.push(user);
            seenUsernames.push(user.username);
            
            // setting username on row element so it's easy to find later
            $(row).attr('data-username', user.username);
        });

        // check that usernames are not repeated, would cause problems with duplicated row identifiers
        var duplicates = findDuplicatedUsernames(seenUsernames);
        if (duplicates.length > 0) {
            applyDuplicatedUsernamesErrorMsg(duplicates);
            return;
        }
        
        $('#groupsToCreateTableBody tr').each(function(i, row) {
            if ($(row).data('created')) {
                return;
            }
            
            var group = {};
            group.uniqueName = $(row).data('uniquename');
            $(row).find(':input').each(function(i, input) {
               var fieldName = $(input).data('fieldname');
               if (fieldName === "otherMembers") {
                   group.otherMembers = $(input).val().split(',');
               } else {
                   group[fieldName] = $(input).val();
               }
            });
            data.parsedGroups.push(group);
        });

        $('#communitiesToCreateTableBody tr').each(function(i, row) {
            if ($(row).data('created')) {
                return;
            }
            
            var community = {};
            community.uniqueName = $(row).data('uniquename');
            $(row).find(':input').each(function(i, input) {
               var fieldName = $(input).data('fieldname');
               if (fieldName === "admins" || fieldName === "labGroups") {
                   community[fieldName] = $(input).val().split(',');
               } else {
                   community[fieldName] = $(input).val();
               }
            });
            data.parsedCommunities.push(community);
        });

        return data;
    }
    
    function submitUsersAndGroups(toCreateData) {
        
        RS.blockPage("Starting...");
        var jqxhr = RS.sendJsonPostRequestToUrl('/system/userRegistration/batchCreate', toCreateData);
        jqxhr.always(function() {
            RS.unblockPage();
        });
        
        clearServerMessageAreas();
        return jqxhr;
    }
    
    function findDuplicatedUsernames(seenUsernames) {
        var sortedUsernames = seenUsernames.sort();
        var duplicates = [];
        
        $.each(sortedUsernames, function(i, name) {
            if (name === sortedUsernames[i-1] && name !== duplicates[duplicates.length-1]) {
                duplicates.push(name);
            } 
        });
        return duplicates;
    }

    function addBatchCreationHandlers(jqxhr) {
        jqxhr.always(function() {
            RS.blockingProgressBar.hide();
            $('#csvUploadProgress').html('');
            $("button:contains('Ok')").click();
            $('#batchUploadUserDlg').dialog('close');
        });

        jqxhr.done(function(result) {
            var remainingMsgs;
            if (result.errorMsg) {
                remainingMsgs = applyServerMsgs(result.errorMsg.errorMessages, applyErrorMsg);
                displayServerMessages($("#batchServerErrorMsgs"), remainingMsgs);
            } 
            if (result.data) {
                remainingMsgs = applyServerMsgs(result.data, applySuccessMsg);
                displayServerMessages($("#batchServerSuccessMsgs"), remainingMsgs);
                toggleCreateAllButton(true);
            }
            
            if (result.errorMsg.errorMessages.length && result.data.length) {
                $().toastmessage('showNoticeToast', 'Batch registration complete with some errors');
            } else if (result.data.length) {
                $().toastmessage('showSuccessToast', 'Batch registration complete');
            }
        });

        jqxhr.fail(function(jqxhr, textStatus, errorThrown) {
            var msg = "Unhandled exception was thrown during Batch User Registration:<br />"
                + "Status: " + textStatus + "(" + jqxhr.status + ") - " + errorThrown;
            displayServerMessages($("#batchServerErrorMsgs"), [ msg ]);
            $().toastmessage('showErrorToast', 'Unexpected error during batch registration');
        });
    }

    function applyServerMsgs(serverMsgs, msgHandler) {
        var remainingMsgs = [];
        $.each(serverMsgs, function (i, msg) {
           var msgHandled = false;
           if (msg.startsWith("U.")) {
               msgHandled = msgHandler(msg.substring(2), userRowLocator);
           } else if (msg.startsWith("G.")) {
               msgHandled = msgHandler(msg.substring(2), groupRowLocator);
           } else if (msg.startsWith("C.")) {
               msgHandled = msgHandler(msg.substring(2), communityRowLocator);
           } 
           if (!msgHandled) {
               remainingMsgs.push(msg);
           }
        });
        return remainingMsgs;
    }
    
    function applyDuplicatedUsernamesErrorMsg(duplicatedUsernames) {
        
        $.each(duplicatedUsernames, function(i, username) {
            var $userRows = $('#usersToCreateTableBody tr').filter('[data-username="' + username + '"]');
            $userRows.each(function(i, row) {
                $(row).find('[data-fieldname="username"]').addClass('invalidField');
                addErrorMsgToStatusField($(row), 'Repeated username.');
            });
        });
    }

    /* returns true if message was handled */
    function applySuccessMsg(msg, rowLocator) {
        var msgParts = msg.split('.');
        var uniqueName = msgParts.shift();
        var message = msgParts.join('.');

        var $row = rowLocator(uniqueName);
        if ($row.length) {
            addSuccessMsgToStatusField($row, message);
            markRowAsCreated($row);
            return true;
        }
    }

    function applyErrorMsg(msg, rowLocator) {
        var msgParts = msg.split('.');
        var uniqueName = msgParts.shift();
        var fieldName = msgParts.shift();
        var message = msgParts.join('.');

        if (fieldName === "confirmPassword") {
            console.log('skipping confirmPassword validation error');
            return true;
        }
        
        var $row = rowLocator(uniqueName);
        if ($row.length) {
            addErrorMsgToStatusField($row, message);
            $row.find('[data-fieldname="' + fieldName + '"]').addClass('invalidField');
            return true; // true means message was handled
        }
    }

    function userRowLocator(username) {
        return $('#usersToCreateTableBody tr').filter('[data-username="' + username + '"]');
    }
    
    function groupRowLocator(uniqueGroupName) {
        return $('#groupsToCreateTableBody tr').filter('[data-uniquename="' + uniqueGroupName + '"]');
    }

    function communityRowLocator(uniqueCommunityName) {
        return $('#communitiesToCreateTableBody tr').filter('[data-uniquename="' + uniqueCommunityName + '"]');
    }
    
    function addErrorMsgToStatusField($row, msg) {
        $row.find('.batchCreateStatus').append('<span class="errorMsg">' + msg + ' </span>');
    }

    function addSuccessMsgToStatusField($row, msg) {
        $row.find('.batchCreateStatus').append('<span class="successMsg">' + msg + ' </span>');
    }

    function markRowAsCreated($row) {
        $row.addClass('batchRowCreated');
        $row.attr('data-created', 'true');
        $row.find('input, select').each(function() {
            var $this = $(this),
                thisText = $this.find('option:selected').text() || this.value,
                thisWidth = $this.css('width'),
                $newField = $('<span />');

            $newField.text(thisText);
            if (thisWidth) {
                $newField.css('width', thisWidth);
            }
            $newField.addClass('batchFieldCreated');
            $this.replaceWith($newField); 
        });
        $row.find('.removeBatchRowLnk').hide();
    }

    function displayServerMessages($msgContainer, msgs) {
        if (!msgs || !msgs.length) {
            return;
        }
        var safeMessages = '';
        $.each(msgs, function() {
            safeMessages += RS.escapeHtml(this) + '<br />';
        });
        $msgContainer.html(safeMessages + '<br />');
        $('#batchServerMessagesHeader').show();
    }
    
    function clearServerMessageAreas() {
        $('#batchServerMessagesHeader').hide();
        $('#batchServerErrorMsgs, #batchServerSuccessMsgs, .batchCreateStatus').html('');
        $('.invalidField').removeClass('invalidField');
    }

});

define(function() {

    console.info('maintenance_mod loading');

    function loadScheduledMaintenanceView() {
        $('#mainArea').empty();
    
        var jqxhr = $.get('/system/maintenance/ajax/view');
        
        jqxhr.done(function (resp) {
            $('#mainArea').html(resp);
            loadScheduledMaintenances();
    
            var timepickerOptions = { dateFormat: "yy-mm-dd" };
            $('#newStartDate, #newEndDate, #nextStartDate, #nextEndDate').datetimepicker(timepickerOptions);
    
            $('#nextMaintenanceForm').submit(updateMaintenance);
            $('#stopUserLogin').click(stopUserLogin);
            $('#finishActiveMaintenance').click(finishActiveMaintenance);
            
            $('#scheduleNewMaintenance').click(showScheduleNewMaintenanceDiv);
            $('#scheduleNewMaintenanceForm').submit(addNewMaintenance);
        });
        
        jqxhr.fail(function () {
             RS.ajaxFailed("Getting schedule downtime view", false, jqxhr);
        });
    }
    
    function loadScheduledMaintenances() {    
        var jqxhr = $.get('/system/maintenance/ajax/list');   
        jqxhr.done(function(maintArray) {
            var firstMaint = maintArray.shift();
            setNextMaintenance(firstMaint);
            setOtherMaintenances(maintArray);
            setOldMaintenances();
        });
        
        jqxhr.fail(function () {
             RS.ajaxFailed("Couldn't retrieve list of scheduled downtimes", false, jqxhr);
        });
    }
    
    function setNextMaintenance(nextMaint) {
        
        if (nextMaint === undefined) {
            $('#nextMaintenanceDetails').hide();
            $('#noFutureMaintenances').show();
            return;
        }
        
        $('#noFutureMaintenances').hide();
        $('#nextMaintenanceDetails').show();
        $('#nextMaintenanceDetails').data("id", nextMaint.id);
        
        $('#nextStartDate').val(nextMaint.formattedStartDate);
        $('#nextEndDate').val(nextMaint.formattedEndDate);
        $('#nextMessage').val(nextMaint.message);
        
        var maintenanceActive = nextMaint.activeNow;
        $('#nextMaintenanceHeader').toggle(!maintenanceActive);
        $('#activeMaintenanceHeader').toggle(maintenanceActive);
        $('#nextStartDate').prop('disabled', maintenanceActive);
    
        var userCanLoginNow = nextMaint.canUserLoginNow; 
        $('#usersCanLoginMsg').toggle(userCanLoginNow);
        $('#usersCannotLoginMsg').toggle(!userCanLoginNow);
        if (userCanLoginNow) {
            $('#usersCanLoginUntil').empty().append(nextMaint.formattedStopUserLoginDate);
        }
        
        $('#stopUserLogin').toggle(userCanLoginNow);
        $('#finishActiveMaintenance').toggle(maintenanceActive);
        $('#deleteNextMaintenance').toggle(!maintenanceActive);
        $('#deleteNextMaintenance').data('id', nextMaint.id);
        
    }
    
    function setOtherMaintenances(otherMaintenances) { 
        $('#otherMaintenances').toggle(otherMaintenances.length > 0);
        $('#otherMaintenancesTableBody').empty();
        
        var otherMaintenanceRowTemplate = $('#otherMaintenanceRowTemplate').html();
        $.each(otherMaintenances, function(i, maint) {
            var otherMaintenanceRowHtml = Mustache.render(otherMaintenanceRowTemplate, {maint: maint});
            $('#otherMaintenancesTableBody').append(otherMaintenanceRowHtml);
        });
    }
    
    function setOldMaintenances() {
    	 var jqxhr = $.get('/system/maintenance/ajax/expired/list');
    	 jqxhr.done(function(maintArray) {
    		 $('#oldMaintenances').toggle(maintArray.length > 0);
    	     $('#oldMaintenancesTableBody').empty();	        
    	     var oldMaintenanceRowTemplate = $('#otherMaintenanceRowTemplate').html();
    	      $.each(maintArray, function(i, maint) {
    	          var otherMaintenanceRowHtml = Mustache.render(oldMaintenanceRowTemplate, {maint: maint});
    	          $('#oldMaintenancesTableBody').append(otherMaintenanceRowHtml);
    	      });
         });       
    }
    
    function checkDatesAreValid(startDate, endDate) {
        if (startDate < new Date()) {
            apprise('Start date can\'t be set in the past');
            RS.focusAppriseDialog();
            return false;
        }
        if (!(startDate < endDate)) {
          apprise('End date can\'t be earlier than start date');
          RS.focusAppriseDialog();
          return false;
        }
        
        return true;
    }
    
    function addNewMaintenance() {
        
        var startDate = $('#newStartDate').datetimepicker('getDate'),
            endDate = $('#newEndDate').datetimepicker('getDate');
    
        if (!checkDatesAreValid(startDate, endDate)) {
            return false; 
        }
        
        var data = {
            startDate: startDate.toISOString(),
            endDate: endDate.toISOString(),
            message: $('#newMessage').val()
        };
        
        $.ajax({
            url: "/system/maintenance/ajax/create",
            type: "POST",
            data: JSON.stringify(data),
            contentType:"application/json",
            dataType: "json",
            success: function (result) {
            	 $().toastmessage('showSuccessToast', 'Scheduled new downtime');
                 loadScheduledMaintenances();
            },
            error: function (xhr, ajaxOptions, thrownError) {
            	 RS.ajaxFailed("Couldn't schedule new downtime", false, xhr);
            }
        });
//        var jqxrh = $.post('', data);
//        
//        jqxrh.done(function() {
//           
//        });
//        
//        jqxrh.fail(function () {
//            
//        });
        
        /* so html form submit is not called */
        return false;
    }
    
    function updateMaintenance() {
        
        var startDate = $('#nextStartDate').datetimepicker('getDate'),
            endDate = $('#nextEndDate').datetimepicker('getDate');
        
        if (!checkDatesAreValid(startDate, endDate)) {
            return false; 
        }
        
        var data = {
            id: $('#nextMaintenanceDetails').data("id"),
            startDate: startDate.toISOString(),
            endDate: endDate.toISOString(),
            message: $('#nextMessage').val()
        };
        $.ajax({
            url: "/system/maintenance/ajax/update",
            type: "POST",
            data: JSON.stringify(data),
            contentType:"application/json",
            success: function (result) {
            	 $().toastmessage('showSuccessToast', 'Downtime details updated');
                 loadScheduledMaintenances();
            },
            error: function (xhr, ajaxOptions, thrownError) {
            	  RS.ajaxFailed("Couldn't update scheduled downtime", false, xhr);
            }
        });
        
        /* so html form submit is not called */
        return false;
    }
    
    function stopUserLogin() {
    
        var jqxrh = $.post('/system/maintenance/ajax/stopUserLogin', {});
        
        jqxrh.done(function() {
            $().toastmessage('showSuccessToast', 'Users no longer allowed to login');
            loadScheduledMaintenances();
        });
        
        jqxrh.fail(function () {
             RS.ajaxFailed("Couldn't stop users login", false, jqxrh);
        });
    }
    
    
    function finishActiveMaintenance() {
    
        var jqxrh = $.post('/system/maintenance/ajax/finishNow', {});
        
        jqxrh.done(function() {
            $().toastmessage('showSuccessToast', 'Downtime finished');
            loadScheduledMaintenances();
        });
        
        jqxrh.fail(function () {
             RS.ajaxFailed("Couldn't finish active downtime", false, jqxrh);
        });
    }
    
    function deleteMaintenance() {
    
        var data = { id: $(this).data('id') };
        var jqxrh = $.post('/system/maintenance/ajax/delete', data);
        
        jqxrh.done(function(result) {
            $().toastmessage('showSuccessToast', 'Scheduled downtime deleted');
            loadScheduledMaintenances();
        });
        
        jqxrh.fail(function () {
             RS.ajaxFailed("Couldn't delete selected downtime", false, jqxrh);
        });
    }
    
    function showScheduleNewMaintenanceDiv() {
        $('#scheduleNewMaintenance').hide();
        $('#scheduleNewMaintenanceDiv').show();
    }
    
    $(document).ready(function() {
        $(document).on('click', '#scheduleMaintenanceLink', loadScheduledMaintenanceView);
        $(document).on('click', '.deleteMaintenanceButton', deleteMaintenance);
        
        console.info('maintenance_mod document.ready executed');
    });
    
});
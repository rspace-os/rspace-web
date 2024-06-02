
<%@ include file="/common/taglibs.jsp"%>
<head>
<title><spring:message code="maintenance.mode.title" /></title>
</head>

	<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
	<div class="row">
    	<axt:biggerLogo/>
    	<div style="text-align:center; margin-top:46px;">
    	    <h2 class="form-signup-heading">Maintenance Mode</h2>
        </div>
    </div>
    <div style="max-width:550px;margin: 0 auto;margin-top:30px;text-align:center;">
		Scheduled maintenance is in progress now. Please try again later.
		<br />
		<br />
		This page will check the maintenance status again in <span class="refreshTimer">30</span> seconds, or you can 
		<br />
		<a href="/login">check the status now</a>
	</div>
	
	<script>
	
	    var statusCheckInterval = 30;
	    var refreshInterval = 1;
	    var refreshCounter = 0;
	    
    	function checkIfMaintenanceOver() {
    	    refreshCounter += refreshInterval;
    	    if (refreshCounter == statusCheckInterval) {
		        var jqxhr = $.get("/public/maintenanceStatus");
		        
		        jqxhr.done(function(maintenanceStatus) {
		           if (maintenanceStatus === 'No maintenance') {
		               console.log('maintenance over, navigatin to login page')
		               window.location.href = "/login";
		           }
		        });
		        jqxhr.always(function() {
		            refreshCounter = 0;
		        })
    	    }
    	    if (refreshCounter <= statusCheckInterval) {
    	        $(".refreshTimer").text(statusCheckInterval - refreshCounter);
    	    }
	     }

    	setInterval(checkIfMaintenanceOver, refreshInterval * 1000);
    </script>
	
</div>
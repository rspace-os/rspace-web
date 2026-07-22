
<%@ include file="/common/taglibs.jsp"%>
<head>
<title><spring:message code="maintenance.mode.title" /></title>
</head>

	<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
	<div class="row">
    	<axt:biggerLogo/>
    	<div style="text-align:center; margin-top:46px;">
    	    <h2 class="form-signup-heading"><spring:message code="maintenance.mode.heading"/></h2>
        </div>
    </div>
    <div style="max-width:550px;margin: 0 auto;margin-top:30px;text-align:center;">
		<spring:message code="maintenance.mode.inProgressNotice"/>
		<br />
		<br />
		<spring:message code="maintenance.mode.checkAgainNotice">
		  <spring:argument value='<span class="refreshTimer">30</span>'/>
		</spring:message>
		<br />
		<a href="/login"><spring:message code="maintenance.mode.checkStatusLink"/></a>
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
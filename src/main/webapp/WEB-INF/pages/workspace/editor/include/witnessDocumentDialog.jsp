<%@ include file="/common/taglibs.jsp"%>

<link rel="stylesheet" href="<c:url value='/styles/signingProcess.css'/>" />

<script src="<c:url value='/scripts/pages/signingProcess.js'/>"></script>

<div id="witnessDocumentDialog" style="display: none;">
    <fieldset>
    	<div id="optionSection">
    		<fieldset>
        	 	<legend> Choose an option: </legend>
            	 	<p><label> <input class="option" type=radio name=option value="true" checked="checked"> I verify that this document is an accurate representation of work performed by ${structuredDocument.owner.username} at the time specified in the system records and / or I agree that the document is complete and should be locked to prevent further editing.</label></p>
            	 	<p><label> <input class="option" type=radio name=option value="false"> I decline to add a witness statement at this time for the following reason: </label></p>
        	 		<input id="declineMsgInput" name="declineMsg" type="text" size="45" style="display:none">
    	 	</fieldset>
    	</div>
      	<div id="confirmationWitnessSection" class="confirmation">
      		<fieldset>
        	 	<legend> Confirmation process </legend>
        		<p>
            		<span class="ui-icon ui-icon-info"></span>
            		<strong class = "witnessConfirmMsg"> This document will be witnessed.</strong>
            		<strong class = "witnessDenyMsg" style="display:none"> This document will not be witnessed.</strong>
        		</p>
            	<label>Password: <input class="passWitnessInput" type="password" name="userpass"><br /></label>
        	</fieldset>
      	</div>
    </fieldset>
</div>

<%@ include file="/common/taglibs.jsp"%>

<link rel="stylesheet" href="<rst:assetUrl value='/styles/signingProcess.css'/>" />

<script src="<rst:assetUrl value='/scripts/pages/signingProcess.js'/>"></script>

<div id="witnessDocumentDialog" style="display: none;">
    <fieldset>
	<div id="optionSection">
		<fieldset>
		<legend> <spring:message code="document.sign.optionChoice.label"/> </legend>
		<p><label> <input class="option" type=radio name=option value="true" checked="checked"> <spring:message code="document.sign.optionChoice.workRecordAffirmation" arguments="${structuredDocument.owner.username}"/></label></p>
		<p><label> <input class="option" type=radio name=option value="false"> <spring:message code="document.sign.optionChoice.declineReason"/> </label></p>
			<input id="declineMsgInput" name="declineMsg" type="text" size="45" style="display:none">
		</fieldset>
	</div>
	<div id="confirmationWitnessSection" class="confirmation">
		<fieldset>
		<legend> <spring:message code="document.sign.confirm.label"/> </legend>
		<p>
		<span class="ui-icon ui-icon-info"></span>
		<strong class = "witnessConfirmMsg"> <spring:message code="document.sign.confirm.willBeWitnessed"/></strong>
		<strong class = "witnessDenyMsg" style="display:none"> <spring:message code="document.sign.confirm.willNotBeWitnessed"/></strong>
		</p>
	<label><spring:message code="document.sign.password.label"/> <input class="passWitnessInput" type="password" name="userpass"><br /></label>
	</fieldset>
	</div>
    </fieldset>
</div>

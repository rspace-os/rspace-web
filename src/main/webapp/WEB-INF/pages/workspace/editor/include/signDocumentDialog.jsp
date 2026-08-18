<%@ include file="/common/taglibs.jsp"%>
<link rel="stylesheet" href="<rst:assetUrl value='/styles/signingProcess.css'/>" />
<script src="<rst:assetUrl value='/scripts/pages/signingProcess.js'/>"></script>
<div id="signDocumentDialog" style="display: none;">
<fieldset>
	<div id="statementSection">
		<fieldset>
			<legend> <spring:message code="document.sign.statement.chooseLabel"/> </legend>
				<p><label> <input class="statement" type=radio name=size value="statement1" checked="checked">
				  <spring:message code="document.sign.statement.workRecordAffirmation"/>
				</label></p>
				<p><label> <input class="statement" type=radio name=size value="statement2">
				 <spring:message code="document.sign.statement.finalVersionAffirmation"/> </label></p>
		</fieldset>
	</div>
	<div id="witnessesSection">
		<fieldset>
			<legend><spring:message code="document.sign.witness.label"/> </legend>
				<p id="witnessesInfo"><strong><spring:message code="document.sign.noWitness.shareDocumentHelp"/> </strong></p>
				<p id="witnessesList"> </p>
		</fieldset>
	</div>
	<div id="confirmationSection" class="confirmation">
		<fieldset>
			<legend><spring:message code="document.sign.confirm.label"/>  </legend>
				<p><span class="ui-icon ui-icon-info"></span><strong>
				 <spring:message code="document.sign.confirm.lockWarning"/></strong></p>
			<label><spring:message code="document.sign.password.label"/>
						<input class="passSignInput" type="password" name="userpass"><br />
					</label>
	</fieldset>
	</div>
</fieldset>
</div>
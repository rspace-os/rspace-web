<%@ include file="/common/taglibs.jsp"%>
<link rel="stylesheet" href="<c:url value='/styles/signingProcess.css'/>" />
<script src="<c:url value='/scripts/pages/signingProcess.js'/>"></script>
<div id="signDocumentDialog" style="display: none;">
<fieldset>
	<div id="statementSection">
		<fieldset>
	 		<legend> <spring:message code="doc.sign.statement.choose.label"/> </legend>
	 			<p><label> <input class="statement" type=radio name=size value="statement1" checked="checked">
	 			  <spring:message code="doc.sign.statement.option1"/> 
	 			</label></p>
	 			<p><label> <input class="statement" type=radio name=size value="statement2"> 
	 			 <spring:message code="doc.sign.statement.option2"/> </label></p>
	 	</fieldset>
	</div>
	<div id="witnessesSection">
		<fieldset>
			<legend><spring:message code="doc.sign.witness.label"/> </legend>
				<p id="witnessesInfo"><strong><spring:message code="doc.sign.nowitness.msg"/> </strong></p>
				<p id="witnessesList"> </p>
		</fieldset>
	</div>
  	<div id="confirmationSection" class="confirmation">
  		<fieldset>
	 		<legend><spring:message code="doc.sign.confirm.label"/>  </legend>
				<p><span class="ui-icon ui-icon-info"></span><strong> 
				 <spring:message code="doc.sign.confirm.msg"/></strong></p>
    			<label><spring:message code="doc.sign.password.label"/>
						<input class="passSignInput" type="password" name="userpass"><br />
					</label>
    	</fieldset>
  	</div>
</fieldset>
</div>
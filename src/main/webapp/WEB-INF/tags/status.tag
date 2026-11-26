<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>

<%-- <%@ attribute name="editStatus" required="false" type="com.researchspace.model.EditStatus" %>
<%@ attribute name="record" required="false" type="com.researchspace.model.record.StructuredDocument" %> --%>

<div id="status">

		<div id="signedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.signed"/> </span>
			<img src="/images/status/signed.png" width="21" height="19" alt="" aria-hidden="true">
		</div>

        <div id="signedAwaitingWitnessStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.signedAwaitingWitness"/> </span>
			<img src="/images/status/signed.png" width="21" height="19" alt="" aria-hidden="true">
		</div>

        <div id="signedWitnessesDeclinedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.signedWitnessesDeclined"/> </span>
			<img src="/images/status/signed.png" width="21" height="19" alt="" aria-hidden="true">
		</div>

		<div id="witnessedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.signedAndWitnessed"/> </span>
			<img src="/images/status/witnessed.png" width="21" height="19" alt="" aria-hidden="true">
		</div>

		<div id="editingStatus" class="state"  style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.editing"/> </span>
			<img src="/images/status/editing.png" width="21" height="19" alt="" aria-hidden="true">
		</div>

		<div id="viewGreenStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.view"/> </span>
			<img src="/images/status/viewGreen.png" width="21" height="19" alt="" aria-hidden="true">
		</div>

		<div id="viewAmberStatus" class="state"  style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.viewSomeoneElseEditing"/> </span>
			<img src="/images/status/viewAmber.png" width="21" height="19" alt="" aria-hidden="true">
		</div>

		<div id="viewAmberStatusReadPermission" class="state"  style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.viewReadPermissionOnly"/> </span>
			<img src="/images/status/viewAmber.png" width="21" height="19" alt="" aria-hidden="true">
		</div>

		<div id="viewRedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.viewNotEditable"/> </span>
			<img src="/images/status/viewRed.png" width="21" height="19" alt="" aria-hidden="true">
		</div>

		<div id="sealedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.sealed"/> </span>
			<img src="/images/status/sealed.png" width="21" height="19" alt="" aria-hidden="true">
		</div>
</div>
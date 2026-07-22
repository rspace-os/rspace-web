<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>

<%-- <%@ attribute name="editStatus" required="false" type="com.researchspace.model.EditStatus" %>
<%@ attribute name="record" required="false" type="com.researchspace.model.record.StructuredDocument" %> --%>

<div id="status">

		<div id="signedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.signed"/> </span>
			<div  style="background-image: url(/images/status/signed.png); height: 19px; width: 21px;"></div>
		</div>

        <div id="signedAwaitingWitnessStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.signedAwaitingWitness"/> </span>
			<div  style="background-image: url(/images/status/signed.png); height: 19px; width: 21px;"></div>
		</div>

        <div id="signedWitnessesDeclinedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.signedWitnessesDeclined"/> </span>
			<div  style="background-image: url(/images/status/signed.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="witnessedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.signedAndWitnessed"/> </span>
			<div style="background-image: url(/images/status/witnessed.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="editingStatus" class="state"  style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.editing"/> </span>
			<div style="background-image: url(/images/status/editing.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="viewGreenStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.view"/> </span>
			<div style="background-image: url(/images/status/viewGreen.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="viewAmberStatus" class="state"  style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.viewSomeoneElseEditing"/> </span>
			<div style="background-image: url(/images/status/viewAmber.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="viewAmberStatusReadPermission" class="state"  style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.viewReadPermissionOnly"/> </span>
			<div style="background-image: url(/images/status/viewAmber.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="viewRedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.viewNotEditable"/> </span>
			<div style="background-image: url(/images/status/viewRed.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="sealedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> <spring:message code="record.status.sealed"/> </span>
			<div style="background-image: url(/images/status/sealed.png); height: 19px; width: 21px;"></div>
		</div>
</div>
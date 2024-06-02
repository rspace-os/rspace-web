<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%-- <%@ attribute name="editStatus" required="false" type="com.researchspace.model.EditStatus" %>
<%@ attribute name="record" required="false" type="com.researchspace.model.record.StructuredDocument" %> --%>

<div id="status">
	
		<div id="signedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> Signed </span>
			<div  style="background-image: url(/images/status/signed.png); height: 19px; width: 21px;"></div>
		</div>

        <div id="signedAwaitingWitnessStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> Signed, awaiting witness </span>
			<div  style="background-image: url(/images/status/signed.png); height: 19px; width: 21px;"></div>
		</div>

        <div id="signedWitnessesDeclinedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> Signed, all witnesses declined </span>
			<div  style="background-image: url(/images/status/signed.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="witnessedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> Signed and witnessed </span>
			<div style="background-image: url(/images/status/witnessed.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="editingStatus" class="state"  style="display:none">
			<span style="font-weight:bold;"> Editing </span>
			<div style="background-image: url(/images/status/editing.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="viewGreenStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> View </span>
			<div style="background-image: url(/images/status/viewGreen.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="viewAmberStatus" class="state"  style="display:none">
			<span style="font-weight:bold;"> View (someone else is editing) </span>
			<div style="background-image: url(/images/status/viewAmber.png); height: 19px; width: 21px;"></div>
		</div>
		
		<div id="viewAmberStatusReadPermission" class="state"  style="display:none">
			<span style="font-weight:bold;"> View (read permission only) </span>
			<div style="background-image: url(/images/status/viewAmber.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="viewRedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> View (Not editable) </span>
			<div style="background-image: url(/images/status/viewRed.png); height: 19px; width: 21px;"></div>
		</div>

		<div id="sealedStatus" class="state" style="display:none">
			<span style="font-weight:bold;"> Sealed </span>
			<div style="background-image: url(/images/status/sealed.png); height: 19px; width: 21px;"></div>
		</div>
</div>
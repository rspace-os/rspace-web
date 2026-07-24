<%-- 
	Div element for controller pages that will show an editable record name and description and dialog,
	 and will store on the server after editing via Ajax.
	 
	 By convention clients need to have a controller request URL with
	 ' {postURL}/rename' and '{postURL}/description' .
--%>
<%-- The name to display. --%>
<%@ attribute name="editInfo" required="true" type="com.researchspace.model.record.EditInfo" %>
<%--An Ajax URL, shouldn't redirect to new page. This is the 'root' of the --%>
<%@ attribute name="postURL" required="true" type="java.lang.String" %>
<%@ attribute name="editStatus" required="false" type="com.researchspace.model.EditStatus" %>
<%@ attribute name="itemType" required="false" type="java.lang.String" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>
<script>
function getFormName () {
	return $('#documentName').find('.recordName').text();
}

</script>
<div id="documentName" class="breadcrumb">
  <spring:message code="dialogs.renameRecord.label.name"/> <span class="recordName" tabindex="0">${editInfo.name}</span>
</div>
<div id="renameRecordDirect" style="display:none">
	<label><spring:message code="dialogs.renameRecord.label.newName"/>
		<input id="nameFieldDirect" type="text" width="30" value="${editInfo.name}">
	</label>
</div>
<c:set value="false" var="canEdit"></c:set>
<c:if test="${editStatus  eq 'EDIT_MODE'}">
   <c:set value="true" var="canEdit"></c:set>
</c:if>
<script>
	var isEditable=${canEdit};
	var itemType="${itemType}";

	$(document).ready(function() {
		var renameRecordButtons = {};
		renameRecordButtons[RS.msg("legacyjs.common.cancel")] = function (){
			$(this).dialog('close');
		};
		renameRecordButtons[RS.msg("legacyjs.core.action.rename")] = function (){
			var newName=$('#nameFieldDirect').val();
			if(newName == ""){
				apprise(RS.msg("legacyjs.core.renameForm.nameRequired"));
				RS.focusAppriseDialog(true);
				return;
			}
			$(this).dialog('close');

			var data={
					recordId:recordId,
					newName:newName
			};
			$.post("${postURL}/rename",data,function (data){
				if(data.errorMsg != null) {
					apprise(getValidationErrorString(data.errorMsg));
				} else {
					$('.recordName').text(newName);
					$('#nameInBreadcrumb').val(newName +" ");
					if(itemType === 'RECORD') {
						var oldTitle = $(document).prop('title');
						var newTitle = oldTitle.replace(/^[^\|]+/, newName + " ");
						$(document).prop('title', newTitle);
					}
				}
			});
		};
		$('#renameRecordDirect').dialog({
			modal : true,
			autoOpen:false,
			title: RS.msg("legacyjs.core.action.rename"),
			buttons : renameRecordButtons
		});
		RS.onEnterSubmitJQueryUIDialog('#renameRecordDirect');
	});
	$('.recordName').click(function (){
		if (editable == 'EDIT_MODE' || editable == 'VIEW_MODE'){
			if(isEditable)
				$('#renameRecordDirect').dialog('open');
		}
	});
</script>
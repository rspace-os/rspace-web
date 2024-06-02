<%@ include file="/common/taglibs.jsp"%>
<!-- Loading TinyMCE -->
<script src="<c:url value='/scripts/tinymce/tinymce516/jquery.tinymce.min.js'/>"></script>
<script src="<c:url value='/scripts/tinymce/tinymce516/tinymce.min.js'/>"></script>
<script src="<c:url value='/scripts/pages/workspace/editor/form.js'/>"></script>
<script src="<c:url value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>
<link rel="stylesheet" href="<c:url value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/structuredDocument.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/tinymce_rs.css'/>" />

<title><spring:message code="menu.templates.formEditor"/></title>
<head>
	<meta name="heading" content="Form"/>
</head>
<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />
<jsp:include page="formMustacheTemplates.html" />
<p style="visibility:hidden;">Text</p>

<axt:toolbar hideSearch="true">
	<jsp:attribute name="menu">
		<div class="container-fluid toolbar-small">
			<ul class="nav navbar-nav" style="float:right;"> 
	      <li>
					<img id="iconImgSrc" src="/image/getIconImage/${template.iconId}" alt="Icon Image" height="32" width="32"  style='display:inline'/>
				</li>
			</ul>

			<rs:chkTmplatePermssns form="${template}" action="WRITE">
			<ul class="nav navbar-nav">
				<li>
					<button id="changeFormIconButton">Change Icon</button>
				</li>
			</ul>
			</rs:chkTmplatePermssns>
			
			<ul class="nav navbar-nav">
				<li>
					<button id='save' title='<fmt:message key="template.save.desc"/>'>
						Save and Close
					</button>
				</li>
				<li>
					<button id='add' title="Add field">Add Field</button>
				</li>
				<li>					
					<button id='changeOrder' title='Reorder fields'>Reorder Fields</button>
				</li>
			</ul>

			<%--Publishing just relevant to a versioned template, not one being edited --%> 
			<c:if test="${template.temporary eq false }">    
			<ul class="nav navbar-nav">
				<li>
					<%--Hide this option unless template is published --%> 
					<button  title='<fmt:message key="template.unpublish.desc"/>' class="publishAction"
						<c:if test="${template.publishedAndVisible == false}"> style='display:none' </c:if>
						id='unpublish' name="publish">Unpublish</button>
				</li>
					
				<li>
					<%--Hide this option if template is published, or it has no fields --%>
					<button title='<fmt:message key="template.publish.desc"/>' class="publishAction"
						<c:if test="${ empty template.fieldForms || template.publishedAndVisible==true}"> style='display:none' </c:if>
						id='publish' name="publish">Publish</button>
				</li>
			</ul>
			</c:if>
			
			<c:if test="${template.temporary eq true }">
				<button id='abandon' title='<fmt:message key="template.abandon.version"/>'>Revert</button>
				<button id='update' title='<fmt:message key="template.commit.version"/>'>Update</button>
			</c:if>
		</div>
	</jsp:attribute>
</axt:toolbar>

<fmt:bundle basename="bundles.admin.admin">
	<div id="changeFormIconDialog" style="display: none;">
		<form id="formIcon" enctype="multipart/form-data" method="post" style='padding: 5px;'>
			<div id= "msgAreaImage"></div>
			<table style="margin: 0px;">
				<tr>
					<td style="white-space:normal;">
						<label><fmt:message key="dialogs.changeFormIcon.instruction"></fmt:message><br><br>
							<input id="newIconImage" type='file' name='filex'
							data-post='${saveImgURL}' data-width='32' data-height='32'/>
						</label>
					</td>
				</tr>
				<tr>
					<td>
						<output id="imagePreview"></output> 
						<%-- <input type="hidden" name="form_id" value="${template.id}" /> --%>
					</td>
				</tr>
			</table>
		</form>
	</div>
</fmt:bundle>

<div id="multiUseBar">
	<c:url value="/workspace/editor/form/ajax" var="renameURL"/>
	<axt:renameForm postURL="${renameURL}" editInfo="${template.editInfo}"></axt:renameForm>
	<div id="tagSlot">
		<label>Tags:
			<input type="text" id="tmp_tag" value="${template.tags}" maxlength="100" size="40" />
		</label>
	</div>
</div>

<%--Status of document while editing is not important --%>
<c:if test="${template.temporary eq false }">
	<div class="publishStatusLine" style='text-align:right;margin:10px;width:98%;'> Status - <span id="publishingStatus">${template.publishingState}</span></div>
</c:if>

<div id="messageToolbar" style="padding:5px;text-align: center;" >
	<span class="messagebox" id="noMessages" >
	<c:if test="${template.temporary eq true }">
		<fmt:message key="form.update.explanation"/>
	</c:if>
	</span>
</div>

<table id="structuredDocument" name="mainTable" style="width:100%"> 
	<tr class="table_cell">
		<td class="field-name" style="font-weight:normal;font-size:1em;width:15%;text-align:left;padding-left:10px;">Field Names</td>
		<td class="field-value" style="font-weight:normal;font-size:1em;text-align:left;padding-left:10px;">Fields</td>
	</tr>
	<input type="hidden" name="timestamp" value="${template.modificationDate}"/>
	 <c:forEach var="field" items="${template.fieldForms}">
	 	<c:choose>
	 		<c:when test="${field['class'].name eq 'com.researchspace.model.field.NumberFieldForm'}">
				 <%@ include file="include/numberFieldForm.jsp"%>
			</c:when>
			<c:when test="${field['class'].name eq 'com.researchspace.model.field.StringFieldForm'}">
				 <%@ include file="include/stringFieldForm.jsp"%>
			</c:when>
			<c:when test="${field['class'].name eq 'com.researchspace.model.field.TextFieldForm'}">
				 <%@ include file="include/textFieldForm.jsp"%>
			</c:when>
			<c:when test="${field['class'].name eq 'com.researchspace.model.field.RadioFieldForm'}">
				 <%@ include file="include/radioFieldForm.jsp"%>
			</c:when>
			<c:when test="${field['class'].name eq 'com.researchspace.model.field.ChoiceFieldForm'}">
				 <%@ include file="include/choiceFieldForm.jsp"%>
			</c:when>
			<c:when test="${field['class'].name eq 'com.researchspace.model.field.DateFieldForm'}">
				 <%@ include file="include/dateFieldForm.jsp"%>
			</c:when>
				<c:when test="${field['class'].name eq 'com.researchspace.model.field.TimeFieldForm'}">
				 <%@ include file="include/timeFieldForm.jsp"%>
			</c:when>
	 	</c:choose>
       </c:forEach>
</table>

<c:if test="${editStatus eq 'EDIT_MODE'}">
	<!-- a jquery-ui dialog, initially closed -->
	<div id="field-editor" title="Field Editor">
		<form id="field-editor-form">
			<input type="hidden" name="fieldForm" value="fieldForm">
			<div id="errorSummary" class="error errorDialog"></div>
			<fieldset id="field_selector">
				<table>
					<tr>
						<td><label for="fieldEditorSelect">Field Type</label></td>
						<td><select name="fieldType" id="fieldEditorSelect">
								<option><spring:message code="form.create.fieldTypeSelect" /></option>
								<c:forEach var="fieldKey" items="${fieldKeys}">
									<option>${fieldKey}</option>
								</c:forEach>
						</select></td>
					</tr>
				</table>
			</fieldset>
			<div id="field-loading">
				<img src="<c:url value ='/images/field-editor-load.gif'/>" />
			</div>
		</form>
	</div>
</c:if>
<%@ include file="include/templatePublishShareDlg.jsp"%>

<div id="reorderFieldsDialog" title="Order Form Fields" style="display: none">
	<p> Select a field for moving options and use the arrow keys to change position.<p>
	<fieldset>
		<legend>Fields</legend>
		<table id="reorderFieldTable"></table>
	</fieldset>
</div>

<script>
	var editable = '${editStatus}';
	var editor = '${editor}';
	var wasAutosaved = false;
	var modificationDate = '${modificationDate}';
	var recordId = "${template.id}";
</script>
<%@ include file="/common/taglibs.jsp"%>
<!-- Loading TinyMCE -->
<script src="<rst:assetUrl value='/scripts/tinymce/tinymce5109/dompurify.min.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/tinymce/tinymce5109/jquery.tinymce.min.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/tinymce/tinymce5109/tinymce.min.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/pages/workspace/editor/form.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>
<link rel="stylesheet" href="<rst:assetUrl value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.css'/>" />
<link rel="stylesheet" href="<rst:assetUrl value='/styles/structuredDocument.css'/>" />
<link rel="stylesheet" href="<rst:assetUrl value='/styles/tinymce_rs.css'/>" />

<title><spring:message code="menu.templates.formEditor"/></title>
<head>
	<meta name="heading" content="<spring:message code='form.editor.metaHeading'/>"/>
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
					<button id="changeFormIconButton"><spring:message code="form.editor.changeIconButton"/></button>
				</li>
			</ul>
			</rs:chkTmplatePermssns>

			<ul class="nav navbar-nav">
				<li>
					<button id='save' title='<spring:message code="template.save.description"/>'>
						<spring:message code="form.editor.saveAndCloseButton"/>
					</button>
				</li>
				<li>
					<button id='add' title="<spring:message code='form.editor.addFieldTitle'/>"><spring:message code="form.editor.addFieldButton"/></button>
				</li>
				<li>
					<button id='changeOrder' title='<spring:message code="form.editor.reorderFieldsTitle"/>'><spring:message code="form.editor.reorderFieldsButton"/></button>
				</li>
			</ul>

			<%--Publishing just relevant to a versioned template, not one being edited --%>
			<c:if test="${template.temporary eq false }">
			<ul class="nav navbar-nav">
				<li>
					<%--Hide this option unless template is published --%>
					<button  title='<spring:message code="template.unpublish.description"/>' class="publishAction"
						<c:if test="${template.publishedAndVisible == false}"> style='display:none' </c:if>
						id='unpublish' name="publish"><spring:message code="form.editor.unpublishLabel"/></button>
				</li>

				<li>
					<%--Hide this option if template is published, or it has no fields --%>
					<button title='<spring:message code="template.publish.description"/>' class="publishAction"
						<c:if test="${ empty template.fieldForms || template.publishedAndVisible==true}"> style='display:none' </c:if>
						id='publish' name="publish"><spring:message code="common:actions.publish"/></button>
				</li>
			</ul>
			</c:if>

			<c:if test="${template.temporary eq true }">
				<button id='abandon' title='<spring:message code="template.abandon.version"/>'><spring:message code="form.editor.revertButton"/></button>
				<button id='update' title='<spring:message code="template.commit.version"/>'><spring:message code="form.editor.updateButton"/></button>
			</c:if>
		</div>
	</jsp:attribute>
</axt:toolbar>

<div id="changeFormIconDialog" style="display: none;">
	<form id="formIcon" enctype="multipart/form-data" method="post" style='padding: 5px;'>
		<div id= "msgAreaImage"></div>
		<table style="margin: 0px;">
			<tr>
				<td style="white-space:normal;">
					<label><spring:message code="dialogs.changeFormIcon.instruction"/><br><br>
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

<div id="multiUseBar">
	<c:url value="/workspace/editor/form/ajax" var="renameURL"/>
	<axt:renameForm postURL="${renameURL}" editInfo="${template.editInfo}"></axt:renameForm>
	<div id="tagSlot">
		<label><spring:message code="form.editor.tagsLabel"/>
			<input type="text" id="tmp_tag" value="${template.tags}" maxlength="100" size="40" />
		</label>
	</div>
</div>

<%--Status of document while editing is not important --%>
<c:if test="${template.temporary eq false }">
	<div class="publishStatusLine" style='text-align:right;margin:10px;width:98%;'> <spring:message code="form.editor.statusPrefix"/> <span id="publishingStatus">${template.publishingState}</span></div>
</c:if>

<div id="messageToolbar" style="padding:5px;text-align: center;" >
	<span class="messagebox" id="noMessages" >
	<c:if test="${template.temporary eq true }">
		<spring:message code="form.update.explanation"/>
	</c:if>
	</span>
</div>

<table id="structuredDocument" name="mainTable" style="width:100%"> 
	<tr class="table_cell">
		<td class="field-name" style="font-weight:normal;font-size:1em;width:15%;text-align:left;padding-left:10px;"><spring:message code="form.editor.fieldNamesHeader"/></td>
		<td class="field-value" style="font-weight:normal;font-size:1em;text-align:left;padding-left:10px;"><spring:message code="form.editor.fieldsHeader"/></td>
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
	<div id="field-editor" title="<spring:message code='form.editor.fieldEditorDialogTitle'/>">
		<form id="field-editor-form">
			<input type="hidden" name="fieldForm" value="fieldForm">
			<div id="errorSummary" class="error errorDialog"></div>
			<fieldset id="field_selector">
				<table>
					<tr>
						<td><label for="fieldEditorSelect"><spring:message code="form.editor.fieldTypeLabel"/></label></td>
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

<div id="reorderFieldsDialog" title="<spring:message code='form.editor.orderFormFieldsDialogTitle'/>" style="display: none">
	<p> <spring:message code="form.editor.orderFormFieldsInstruction"/><p>
	<fieldset>
		<legend><spring:message code="form.editor.fieldsHeader"/></legend>
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
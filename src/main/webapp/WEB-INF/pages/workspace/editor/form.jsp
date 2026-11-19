<%@ include file="/common/taglibs.jsp"%>
<%-- The form field editor (formFieldEditor bundle) loads its own TinyMCE; the legacy TinyMCE 5 must not be on this page. --%>
<script src="<rst:assetUrl value='/scripts/pages/workspace/editor/form.js'/>"></script>
<link rel="stylesheet" href="<rst:assetUrl value='/styles/structuredDocument.css'/>" />

<title><spring:message code="menu.templates.formEditor"/></title>
<head>
	<meta name="heading" content="<spring:message code='form.editor.metaHeading'/>"/>
</head>
<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />
<p style="visibility:hidden;">Text</p>

<axt:toolbar hideSearch="true">
	<jsp:attribute name="menu">
		<div class="container-fluid toolbar-small">
			<ul class="nav navbar-nav" style="float:right;"> 
	      <li>
					<img id="iconImgSrc" src="/image/getIconImage/${template.iconId}" alt="<spring:message code='dialogs.createFromForm.iconAlt'/>" height="32" width="32"  style='display:inline'/>
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

<%-- Fields, their order and their display rules are edited here; the toolbar above saves them. --%>
<div id="formFieldEditor" style="max-width: 1100px; margin: 0 auto; padding: 0 16px;"
	data-form-id="${template.id}" data-editable="${editStatus eq 'EDIT_MODE'}"></div>
<rst:bundle bundle="formFieldEditor" />

<%@ include file="include/templatePublishShareDlg.jsp"%>
<%-- A new form is rendered straight from a POST, where the decorator's include of this template
     comes back empty, and the toolbar's page blocker (RS.blockPage) needs it. A static include works
     for any method. --%>
<c:if test="${pageContext.request.method eq 'POST'}">
	<%@ include file="/scripts/templates/blockUI.html"%>
</c:if>

<script>
	var editable = '${editStatus}';
	var editor = '${editor}';
	var modificationDate = '${modificationDate}';
	var recordId = "${template.id}";
	// identifies this page's hold on the edit lock, so only this page can release it
	var editLockToken = "${editLockToken}";
</script>

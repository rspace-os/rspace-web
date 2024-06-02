<%@ include file="/common/taglibs.jsp"%>

<input type="hidden" id="noOfRows" value="${fn:length(templates)}">
<c:forEach var="template" items="${templates}">
  <c:url var="editURL" value="/workspace/editor/form/edit/${template.id}" />
	<tr data-formid="${template.id}">
		<td>
			<input data-formid="${template.id}" class="form_checkbox" type="checkbox" aria-label="Select form" />
		</td>
		<td class="icon${template.iconId}">
			<img src="/image/getIconImage/${template.iconId}" alt="Icon Image" height="32" width="32" />
		</td>
		<td>
			<img class="infoImg"
				 src="/images/info.svg"
				 style="top:-1px"
				 alt="Form Info"
				 title="Form Info"
				 data-formid="${template.id}"
				 tabindex="0">
			&nbsp;
			<a data-formid="${template.id}" href="${editURL}">${template.name}</a>
		</td>
		<td>${template.owner.username}</td>
		<td style="text-align: center;">${template.version.version}</td>
		<!-- <td>${template.description}</td> -->
		<td class="publishingState">${template.publishingState}</td>
		
		<input type ="hidden" name="permissionpublish" value="${formPermissions[template.id]['SHARE']}">
		<input type ="hidden" name="publishingstate" value="${template.publishingState}">
		<input type ="hidden" name="copyable" value="true">
		<input type ="hidden" name="permissionsedit" value="${formPermissions[template.id]['SHARE']}">
		<input type ="hidden" name="menu" value="${template.inSubjectsMenu}"> 
		<input type ="hidden" name="deletable"  value="${formPermissions[template.id]['DELETE']}"> 
	</tr>
</c:forEach>

<div class="tabularViewBottom" style="opacity: 0">
	<axt:paginate_new paginationList="${paginationList}"></axt:paginate_new>
	<axt:numRecords></axt:numRecords>
	<input type="text" name="" id="resultsPerPage" hidden value="${numberRecords}">
</div>

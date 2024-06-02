<%@taglib prefix="f" uri="http://researchspace.com/functions" %>
<%--
	Produces the correct editor link based on format
--%>
<%@ attribute name="record" required="true"
	type="com.researchspace.model.record.BaseRecord"%>
<%@ attribute name="parentType" required="true"
	type="com.researchspace.model.core.RecordType"%>
<%@ attribute name="editStatus" required="false"
	type="com.researchspace.model.EditStatus"%>
<%@ attribute name="wrapped" required="false"
			  type="com.researchspace.core.util.SearchResultEntry"%>
<%@ attribute name="user" required="true"
			  type="com.researchspace.model.User"%>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>

<c:catch var="highlightNotFoundException">
    <c:set var="visibleRecordName" value="${wrapped.getHighlightedField(\"name\")}" />
</c:catch>
<c:if test="${not empty highlightNotFoundException or empty visibleRecordName}">
    <c:set var="visibleRecordName" value="${f:unescapeXml(record.name)}"  />
</c:if>

<c:choose>
	<c:when test="${record.structuredDocument}">
		<c:choose>
			<c:when test="${record.notebookEntry and not empty parentType and parentType eq 'NOTEBOOK'}">
				<c:url value='/notebookEditor/${recordId}?initialRecordToDisplay=${record.id}&settingsKey=${settingsKey}'
					var="notebookentryLink" />

				<a id="structuredDocument_${record.id}" class="structuredDocument recordNameCell"
					href="${notebookentryLink}" title="${record.name}">${visibleRecordName}</a>
			</c:when>
			<c:when test="${empty editStatus or editStatus eq 'EDIT_MODE'}">
				<c:url value='/workspace/editor/structuredDocument/${record.id}?settingsKey=${settingsKey}' var="regularLink" />
				<a id="structuredDocument_${record.id}" class="structuredDocument recordNameCell" href="${regularLink}" title="${record.name}">
                   ${visibleRecordName}
                </a>
                <c:if test="${record.type eq 'NORMAL:TEMPLATE'}">
                    <span class="templateSpan" style="padding: 2px 6px" title="Template">T</span>
                </c:if>
			</c:when>
			<c:otherwise>
				<%--  Other records that are view-only. Probably never happens. --%>
                ${record.name}
			</c:otherwise>
		</c:choose>
	</c:when>

	<c:when test="${record.folder}">
		<a id="folder_${record.id}" class="folder recordNameCell" data-folderId="${record.id}" data-parentFolderId="${recordId}" href="/workspace/${record.id}" title="${record.name}">
			${visibleRecordName}
		</a>
	</c:when>

	<c:when test="${record['class'].name eq 'com.researchspace.model.EcatImage'}">
		<a id="image_${record.id}" class="recordNameCell" target="_blank" href="/gallery/${record.parent.id}?term=${record.globalIdentifier}" title="${record.name}">
			${visibleRecordName}
		</a>
	</c:when>

	<c:when test="${record['class'].name eq 'com.researchspace.model.EcatAudio'}">
		<a id="audio_${record.id}" class="recordNameCell" target="_blank" href="/gallery/${record.parent.id}?term=${record.globalIdentifier}" title="${record.name}">
			${visibleRecordName}
		</a>
	</c:when>

	<c:when test="${record['class'].name eq 'com.researchspace.model.EcatVideo'}">
		<a id="video_${record.id}" class="recordNameCell" target="_blank" href="/gallery/${record.parent.id}?term=${record.globalIdentifier}" title="${record.name}">
			${visibleRecordName}
		</a>
	</c:when>

	<c:when
		test="${record['class'].name eq 'com.researchspace.model.EcatDocumentFile'}">
		<a id="document_${record.id}" class="recordNameCell" target="_blank" href="/gallery/${record.parent.id}?term=${record.globalIdentifier}" title="${record.name}">
			${visibleRecordName}
		</a>
	</c:when>

	<c:when
		test="${record['class'].name eq 'com.researchspace.model.EcatChemistryFile'}">
		<a id="document_${record.id}" class="recordNameCell" target="_blank" href="/gallery/${record.parent.id}?term=${record.globalIdentifier}" title="${record.name}">
			${visibleRecordName}
		</a>
	</c:when>

	<c:when
		test="${record['class'].name eq 'com.researchspace.model.record.Snippet'}">
        <c:if test="${record.getOwnerOrSharedParentForUser(user).isPresent()}">
		<a id="snippet_${record.id}" class="recordNameCell" target="_blank" href="/gallery/${record.getOwnerOrSharedParentForUser(user).get().id}?term=${record.globalIdentifier}&mediaType=Snippets" title="${record.name}">
				${visibleRecordName}
		</a>
        </c:if>
        <c:if test="${not record.getOwnerOrSharedParentForUser(user).isPresent() and record.getSharedFolderParent().isPresent()}">
            <a id="snippet_${record.id}" class="recordNameCell" target="_blank" href="/gallery/${record.getSharedFolderParent().get().id}?term=${record.globalIdentifier}&mediaType=Snippets" title="${record.name}">
                    ${visibleRecordName}
            </a>
        </c:if>
        <!--It should not be possible for a shared snippet to have BOTH these test conditions
        as false but this is included to prevent JSP render error IF that should occur-->
        <c:if test="${not record.getOwnerOrSharedParentForUser(user).isPresent() and not record.getSharedFolderParent().isPresent()}">
            <a id="snippet_${record.id}" class="recordNameCell" target="_blank" href="/gallery/?term=${record.globalIdentifier}&mediaType=Snippets" title="${record.name}">
                    ${visibleRecordName}
            </a>
        </c:if>
	</c:when>

	<c:otherwise>
      	${name} not supported
    </c:otherwise>
</c:choose>

<%@ include file="/common/taglibs.jsp"%>
<%@taglib prefix="f" uri="http://researchspace.com/functions" %>

<c:if test="${fn:length(errorMsg) > 0}">
    <script>
      apprise('${errorMsg}');
    </script>
</c:if>

<c:if test="${fn:length(successMsg) > 0}">
    <script>
      RS.confirm("${successMsg}", "success", 3000);
    </script>
</c:if>


<%-- This page provides the search results and listings of workspace contents. --%>
<div class="breadcrumb bootstrap-custom-flat">
    <axt:breadcrumb breadcrumb="${bcrumb}" breadcrumbTagId="workspaceBcrumb"></axt:breadcrumb>
    <script>
      $(document).ready(function () {
        setUpWorkspaceBreadcrumbs();
      });
      var settingsKey = "${settingsKey}";
    </script>
</div>

<div style="position: relative;" class="optionsPopup">
    <input id="currFolderId" type="hidden" value="${recordId}" />
    <input id="authzCreateRecord" type="hidden" value="${createPermission.createRecord}" />
    <input id="authzCreateThirdPartyRecord" type="hidden" value="${allowThirdPartyImport}" />
    <input id="authzCreateFormRecord" type="hidden" value="${allowCreateForm}" />
    <input id="authzCreateFolder" type="hidden" value="${createPermission.createFolder}" />
    <input id="movetargetRoot" type="hidden" value="${movetargetRoot}" />
    <input id="isNotebook" type="hidden" value="${isNotebook}" />
</div>

<div class="rs-working-area">
    <div class="panel panel-default">
        <input type="hidden" id="noOfRows" value="${fn:length(searchResults.results)}">
        <table id="file_table" class="table table-striped table-hover mainTable">
            <thead>
            <tr>
                <th>
                    <input  id="selectAllToggle"
                            type="checkbox"
                            title="Select/deselect all"
                            aria-label="Select/deselect all"
                    />
                </th>
                <th>
                    <spring:message code="workspace.list.type.header" />
                </th>
                <th>
                    <div class="orderByTableHeader">
                        <a href="#" id="page_${orderByName.link}" class="orderByLink">
                            <spring:message code="workspace.list.name.header" /></a>
                        <input id="orderName" type="button" class="orderButtonClass" style="display: none;" />
                    </div>
                </th>
                <th>
                    <div class="orderByTableHeader">
                        <a href="#" id="page_${orderByCreationDate.link}" class="orderByLink">
                            <spring:message code="workspace.list.creationDate.header" /></a>
                        <input id="orderCreationDate" type="button" class="orderButtonClass" style="display:none;" />
                    </div>
                </th>
                <th>
                    <div class="orderByTableHeader">
                        <a href="#" id="page_${orderByDate.link}" class="orderByLink">
                            <spring:message code="workspace.list.date.header" /></a>
                        <input id="orderDate" type="button" class="orderButtonClass" style="display: none;" />
                    </div>
                </th>
                <th>
                    <spring:message code="workspace.list.id.header" />
                </th>
                <th>
                    <spring:message code="workspace.list.owner.header" />
                </th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="record" items="${searchResults.results}">
                <c:if test="${record.invisible eq false}">
                    <tr data-recordId="${record.id}" data-recordName="${record.name}">
                        <td class="workspace-record-operations">
                            <input  data-id="${record.id}"
                                    data-globalid="${record.globalIdentifier}"
                                    id="chk_${record.id}"
                                    class="record_checkbox"
                                    type="checkbox"
                                    aria-label="Select record"
                            />
                            <input  id="type_${record.id}"
                                    value="${record.type}"
                                    name="recordType"
                                    type="hidden"
                            />
                            <c:set var="canShare" value="false"></c:set>
                            <rs:chkRecordPermssns user="${user}" action="SHARE" record="${record}">
                                <c:set var="canShare" value="true"></c:set>
                            </rs:chkRecordPermssns>
                            <input  id="share_${record.id}"
                                    value="${canShare}"
                                    name="isShareable"
                                    type="hidden"
                            />
                            <c:set var="canTag" value="false"></c:set>
                            <c:if test="${record.taggable eq true}">
                                <rs:chkRecordPermssns user="${user}" action="WRITE" record="${record}">
                                    <c:set var="canTag" value="true"></c:set>
                                </rs:chkRecordPermssns>
                            </c:if>
                            <input  id="edit_${record.id}"
                                    value="${canTag}"
                                    name="isTaggable"
                                    type="hidden"
                            />
                            <c:set var="canPublish" value="false"></c:set>
                            <c:if test="${publish_allowed}">
                                <%--  A PI can Publish a record explicitly shared with them,
                                    even though they can only share a record which they own --%>
                                <rs:chkRecordPermssns user="${user}" action="PUBLISH" record="${record}">
                                    <c:set var="canPublish" value="true"></c:set>
                                </rs:chkRecordPermssns>
                            </c:if>
                            <input  id="publish_${record.id}"
                                    value="${canPublish}"
                                    name="isPublishable"
                                    type="hidden"
                            />
                            <input  id="authzRename_${record.id}"
                                    type="hidden"
                                    name="isRenamable"
                                    value="${createPermission.instancePermissions[record.id]['RENAME']}"
                            />
                            <input  id="authzDelete_${record.id}"
                                    type="hidden"
                                    name="isDeletable"
                                    value="${createPermission.instancePermissions[record.id]['DELETE']}"
                            />
                            <input  id="authzMove_${record.id}"
                                    type="hidden"
                                    name="isMoveable"
                                    value="${createPermission.instancePermissions[record.id]['SEND']}"
                            />
                            <input  id="authzCopy_${record.id}"
                                    type="hidden"
                                    name="isCopyable"
                                    value="${createPermission.instancePermissions[record.id]['COPY']}"
                            />
                            <input  id="offlineStatus_${record.id}"
                                    type="hidden"
                                    name="offlineStatus"
                                    value="${record.offlineWorkStatus}"
                            />
                            <input  id="authzExport_${record.id}"
                                    type="hidden"
                                    name="isExportable"
                                    value="${createPermission.instancePermissions[record.id]['EXPORT']}"
                            />
                            <input  id="favoriteStatus_${record.id}"
                                    type="hidden"
                                    name="favoriteStatus"
                                    value="${record.favoriteStatus}"
                            />
                        </td>
                        <td class="workspace-record-icon">
                            <axt:record_icon record="${record}" />
                        </td>
                        <td class="workspace-record-name">
                            <a href="#" class="workspaceRecordInfo" alt="Record Info" title="Record Info">
                                <img class="infoImg"src="/images/info.svg" style="top:-1px">
                            </a>
                            <axt:record_editor_link parentType="${parentType}" record="${record}" user="${user}"/>
                            <c:if test="${record.favoriteStatus eq 'FAVORITE'}">
                                <img class="favoriteImg"
                                     src="/images/favorite.svg"
                                     alt="Favorite"
                                     title="Favorite">
                            </c:if>
                            <img id="sharedStatusImg_${record.id}"
                                 src="/images/shared.svg"
                                 alt="Shared"
                                 title="Shared"
                                 <c:if test="${not record.shared}">hidden</c:if>
                            >
                            <img id="publishedStatusImg_${record.id}"
                                 src="/images/icons/html.png"
                                 alt="Published"
                                 title="Published"
                                 width="35" height="35"
                                 <c:if test="${not record.published}">hidden</c:if>
                            >
                            <c:if test="${record.signed}">
                                <img class="witnessedImg"
                                     src="/images/signed.svg"
                                     alt="Record Is Signed"
                                     title="Signed">
                            </c:if>
                            <c:if test="${record.structuredDocument and not record.allFieldsValid}">
                                <img src="/images/icons/missingMandatoryField.svg"
                                     alt="Some of the mandatory fields in the document are missing a value"
                                     title="Mandatory fields are missing value" width="30" height="30"
                                >
                            </c:if>
                        </td>
                        <td class="workspace-record-create-date">
                            <fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${record.creationDateAsDate}" />
                        </td>
                        <td class="workspace-record-modify-date">
                            <fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${record.modificationDateAsDate}" />
                        </td>

                        <td class="workspace-record-id">
                            <c:url value="/globalId/${record.globalIdentifier}" var="globalURL"></c:url>
                            <a href="${globalURL}" class="workspace-record-id-link">
                                <c:out value="${record.globalIdentifier}" />
                            </a>
                        </td>
                        <c:choose>
                            <c:when test="${record.type.equals('FOLDER:SHARED_GROUP_FOLDER_ROOT')}">
                                <td class="workspace-record-ownerName" data-uname="Group-owned">
                                    Group-owned
                                </td>
                            </c:when>
                            <c:otherwise>
                                <td class="workspace-record-ownerName" data-uname="${record.owner.username}">
                                    <div    data-test-id="mini-profile-activator-${record.owner.id}"
                                            class="user-details"
                                            data-userid="${record.owner.id}"
                                            data-firstName="${record.owner.firstName}"
                                            data-lastName="${record.owner.lastName}"
                                            data-uniqueid="${record.globalIdentifier}"
                                            data-position="bottom_left">
                                        <a href="#" style="font-size: 14px; line-height:30px">${record.owner.firstName}&nbsp;${record.owner.lastName}</a>
                                    </div>
                                </td>
                            </c:otherwise>
                        </c:choose>
                    </tr>
                </c:if>
            </c:forEach>
            </tbody>
        </table>
    </div>
</div>

<div class="tabularViewBottom">
    <axt:paginate_new paginationList="${paginationList}" omitATagLinkId="true"></axt:paginate_new>
    <axt:numRecords></axt:numRecords>
</div>


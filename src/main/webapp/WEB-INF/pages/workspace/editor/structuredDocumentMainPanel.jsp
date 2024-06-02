<%@ include file="/common/taglibs.jsp"%>
    <%@ include file="include/messagingToolbarButtons.jsp"%>

<title>${structuredDocument.name} </title>
<head>
    <meta name="heading" content="Notebook" />
    <link rel="canonical" href="${applicationScope['RS_DEPLOY_PROPS']['serverUrl']}${requestScope['javax.servlet.forward.servlet_path']}" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
</head>
<link href="<c:url value='/scripts/bower_components/jquery-tagit/css/tagit.ui-zendesk.css'/>" rel="stylesheet" />

<link rel="stylesheet" href="<c:url value='/styles/pages/workspace/workspace.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/pages/workspace/workspace-toolbar.css'/>">
<link rel="stylesheet" href="<c:url value='/styles/pages/workspace/workspace-widgets.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/pages/workspace/workspace-extra-icons.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/pages/workspace/workspace-dialogs.css'/>" />

<link rel="stylesheet" href="<c:url value='/styles/structuredDocument.css'/>" />
<link rel="stylesheet" href="<c:url value='/scripts/jqueryFileTree/jqueryFileTree.css'/>" />
<link rel="stylesheet" href="<c:url value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/messages.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/mediaGallery.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/rs-widgets.css'/>" />
<link rel="stylesheet" href="<c:url value='/scripts/tinymce/tinymce516/plugins/codesample/css/prism.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/tinymce_rs.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/plugins/autocomplete.css'/>" />
<link rel="stylesheet" href="<c:url value='/scripts/bower_components/font-awesome/css/font-awesome.min.css'/>" />
<script src="<c:url value='/scripts/bower_components/file-saverjs/FileSaver.js'/>"></script>
<script src="<c:url value='/scripts/bower_components/tableexport.js/dist/js/tableexport.js'/>"></script>
<script src="<c:url value='/scripts/tinymce/tinymce516/plugins/codesample/js/prism.js'/>"></script>
<script src="<c:url value='/scripts/bower_components/jquery-tagit/js/tag-it.min.js'/>"></script>
<script src="<c:url value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>
<script src="<c:url value='/scripts/bower_components/jquery.scrollTo/jquery.scrollTo.min.js'/>"></script>
<script src="<c:url value='/scripts/jwplayer.js'/>"></script>
<script src="<c:url value='/scripts/jqueryFileTree/jqueryFileTree.js'/>"></script>
<script src="<c:url value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
<script src="/scripts/pages/workspace/clientUISettings.js"></script>
<script src="<c:url value='/scripts/tags/shareDlg.js'/>"></script>
<script src="<c:url value='/scripts/pages/coreEditor.js'/>"></script>
<script src="<c:url value='/scripts/pages/workspace/editor/documentView.js'/>"></script>
    <axt:toolbar hideSearch="true">
      <jsp:attribute name="menu">
        <axt:saveAsTemplate structuredDocument="${structuredDocument}"></axt:saveAsTemplate>
        <axt:useTemplate />
        <%@ include file="include/signDocumentDialog.jsp"%>
        <%@ include file="include/witnessDocumentDialog.jsp"%>
      </jsp:attribute>
    </axt:toolbar>
<c:set var="isTemplate" value="${fn:contains(structuredDocument.type, 'TEMPLATE')}"></c:set>
    <%-- The public view is for a document made accessible to non RSpace users --%>
<c:if test="${param.publicDocument}">
    <div id="public_document_view"/>
</c:if>
    <div class="bootstrap-custom-flat">
        <div class="breadcrumb">
            <div class="container" style="display:flex;">
          <span style="flex-grow: 1">
            <axt:breadcrumb breadcrumb="${bcrumb}" breadcrumbTagId="editorBcrumb"></axt:breadcrumb>
          </span>
                <c:if test="${!param.publicDocument}">
                    <div> <img id="publishedStatusImg"
                               src="/images/icons/html.png"
                               alt="Published"
                               title="Published"
                               width="35" height="35"
                               <c:if test="${not isPublished}">hidden</c:if>
                    ><label style="cursor:default;" for="publishedStatusImg" <c:if test="${not isPublished}">hidden</c:if>>Published</label>
                    </div>
                </c:if>
                <c:if test="${!param.publicDocument}">
                    <script>
                        $(document).ready(function() { setUpEditorBreadcrumbs(); });
                    </script>
                </c:if>
                <c:if test="${isTemplate eq 'true'}">
                    <span class="templateSpan" style="padding: 3px 12px;">Template</span>
                </c:if>
                <axt:status></axt:status>
            </div>
        </div>
    </div>
    <c:if test="${!param.publicDocument}">
        <div class="rs-record-info-panel">
            <div class="rs-record-header-line"></div>
        </div>
    </c:if>
    <table id="structuredDocument" name="mainTable" class="structuredDocumentTable">
        <tr>
        <c:if test="${param.publicDocument}">
                    <td>
                    <label class="displayRevisionsContainer">
                        <input type="checkbox" class="displayRevisions"> Show last modified date
                    </label>
                </td>
        </c:if>
            <td>
                <input
                        type="hidden" id='lastModificationRecord'
                        name="timestamp"
                        value="${structuredDocument.modificationDate}"
                />
                <input
                        type="file"
                        class="fromLocalComputer fileReplaceInput"
                        style="display:none"
                        aria-label="Insert file from local computer"
                />
            </td>
        </tr>
        <c:forEach var="field" items="${structuredDocument.fields}">
            <c:choose>
                <c:when
                        test="${field['class'].name eq 'com.researchspace.model.field.NumberField'}">
                    <%@ include file="include/numberField.jsp" %>
                </c:when>
                <c:when
                        test="${field['class'].name eq 'com.researchspace.model.field.StringField'}">
                    <%@ include file="include/stringField.jsp"%>
                </c:when>
                <c:when
                        test="${field['class'].name eq 'com.researchspace.model.field.TextField'}">
                    <%@ include file="include/textField.jsp"%>
                </c:when>
                <c:when
                        test="${field['class'].name eq 'com.researchspace.model.field.RadioField' && field.fieldForm.showAsPickList eq false }">
                    <%@ include file="include/radioField.jsp"%>
                </c:when>
                <c:when
                        test="${field['class'].name eq 'com.researchspace.model.field.RadioField' && field.fieldForm.showAsPickList eq true}">
                    <%@ include file="include/pickListField.jsp"%>
                </c:when>
                <c:when
                        test="${field['class'].name eq 'com.researchspace.model.field.ChoiceField'}">
                    <%@ include file="include/choiceField.jsp"%>
                </c:when>
                <c:when
                        test="${field['class'].name eq 'com.researchspace.model.field.DateField'}">
                    <%@ include file="include/dateField.jsp"%>
                </c:when>
                <c:when
                        test="${field['class'].name eq 'com.researchspace.model.field.TimeField'}">
                    <%@ include file="include/timeField.jsp"%>
                </c:when>
            </c:choose>
        </c:forEach>
    </table>
    <div class="bootstrap-custom-flat attachmentsListing">
      <div class="container">
        <div class="row">
          <h2 class="h4" style="margin-bottom: 0">Attachments</h2>
          <small>Tap to expand listing.</small>
        </div>

        <div class="row" >
          <button class="btn btn-primary attachmentButton" title="Attachments">
            Files
          </button>
          <div class="attachmentList" style="display:none">
            <ul class="attachmentUL"></ul>
          </div>
        </div>

        <c:if test="${inventoryAvailable eq 'true'}">
          <div class="row">
            <div id="inventoryRecordList" data-documentId="${id}">
            </div>
          </div>
        </c:if>
    </div>

    <axt:signature record="${structuredDocument}"></axt:signature>
</div>
</div>
<jsp:include page="../../documentAndNotebookTemplates.jsp" />
<jsp:include page="include/photoswipe.jsp" />
<script>
  var editable = '${editStatus}';
   var isEditable = '${editStatus.editable}';
    var editor = '${editor}';
    var hasAutosave = '${hasAutosave}';
    var modificationDate = '${modificationDate}';
    var recordId = '${id}';
    var parentId = '${parentId}';
    var nameTemplate = '${template}';
    var fromNotebook = '${fromNotebook}';
    var isDocumentEditor = true;
    var recordName = "${structuredDocument.editInfo.name}";
    var recordTags = "${structuredDocument.docTag}";//used to populate the displayed tags text for a document: documentView.js sets this value onto a moustache template as 'tags' look for 'var mdata =' etc)
    var tagMetaData = "${structuredDocument.tagMetaData}";//this global will be used by coreEditor for tagging
    if (recordTags != null && recordTags != undefined) {
        recordTags = recordTags.replace(/\,/g, ", ");
    }
    var recordType = "${structuredDocument.type}";
    var canCopy = false;
    <rs:chkRecordPermssns user="${user}" record="${structuredDocument}" action="COPY">
    canCopy=true;
    </rs:chkRecordPermssns>
    var settingsKey = "${settingsKey}";
    var docRevision = "${docRevision}";
    var versionNumber = !!docRevision ? "${structuredDocument.userVersion.version}" : null;
    var globalId = "${structuredDocument.oid.idString}" + (versionNumber ? "v" + versionNumber : "");
    var basicDocument = "${structuredDocument.basicDocument}";

    var clientUISettingsPref = "${clientUISettingsPref}";
</script>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>
<%@ attribute name="folderChooserId" required="true" type="java.lang.String" %>

<script src="<rst:assetUrl value='/scripts/tags/folderChooser.js'/>"></script>

<div id="folderChooser${folderChooserId}" style="display: none;">
    <c:if test="${folderChooserId != '-shareIntoFolder'}">
        <spring:message code="dialogs.folderChooser.optionally"/>
    </c:if>
    <a href="#" class="nobutton" id="folderChooserLnk${folderChooserId}">
        <spring:message code="dialogs.folderChooser.linkText"/>
    </a>
    <span id="folderChooserDesc${folderChooserId}"></span>

    <c:if test="${folderChooserId == '-shareIntoFolder'}">
        <h4><spring:message code="dialogs.folderChooser.shareTitle"/></h4>
    </c:if>

    <div>
        <div id="folderChooserInfo${folderChooserId}" style="display: none">
            <div class="formfield_highlighted">
                <spring:message code="dialogs.folderChooser.selectedFolder"/>
                <span id="folderChooser-path${folderChooserId}"></span>
            </div>
            <div id="folderChooserFolderCreation${folderChooserId}">
                <p style="margin-bottom:5px">
                    <spring:message code="dialogs.folderChooser.createNewFolderInstruction"/>
                </p>
                <div class="bootstrap-custom-flat">
                    <input class="form-control folderChooserData${folderChooserId}"
                           id="newFolder${folderChooserId}"
                           name="newFolder"
                           placeholder="<spring:message code='dialogs.folderChooser.newFolderPlaceholder'/>"
                           aria-label="<spring:message code='dialogs.folderChooser.newFolderAriaLabel'/>"
                           style="font-size:12px;height:24px;padding-left:1px;padding-right:1px"/>
                    <span id="folderChooser-createFolderSpan${folderChooserId}">
                        <a style="color:#337ab7;padding-left:1px"
                           id="folderChooser-createNewSubfolder${folderChooserId}"
                           class="nobutton" href="#"><spring:message code="common:actions.create"/></a>
                    </span>
                </div>
            </div>
        </div>

        <div id="folderChooserTree${folderChooserId}" style="margin: 10px 3px 3px;"></div>

        <input class="folderChooserData${folderChooserId}" type="hidden"
               id="folderChooser-id${folderChooserId}"/>
        <input class="folderChooserType" type="hidden"
               id="folderChooser-type${folderChooserId}"/>
    </div>
</div>

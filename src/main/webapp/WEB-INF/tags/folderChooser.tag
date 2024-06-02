<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ attribute name="folderChooserId" required="true" type="java.lang.String" %>

<script src="/scripts/tags/folderChooser.js"></script>

<div id="folderChooser${folderChooserId}" style="display: none;">
    <c:if test="${folderChooserId != '-shareIntoFolder'}">
        Optionally,
    </c:if>
    <a href="#" class="nobutton" id="folderChooserLnk${folderChooserId}">
        choose a folder or notebook
    </a>
    <span id="folderChooserDesc${folderChooserId}"></span>

    <c:if test="${folderChooserId == '-shareIntoFolder'}">
        <h4>Select a folder to share into</h4>
    </c:if>

    <div>
        <div id="folderChooserInfo${folderChooserId}" style="display: none">
            <div class="formfield_highlighted">
                Selected folder:
                <span id="folderChooser-path${folderChooserId}"></span>
            </div>
            <div id="folderChooserFolderCreation${folderChooserId}">
                <p style="margin-bottom:5px">
                    Optionally, create a new folder in the chosen folder:
                </p>
                <div class="bootstrap-custom-flat">
                    <input class="form-control folderChooserData${folderChooserId}"
                           id="newFolder${folderChooserId}"
                           name="newFolder"
                           placeholder="New Folder Name"
                           aria-label="New folder name"
                           style="font-size:12px;height:24px;padding-left:1px;padding-right:1px"/>
                    <span id="folderChooser-createFolderSpan${folderChooserId}">
                        <a style="color:#337ab7;padding-left:1px"
                           id="folderChooser-createNewSubfolder${folderChooserId}"
                           class="nobutton" href="#">Create</a>
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

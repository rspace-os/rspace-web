<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %>
<%@ taglib uri="http://researchspace.com/tags" prefix="rst" %>

<%--
This is a menu bar for CRUD type operations on Galleries.
It defines operations for move, copy, delete, rotate and rename.
 --%>
<script src="<c:url value='/scripts/pages/workspace/GalleriesCrudops.js'/>"></script>
<script src="<c:url value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
<script src="<c:url value='/scripts/jqueryFileTree/jqueryFileTreeSorter.js'/>"></script>

<span id="galleriesMenuCrud">
    <a id="galleriesCrudCopy"    class="galleriesCrudAction" href="#" style="background-image:url('/images/icons/copyIcon.png');">Duplicate</a>
    <a id="galleriesCrudMove"    class="galleriesCrudAction" href="#" style="background-image:url('/images/icons/moveIcon.png');">Move</a>
    <a id="galleriesCrudRename"  class="galleriesCrudAction" href="#" style="background-image:url('/images/icons/renameIcon.png');">Rename</a>
    <a id="galleriesCrudDelete"  class="galleriesCrudAction" href="#" style="background-image:url('/images/icons/deleteIcon.png');">Delete</a>
    <a id="galleriesCrudDeleteDMP"  class="galleriesCrudAction" href="#" style="background-image:url('/images/icons/deleteIcon.png');">Delete DMP</a>
    <a id="galleriesCrudPublish" class="galleriesCrudAction" href="#" style="background-image:url('/images/icons/publishIcon.png');">Publish</a>
    <a id="galleriesCrudExport"  class="galleriesCrudAction" href="#" style="background-image:url('/images/icons/zipExportIcon.png');">Export</a>
    <a id="galleriesCrudEdit"    class="galleriesCrudAction" href="#" style="background-image:url('/images/icons/editIcon.png');">Edit</a>
    <a id="galleriesCrudInsert"  class="galleriesCrudAction" href="#" style="background-image:url('/images/icons/insertIcon.png');">Insert</a>
    <a id="shareRecord"   class="galleriesCrudAction"  href="#" style="background-image:url('/images/icons/insertIcon.png');">Share</a>
    <rst:hasDeploymentProperty name="netFileStoresEnabled" value="true">
      <a id="moveToIrods" class="galleriesCrudAction" href="#" style="background-image:url('/images/icons/irodsIcon.png');">Move to iRODS</a>
    </rst:hasDeploymentProperty>
</span>

<!-- Move record dialog - initially hidden on page load -->
<div id='moveGalleries' title="Select move target" style="display: none;">
	<p>Please select a folder to move items to:</p>
	<div class="sortingSettings" style="padding: 5px;">
    	Order by:
    	<select class="orderBy" aria-label="Order by">
    		<option value="name">Name</option>
    		<option value="creationdate">Creation Date</option>
    		<option value="modificationdate">Last Modified</option>
    	</select>
    	<select class="sortOrder" aria-label="Sort order">
    		<option value="ASC">Ascending</option>
    		<option value="DESC">Descending</option>
    	</select>
    </div>
	<div id="galleries-folder-tree"
		style="height: 350px; background: white; overflow: scroll; border-style: solid; border-width: 1px; border-color: black; padding: 3px;">
	</div>
	<br />
	<p>
		You have selected the path: <br /> <b><span id="galleries-folder-move-path">/</span></b>
	</p>
</div>

<!-- Rename record dialog, initially hidden on page load -->
<div id="renameGalleries">
	<label for="galleryNameInput"><fmt:message key="dialogs.renameRecord.label.newName"></fmt:message></label>
	<input id="galleryNameInput" type="text" width="30">
</div>

<!-- Public link dialog, initially hidden on page load -->
<div id="publishGalleries" style="margin: 10px;">
	<div> This file is now publicly available from this stable link:<br><br> <a id="fileLink" style="color: #1465B7;" href=""></a><br><br>  Please copy and share.</div>
</div>

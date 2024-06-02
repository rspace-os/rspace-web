<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>

<link rel="stylesheet" href="<c:url value='/scripts/bower_components/jquery.fancytree/dist/skin-bootstrap/ui.fancytree.min.css'/>" />
<link rel="stylesheet" href="<c:url value='/styles/tags/fileTreeBrowser.css'/>" />

<script src="<c:url value='/scripts/bower_components/jquery.scrollTo/jquery.scrollTo.min.js'/>"></script>
<script src="<c:url value='/scripts/bower_components/jquery.fancytree/dist/jquery.fancytree-all.min.js'/>"></script>
<script src="<c:url value='/scripts/tags/fileTreeBrowser.js'/>"></script> 
<script src="<c:url value='/scripts/tags/fileTreeBrowserSorter.js'/>"></script>

<div id="fileBrowsing" class="fileTreeBrowserPanel bootstrap-custom-flat">

  <div class="fileTreeButtons navbar navbar-inverse rs-container container-fluid" id="fileTreeToolbar">
      <ul class="nav navbar-nav"> 
        <li>
          <a id="fileTreeSettingsToggle" href="#" class="rs-actionbar__item rs-actionbar__item--icon" title="Change tree ordering">
            <span class="glyphicon glyphicon-cog"></span>
          </a>
        </li>
      </ul>
      <ul class="nav navbar-nav">
        <li class="sortingSettingsLi">
          <div class="sortingSettings" style="display:none">
            <span style="margin-left: 10px; color: white;">
                Order by: 
            </span>
            <br/>
            <div style="text-align: center;">
                <select class="orderBy" aria-label="Order by">
                  <option value="name">Name</option>
                  <option value="creationdate">Creation Date</option>
                  <option value="modificationdate">Last Modified</option>
                </select>
                <select class="sortOrder" style="margin-left: 2px;" aria-label="Sort order">
                  <option value="ASC">Ascending</option>
                  <option value="DESC">Descending</option>
                </select>
                <button class="btn btn-default" id="applySortingSettings" title="Apply new ordering">
                    <span class="glyphicon glyphicon-ok"></span>
                </button>
            </div>
          </div>
        </li>
      </ul>
      <ul class="nav navbar-nav navbar-right">
        <li>
          <a id="hideFileTreeSmall" href="#" class="rs-actionbar__item rs-actionbar__item--icon" title="Hide tree browser">
            <span class="glyphicon glyphicon-menu-right"></span>
          </a>
        </li>
      </ul>
  </div>

  <ul id="fancyTree"></ul>

</div>

<div class="bootstrap-custom-flat">
    <button id="showFileTreeSmall" class="btn btn-primary" title="Show tree browser">
      <span class="glyphicon glyphicon-menu-left"></span>
    </button>
</div>

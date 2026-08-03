<%@ include file="/common/taglibs.jsp"%>

<script>
  var onlyDocuments = ${param.onlyDocuments};
</script>

<div class="bootstrap-custom-flat">
 <div id="recordPickerTabConfigDiv" class="container">

  <!-- Nav tabs -->
  <ul id="recordPickerTopNavBar" class="nav nav-pills" role="tablist">
    <li role="presentation" class="active"><a href="#searchTab" aria-controls="search" role="tab" data-toggle="tab"><spring:message code="common:actions.search"/></a></li>
    <li role="presentation" class=""><a href="#browseTab" aria-controls="browse" role="tab" data-toggle="tab"><spring:message code="searchableRecordPicker.browseTab"/></a></li>
  </ul>

  <hr id="navbarHr"/>

  <div class="tab-content">
    <div role="tabpanel" class="tab-pane active" id="searchTab">
        <form>
          <div id="searchDocDiv">
              <input type="text" id="searchQueryInput" name="searchQuery" placeholder="<spring:message code='searchableRecordPicker.searchPlaceholder'/>"></input>
              <div id="searchBtn" class="btn btn-default"><spring:message code="common:actions.search"/></div>

              <div id="searchResultsDiv">

              </div>
          </div>
        </form>
    </div>

    <div role="tabpanel" class="tab-pane" id="browseTab">
        <form>
          <div id="fileTreeDiv">
              <axt:fileTreeBrowser />
          </div>
        </form>
    </div>

  </div>

 </div>
</div>

<script id="searchResultTemplate" type="text/mustache">
  <div class='searchResultDiv' data-globalid='{{globalId}}' data-name='{{docName}}'>
    <div class='searchResultIconDiv'>
       <img src='{{iconUrl}}' height='32' width='32' />
    </div>
    <div class='searchResultDescriptionDiv'>
        <span class="resultDocName">{{docName}}</span>
        <br />
        <spring:message code="searchableRecordPicker.globalIdOwnerSummary"/>
    </div>
  </div>
</script>

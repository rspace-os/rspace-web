<%--
	Dialog that lists templates
--%>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>

<script src="<rst:assetUrl value='/scripts/tags/createFromTemplateDlg.js'/>"></script>
<link rel="stylesheet" href="<rst:assetUrl value='/styles/tags/createFromTemplateDlg.css'/>" />

<link rel="stylesheet" href="<rst:assetUrl value='/scripts/bootstrap-namespace/bootstrap-namespace.min.css'/>" />

<div id="createFromTemplateDlg" style="display: none">

    <div id="templateDlgTabs" class="bootstrap-namespace">

        <form id="templateFilterForm" class="form-inline">
            <div class="form-group">
                <label id="templateFilterInputLabel" for="templateFilterInput"><spring:message code="dialogs.createFromTemplate.filterByName"/></label>
                <input type="text" class="form-control" id="templateFilterInput">
            </div>
            <button type="submit" class="btn btn-primary"><spring:message code="dialogs.createFromTemplate.goButton"/></button>
        </form>

        <ul>
          <li><a href="#myTemplatesTab"><spring:message code="dialogs.createFromTemplate.myTemplatesTab"/></a></li>
          <li><a href="#sharedTemplatesTab"><spring:message code="dialogs.createFromTemplate.sharedTemplatesTab"/></a></li>
        </ul>

        <div id="myTemplatesTab">
            <div class="templatesTableDiv">
                <table class="templatesTable table table-hover">
                    <thead>
                        <tr>
                            <th></th>
                            <th><a href="#" class="sortByName" data-sorted="true"><spring:message code="workspace.list.name.header"/></a></th>
                            <th style="width: 10em;"><a href="#" class="sortByCreation" data-sorted="false"><spring:message code="workspace.sort.byCreationDate"/></a></th>
                        </tr>
                    </thead>
                    <tbody class="templatesTableBody">
                    </tbody>
                </table>
            </div>
            
            <div class="rowTemplate" style="display:none">
              <table>
                <tr data-id="{{template.id}}">
                    <td><span style="display: inline-block; height: 32px; width: 32px; background-image: url(/image/getIconImage/{{template.iconId}})" data-icon="{{template.iconId}}"></span></td>
                    <td class="templateNameCell">{{template.name}}</td>
                    <td class="templateCreatedCell">{{template.creationDateWithClientTimezoneOffset}}</td>
                </tr>
              </table>
            </div>
        </div>
        
        <div id="sharedTemplatesTab">
            <div class="templatesTableDiv">
                <table class="templatesTable table table-hover">
                    <thead>
                        <tr>
                            <th></th>
                            <th style="width: 10em;"><a href="#" class="sortByName" data-sorted="true"><spring:message code="workspace.list.name.header"/></a></th>
                            <th style="width: 10em;"><a href="#" class="sortByCreation" data-sorted="false"><spring:message code="workspace.sort.byCreationDate"/></a></th>
                            <th style="width: 10em;"><a href="#" class="sortByOwner" data-sorted="false"><spring:message code="workspace.list.owner.header"/></a></th>
                        </tr>
                    </thead>
                    <tbody class="templatesTableBody">
                    </tbody>
                </table>
            </div>
            
            <div class="rowTemplate" style="display:none">
              <table>
                <tr data-id="{{template.id}}">
                    <td><span style="display: inline-block; height: 32px; width: 32px; background-image: url(/image/getIconImage/{{template.iconId}})" data-icon="{{template.iconId}}"></span></td>
                    <td class="templateNameCell">{{template.name}}</td>
                    <td class="templateCreatedCell">{{template.creationDateWithClientTimezoneOffset}}</td>
                    <td class="templateOwnerCell">{{template.ownerFullName}}</td>
                </tr>
              </table>
            </div>
        </div>

        <form id="createFromTemplateNameForm" class="form-inline">
            <div class="form-group">
                <label id="createFromTemplateNameLabel" for="createFromTemplateNameInput"><spring:message code="dialogs.createFromTemplate.newDocumentName"/></label>
                <input id="createFromTemplateNameInput" type="text" class="form-control"/>
            </div>
        </form>
    </div>

    <form id="createFromTemplateForm" method="POST" action="#">
        <input id="createFromTemplateNewName" type="hidden" name="newname" />
        <input id="createFromTemplateId" type="hidden" name="template" />
    </form>

</div>

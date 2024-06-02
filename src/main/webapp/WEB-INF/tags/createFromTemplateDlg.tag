<%-- 
	Dialog that lists templates
--%>

<script src="/scripts/tags/createFromTemplateDlg.js"></script>
<link rel="stylesheet" href="/styles/tags/createFromTemplateDlg.css" />

<link rel="stylesheet" href="/scripts/bootstrap-namespace/bootstrap-namespace.min.css" />

<div id="createFromTemplateDlg" style="display: none">

    <div id="templateDlgTabs" class="bootstrap-namespace">

        <form id="templateFilterForm" class="form-inline">
            <div class="form-group">
                <label id="templateFilterInputLabel" for="templateFilterInput">Filter by Name</label>
                <input type="text" class="form-control" id="templateFilterInput">
            </div>
            <button type="submit" class="btn btn-primary">Go</button>
        </form>

        <ul>
          <li><a href="#myTemplatesTab">My Templates</a></li>
          <li><a href="#sharedTemplatesTab">Shared With Me</a></li>
        </ul>

        <div id="myTemplatesTab">
            <div class="templatesTableDiv">
                <table class="templatesTable table table-hover">
                    <thead> 
                        <tr>
                            <th></th>
                            <th><a href="#" class="sortByName" data-sorted="true">Name</a></th>
                            <th style="width: 10em;"><a href="#" class="sortByCreation" data-sorted="false">Creation Date</a></th>
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
                            <th style="width: 10em;"><a href="#" class="sortByName" data-sorted="true">Name</a></th>
                            <th style="width: 10em;"><a href="#" class="sortByCreation" data-sorted="false">Creation Date</a></th>
                            <th style="width: 10em;"><a href="#" class="sortByOwner" data-sorted="false">Owner</a></th>
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
                <label id="createFromTemplateNameLabel" for="createFromTemplateNameInput">New document name</label>
                <input id="createFromTemplateNameInput" type="text" class="form-control"/>
            </div>
        </form>
    </div>

    <form id="createFromTemplateForm" method="POST" action="#">
        <input id="createFromTemplateNewName" type="hidden" name="newname" />
        <input id="createFromTemplateId" type="hidden" name="template" />
    </form>

</div>

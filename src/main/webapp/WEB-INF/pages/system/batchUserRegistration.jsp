<%@ include file="/common/taglibs.jsp"%>

<style>
    input:invalid, select:invalid, .invalidField {
      border: 2px solid #cc0000;
    }
    .removeBatchRowLnk {
      background-image:url('/images/icons/closeIcon.png');
      background-repeat: no-repeat; 
      padding:4px 7px 3px 25px;
    }
    .errorMsg {
      color: red
    }
    .successMsg {
      color: green
    }
    .batchRowCreated {
      color: green;
      line-height: initial;
      font-size: larger;
    }
    .batchFieldCreated {
      display: block;
      margin-left: 3px;
    }
    .addRowLnk {
      margin: 1.5em 0em 1.5em 0.5em; 
      font-size: 1.3em
    }
</style>

<div id="chooseInputModeDiv" class="bootstrap-custom-flat" style="font-size: 1.3em; margin-top: 1em; text-align:center">
   <button id="batchManualInput" class="btn btn-default" style="width: 10em">
        <span class="ui-button-text"><fmt:message key="system.batchRegistration.button.manualInput" /></span></button>
   <button id="batchCSVInput" class="btn btn-default" style="width: 10em; margin-left: 20px;">
        <span class="ui-button-text"><fmt:message key="system.batchRegistration.button.csvInput" /></span></button>
</div>

<div id="csvInputContent" style="margin: 1em 0 0 5px; display:none">
    <div>
        <rst:hasDeploymentProperty name="cloud" value="true">
            <fmt:message key="system.batchRegistration.textarea.helpText.cloud" />
        </rst:hasDeploymentProperty>
        <rst:hasDeploymentProperty name="cloud" value="false">
            <rst:hasDeploymentProperty name="standalone" value="true">
                <fmt:message key="system.batchRegistration.textarea.helpText.standalone" />
            </rst:hasDeploymentProperty>
            <rst:hasDeploymentProperty name="standalone" value="false">
                <fmt:message key="system.batchRegistration.textarea.helpText.sso" />
            </rst:hasDeploymentProperty>
        </rst:hasDeploymentProperty>
        
        <br/>
        <fmt:message key="system.batchRegistration.helpText.exampleCSV.intro" />
        <a href="/resources/batch_registration_example.csv"><fmt:message key="system.batchRegistration.helpText.exampleCSV.label" /></a>.

       <rst:hasDeploymentProperty name="ldapEnabled" value="true">
            <br /><fmt:message key="system.batchRegistration.helpText.ldap.csv" />
       </rst:hasDeploymentProperty>
    </div>
    
    <div class="bootstrap-custom-flat" style="float:right">
      <button id="csvUploadBtn" class="btn btn-default" style="width: 10em; margin: -2em 2em 1em;">
        <span class="ui-button-text"><fmt:message key="system.batchRegistration.button.csvUpload" /></span></button>
    </div>

    <textarea id="csvInputContentArea" rows="8" cols="130" ></textarea>

    <div class="bootstrap-custom-flat" style="font-size: 1.3em; margin-top: 1em; text-align:center">
        <button id="csvParseInputContentBtn" class="btn btn-default" style="width: 10em">
            <span class="ui-button-text"><fmt:message key="system.batchRegistration.button.csvLoad" /></span></button>
    </div>
</div>

<div id="tableRowTemplates" style="display:none"> 

    <div id="userToCreateRowTemplate" style="display:none">
      <table>
        <tr>
            <td><input type="text" data-fieldname="firstName" value="{{firstName}}" style="width:80px;" required></td>
            <td><input type="text" data-fieldname="lastName" value="{{lastName}}" style="width:107px;" required></td>
            <td><input type="email" data-fieldname="email" value="{{email}}" style="width:187px;" required></td>
            <rst:hasDeploymentProperty name="cloud" value="true">
                <td><input type="text" data-fieldname="affiliation" value="{{affiliation}}" style="width:80px;" required></td>
            </rst:hasDeploymentProperty>
            <td>
                <select name="roleSelection" data-fieldname="role" style="width:60px;" required>
                  <option value="ROLE_USER">User</option>
                  <option value="ROLE_PI">PI</option>
                  <option value="ROLE_ADMIN">Community Admin</option>
                  <shiro:hasRole name="ROLE_SYSADMIN">
                    <option value="ROLE_SYSADMIN">System Admin</option>
                  </shiro:hasRole>
                </select>
            </td>
            <td><input type="text" data-fieldname="username" value="{{username}}" class="batchUsernameInput" style="width:107px;"></td>
            <rst:hasDeploymentProperty name="standalone" value="true">
              <rst:hasDeploymentProperty name="ldapAuthenticationEnabled" value="false">
                <td><input type="text" data-fieldname="password" value="{{password}}" style="width:80px;"></td>
              </rst:hasDeploymentProperty>
            </rst:hasDeploymentProperty>
            <td style="width: 160px;"><span class="batchCreateStatus"></span></td>
            <td><a class="removeBatchRowLnk" href="#"><fmt:message key="system.batchRegistration.button.removeRow" /></a></td>
        </tr>
      </table>
    </div>

    <div id="groupToCreateRowTemplate" style="display:none">
      <table>
        <tr data-uniquename="{{uniqueName}}">
            <td><input type="text" data-fieldname="displayName" value="{{displayName}}" style="width:133px;" required></td>
            <td><input type="text" data-fieldname="pi" value="{{pi}}" style="width:107px;" required></td>
            <td><input type="text" data-fieldname="otherMembers" value="{{otherMembers}}" style="width:470px;"></td>
            <td style="width: 160px;"><span class="batchCreateStatus"></span></td>
            <td><a class="removeBatchRowLnk" href="#"><fmt:message key="system.batchRegistration.button.removeRow" /></a></td>
        </tr>
      </table>
    </div>
    
    <div id="communityToCreateRowTemplate" style="display:none">
      <table>
        <tr data-uniquename="{{uniqueName}}">
            <td><input type="text" data-fieldname="displayName" value="{{displayName}}" style="width:133px;" required></td>
            <td><input type="text" data-fieldname="admins" value="{{admins}}" style="width:220px;" required></td>
            <td><input type="text" data-fieldname="labGroups" value="{{labGroups}}" style="width:357px;"></td>
            <td style="width: 160px;"><span class="batchCreateStatus"></span></td>
            <td><a class="removeBatchRowLnk" href="#"><fmt:message key="system.batchRegistration.button.removeRow" /></a></td>
        </tr>
      </table>
    </div>
    
</div>

<form id="batchCreateForm" style="display:none">

  <h3>
    <span id="manualRegistrationTablesHelpText" style="display:none">
        <fmt:message key="system.batchRegistration.tables.helpText.manualRegistration" />
    </span>
    <span id="csvInputTablesHelpText" style="display:none">
        <fmt:message key="system.batchRegistration.tables.helpText.csvInput" />
    </span>
  </h3>

  <div id="usersToCreate" style="display:none; margin-top: 2em;">
    <h3>Users to create</h3>
    <table style="margin-bottom: 0.5em">
        <thead> 
            <tr>
                <th>First Name</th>
                <th>Last Name</th>
                <th>Email</th>
                <rst:hasDeploymentProperty name="cloud" value="true">
                    <th>Affiliation</th>
                </rst:hasDeploymentProperty>
                <th>Role</th>
                <th>Username</th>
                <rst:hasDeploymentProperty name="standalone" value="true">
                  <rst:hasDeploymentProperty name="ldapAuthenticationEnabled" value="false">
                    <th>Password</th>
                  </rst:hasDeploymentProperty>
                </rst:hasDeploymentProperty>
                <th>Status</th>
                <th style="background-color: white"></th>
            </tr>
        </thead>
        <tbody id="usersToCreateTableBody">
        </tbody>
    </table>
    <rst:hasDeploymentProperty name="ldapEnabled" value="true">
        <div style="font-size: larger;">
            <fmt:message key="system.batchRegistration.helpText.ldap.usersTable" />
        </div>
    </rst:hasDeploymentProperty>
  </div>
  <div class="addRowLnk">
    <a id="addBatchUserLnk" href="#" ><fmt:message key="system.batchRegistration.button.addUser" /></a>
  </div>

  <div id="groupsToCreate" style="display:none; margin-top: 2em;">
    <h3>Groups to create</h3>
    <table>
        <thead> 
            <tr>
                <th>Name</th>
                <th>PI</th>
                <th>Members</th>
                <th>Status</th>
                <th style="background-color: white"></th>
            </tr>
        </thead>
        <tbody id="groupsToCreateTableBody">
        </tbody>
    </table>
  </div>
  <div class="addRowLnk">
    <a id="addBatchGroupLnk" href="#" ><fmt:message key="system.batchRegistration.button.addGroup" /></a>
  </div>

  <div id="communitiesToCreate" style="display:none; margin-top: 2em;">
    <h3>Communities to create</h3>
    <table>
        <thead> 
            <tr>
                <th>Name</th>
                <th>Community Admins</th>
                <th>LabGroups</th>
                <th>Status</th>
                <th style="background-color: white"></th>
            </tr>
        </thead>
        <tbody id="communitiesToCreateTableBody">
        </tbody>
    </table>
  </div>
  <div class="addRowLnk">
    <a id="addBatchCommunityLnk" href="#" ><fmt:message key="system.batchRegistration.button.addCommunity" /></a>
  </div>

</form>

<div class="bootstrap-custom-flat" style="font-size: 1.3em;line-height:1.3em; text-align:center; margin-bottom:1.5em">
    <button id="batchCreateAllBtn" class="btn btn-default" style="width: 12em; display:none">
        <span class="ui-button-text"><fmt:message key="system.batchRegistration.button.createAll" /></span></button>
</div>

<div id="batchServerMessages">

    <div id="batchServerMessagesHeader" style="display:none; font-size: larger; margin-top: 30px; margin-bottom: 10px;">
        Server messages:
    </div>
    <div id="batchServerErrorMsgs" class="errorMsg"></div>
    <div id="batchServerSuccessMsgs" class="successMsg"></div>
    
</div>

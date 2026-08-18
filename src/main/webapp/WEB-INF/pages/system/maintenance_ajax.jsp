<%@ include file="/common/taglibs.jsp"%>

<br/>
<br/>
<div>
    <spring:message code="system.downtime.header.currentlyLoggedUsers" />
    <c:forEach items="${applicationScope.userNames.activeUsers}" var="user" varStatus="loop">
        ${user}<c:if test="${!loop.last}">, </c:if>
    </c:forEach>
</div>

<br/>
<hr />
<br/>

<div id="nextMaintenance">
    <div id="noFutureMaintenances" style="display:none">
        <h2><spring:message code="system.downtime.header.noMaintenance" /></h2>
    </div>

    <div id="nextMaintenanceDetails" style="display:none">
      <form id="nextMaintenanceForm">
        <div id="activeMaintenanceHeader"><h3><spring:message code="system.downtime.header.activeMaintenance" /></h3></div>
        <div id="nextMaintenanceHeader"><h3><spring:message code="system.downtime.header.nextMaintenance" /></h3></div>

        <div id="usersCanLoginMsg">
            <spring:message code="system.downtime.message.userCanLoginUntil" />
            <span id="usersCanLoginUntil"></span>
        </div>
        <div id="usersCannotLoginMsg"><spring:message code="system.downtime.message.userCannotLogin" /></div>
        <br />

        <table>
            <tr>
                <td><label for="nextStartDate"><spring:message code="system.downtime.label.starts" /></label></td>
                <td><input id="nextStartDate" type="text" required /><td>
            </tr>
            <tr>
                <td><label for="nextEndDate"><spring:message code="system.downtime.label.ends" /></label></td>
                <td><input id="nextEndDate" type="text" required /><td>
            </tr>
            <tr>
                <td><label for="nextMessage"><spring:message code="system.downtime.label.message" /></label></td>
                <td><textarea id="nextMessage" rows="2" cols="50"></textarea><td>
            </tr>
        </table>

        <div id="nextMaintenanceButtons" class="bootstrap-custom-flat">
            <button type="submit" class="btn btn-default">
                <span class="ui-button-text"><spring:message code="system.downtime.button.update" /></span>
            </button>
            <button id="stopUserLogin" type="button" class="btn btn-default" style="width:15em;">
                <span class="ui-button-text"><spring:message code="system.downtime.button.stopLogin" /></span>
            </button>
            <button id="deleteNextMaintenance" type="button" class="deleteMaintenanceButton btn btn-default">
                <span class="ui-button-text"><spring:message code="common:actions.delete" /></span>
            </button>
            <button id="finishActiveMaintenance" type="button" class="btn btn-default" style="width:10em;">
                <span class="ui-button-text"><spring:message code="system.downtime.button.finish" /></span>
            </button>
        </div>
      </form>
    </div>
</div>

<div id="otherMaintenances" style="display:none">
    <br/>
    <hr />
    <br/>
    <h3><spring:message code="system.downtime.header.otherMaintenances" /></h3>
    <table>
        <thead>
            <tr>
                <th style="width: 10em;"><spring:message code="system.downtime.label.starts" /></th>
                <th style="width: 10em;"><spring:message code="system.downtime.label.ends" /></th>
                <th style="width: 20em;"><spring:message code="system.downtime.label.message" /></th>
                <th></th>
            </tr>
        </thead>
        <tbody id="otherMaintenancesTableBody">
        </tbody>
    </table>
</div>

<div id="oldMaintenances" style="display:none">
    <br/>
    <hr />
    <br/>
    <h3><spring:message code="system.downtime.header.oldMaintenances" /></h3>
    <table>
        <thead>
            <tr>
                <th style="width: 10em;"><spring:message code="system.downtime.label.starts" /></th>
                <th style="width: 10em;"><spring:message code="system.downtime.label.ends" /></th>
                <th style="width: 20em;"><spring:message code="system.downtime.label.message" /></th>
                <th></th>
            </tr>
        </thead>
        <tbody id="oldMaintenancesTableBody">
        </tbody>
    </table>
</div>
<script id = "otherMaintenanceRowTemplate" type="text/x-mustache-template">
        <tr>
            <td>{{maint.formattedStartDate}}</td>
            <td>{{maint.formattedEndDate}}</td>
            <td>{{maint.message}}</td>
            <td>
              <div class="bootstrap-custom-flat">
                <button id="deleteNextMaintenance" data-id="{{maint.id}}" class="deleteMaintenanceButton btn btn-default">
                    <span class="ui-button-text"><spring:message code="common:actions.delete" /></span>
                </button>
              </div>
            </td>
        </tr>
</script>
<br/>
<hr />
<br/>

<div class="bootstrap-custom-flat">
	<button id="scheduleNewMaintenance" class="btn btn-default" style="width:15em;">
	    <span class="ui-button-text"><spring:message code="system.downtime.button.addNewMaintenance" /></span>
	</button>
</div>

<div id="scheduleNewMaintenanceDiv" style="display:none">
    <form id="scheduleNewMaintenanceForm">
        <h3><spring:message code="system.downtime.header.scheduleNew" /></h3>
        <table>
            <tr>
                <td><label for="newStartDate"><spring:message code="system.downtime.label.starts" /></label></td>
                <td><input id="newStartDate" type="text" required /><td>
            </tr>
            <tr>
                <td><label for="newEndDate"><spring:message code="system.downtime.label.ends" /></label></td>
                <td><input id="newEndDate" type="text" required /><td>
            </tr>
            <tr>
                <td><label for="newMessage"><spring:message code="system.downtime.label.message" /></label></td>
                <td><textarea id="newMessage" rows="2" cols="50"></textarea><td>
            </tr>
        </table>

        <div class="bootstrap-custom-flat">
          <button type="submit" class="btn btn-default">
            <span class="ui-button-text"><spring:message code="common:actions.add" /></span>
          </button>
        </div>
    </form>
</div>

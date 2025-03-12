<%@ include file="/common/taglibs.jsp"%>

<script src="<c:url value='/scripts/jquery-simply-countable/jquery.simplyCountable.js'/>"></script>

<!--  component of message creation dialog -->
<form:form class="requestForm" modelAttribute="request">
<form:hidden  path="userRole"/>
<table style="margin: 0">
  <tr>
      <td colspan="2">
          <form:errors class="error" path="*"/>
      </td>
  </tr>
  <tr>
      <td><form:label path="recipientnames">To</form:label></td>
      <td><form:textarea class="recipientsMessageArea form-control" path="recipientnames" rows="1" spellcheck="false"/></td>
  </tr>
  <tr>
      <td><form:label path="messageType">Request Type</form:label></td>
      <td>
          <form:select  class="msgTypes form-control" path="messageType">
              <form:options items="${request.allMessageTypes}"  itemLabel="label"/>
          </form:select>
      </td>
  </tr>
  <tr>
      <td><form:label path="optionalMessage"> Optional Message<br/><br/>
          <span id="optionalMessageCounter"></span>/2000 </form:label>
      </td>
      <td><form:textarea class="optionalMessageArea form-control" path="optionalMessage" rows="6" /></td>
  </tr>
  <tr>
      <td><form:label class="date" path="requestedCompletionDate"> Completion Date?</form:label></td>
      <td><form:input style="position:relative; z-index: 0;" class="date datepickerz-index form-control" 
          path="requestedCompletionDate"/>
      </td>
  </tr>

  <form:hidden path="recordId"/>
  <form:hidden path="groupId"/>
  <form:hidden path="targetFinderPolicy"/>

</table>
</form:form>


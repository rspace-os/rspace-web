<%@ include file="/common/taglibs.jsp"%>

<form:form id ='rsFormSharingForm' method='POST' modelAttribute="formSharingCommand" >
<table id="templateShareConfig" > 
 

 <tr>
              <td><spring:message code="dialogs.share.groupHeader"/></td>

              <td>
              <form:radiobuttons class="alwaysActive" path="groupOptions" items="${groupAvailableOptionList}"/>
                <a href="#" class="templateSharingForm_help">?</a>
 			    <p class="templateSharingForm_helpContent" style="display:none">
 			     <spring:message code="form.sharingConfig.groupHelpText"/> <a href="#" style="font-size: 75%;color:blue" class="templateSharingForm_help_hide"><spring:message code="form.sharingConfig.hideLabel"/></a></p>
 			   </td>
 </tr>
 <tr>
              <td><spring:message code="form.sharingConfig.worldLabel"/></td>

              <td><form:radiobuttons class="alwaysActive" path="worldOptions" items="${worldAvaialbleOptionList}"/>
              	<a href="#" class="templateSharingForm_help">?</a>
 			    <p class="templateSharingForm_helpContent" style="display:none">
 			     <spring:message code="form.sharingConfig.worldHelpText"/> <a href="#" style="font-size: 75%; color:blue" class="templateSharingForm_help_hide"><spring:message code="form.sharingConfig.hideLabel"/></a></p>
              </td>
 </tr>

</table>
<form:hidden path="formId"/>
</form:form>
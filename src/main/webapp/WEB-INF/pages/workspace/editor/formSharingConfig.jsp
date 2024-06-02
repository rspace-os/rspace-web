<%@ include file="/common/taglibs.jsp"%>

<form:form id ='rsFormSharingForm' method='POST' modelAttribute="formSharingCommand" >
<table id="templateShareConfig" > 
 

 <tr>
              <td>Group</td>
              
              <td>
              <form:radiobuttons class="alwaysActive" path="groupOptions" items="${groupAvailableOptionList}"/>
                <a href="#" class="templateSharingForm_help">?</a>
 			    <p class="templateSharingForm_helpContent" style="display:none"> 
 			     Configures access to other members of groups that you belong to. <a href="#" style="font-size: 75%;color:blue" class="templateSharingForm_help_hide">Hide</a></p>
 			   </td>
 </tr>	 
 <tr>
              <td>World</td>
              
              <td><form:radiobuttons class="alwaysActive" path="worldOptions" items="${worldAvaialbleOptionList}"/>
              	<a href="#" class="templateSharingForm_help">?</a>
 			    <p class="templateSharingForm_helpContent" style="display:none"> 
 			     Configures public access to all users. <a href="#" style="font-size: 75%; color:blue" class="templateSharingForm_help_hide">Hide</a></p>
              </td>
 </tr>	 

</table>
<form:hidden path="formId"/>
</form:form>
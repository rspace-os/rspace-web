<%@ include file="/common/taglibs.jsp"%>

<h3>Revision history for ${currentDoc.name}, created by <b>${currentDoc.createdBy}</b>
    on <fmt:formatDate value="${currentDoc.creationDate}"/></h3>
<a href="/workspace?settingsKey=${settingsKey}">Back to Workspace</a>

<table id="documentHistory" cellspacing="0" width="100%"> 
    <tr>
      <td width="20%"></td>
      <td width="5%"></td>
      <td width="10%"></td>
      <td width="15%"></td>
      <td width="15%"></td>
      <td width="20%"></td>
      <td width="15%" colspan="2" align="right">
        <input id="revisionSearch" style="height:20px;color:#FFF;background-color:#6389A8;border:1px solid #6389A8;font-weight:normal;border-radius:3px;cursor:pointer;margin:0 30px 4px 0;" value="Search Revisions" type="button"></input>
      </td>
    </tr>
    <tr class="table_cell spaceOver">
      <th class="search_bar_cell" style="text-align:center;">Search Revisions By:</th>
      <th class="search_bar_cell"></th>
      <th class="search_bar_cell"></th>
      <th class="search_bar_cell" style="text-align:center;padding-right:20px;">Modified by</th>
      <th class="search_bar_cell" style="text-align:center;padding-right:20px;">Date Range</th>
      <th class="search_bar_cell" style="text-align:center;padding-right:20px;">Type of Modification</th>
      <th class="search_bar_cell"></th>
      <th class="search_bar_cell"></th>
    </tr>
    <tr class="spaceUnder spaceOver">
      <td class="search_bar_cell" width="20%" align="center"></td>
      <td class="search_bar_cell" width="5%"></td>
      <td class="search_bar_cell" width="10%"></td>
      <td class="search_bar_cell" width="15%"><input class="search_term" type="text" id="search_modifiedBy" name="modifiedBy" value="${searchCriteria.modifiedBy}" aria-label="Modified by"/></td>
      <td class="search_bar_cell" width="20%"><input class="search_term" type="text" id="search_dateRange" name="dateRange" value="${searchCriteria.dateRange}" aria-label="Date range"/></td>
      <td class="search_bar_cell" width="20%">
         <c:if test="${fn:length(fieldNames) gt 1}">
           <select id="search_modifiedFields" class="search_term" multiple="multiple" id="search_modifiedField" name="modifiedField" style="margin-top:5px;">
             <c:forEach items="${fieldNames}" var="fieldName" >
             <option value="${fieldName}"
                  <c:if test="${fn:contains( fn:join(searchCriteria.selectedFields, ','), fieldName)}">
                          selected="selected"
                  </c:if>>
                   ${fieldName}
             </option>
             </c:forEach>
           </select>
         </c:if>
      </td>
      <td class="search_bar_cell" width="5%"><input id="search_dateButton" style="height:24px;color:#FFF;background-color:#6389A8;border:1px solid #6389A8;background: -moz-linear-gradient(center top , #6389A8, #6389A8 100%) repeat scroll 0% 0% transparent;box-shadow: 0px 0px 0px rgba(255, 255, 255, 0.6) inset;font-weight:normal;"  value="Go" type="button"></input></td>
      <td class="search_bar_cell" width="10%"><input id="clearSearch" style="height:24px;color:#FFF;background-color:#6389A8;border:1px solid #6389A8;background: -moz-linear-gradient(center top , #6389A8, #6389A8 100%) repeat scroll 0% 0% transparent;box-shadow: 0px 0px 0px rgba(255, 255, 255, 0.6) inset;font-weight:normal;border-radius:3px;cursor:pointer;"  value="Clear" type="button"></input></td>
    </tr>
    <tr><td height="10px"></td></tr>
    <tr class="table_cell">
      <th style="text-align:center;">Name</th>
      <th style="text-align:center;">Version</th>
      <th style="text-align:center;">ID</th>
      <th style="text-align:center;">Modified by</th>
      <th style="text-align:center;">Modification Date</th>
      <th style="text-align:center;">Modification Details</th>
      <th style="text-align:center;">Options</th>
      <th></th>
    </tr>
    <c:forEach var="auditedDoc" items="${history}">
      <tr class="table_cell">
        <td style="border-bottom:2px solid #FFF;">${auditedDoc.record.name}</td>
        <td style="border-bottom:2px solid #FFF;">${auditedDoc.record.userVersion.version}</td>
        <td style="border-bottom:2px solid #FFF;">
           <a href="/globalId/${auditedDoc.record.oidWithVersion}">${auditedDoc.record.oidWithVersion}</a>
        </td>
        <td style="border-bottom:2px solid #FFF;">${auditedDoc.record.modifiedBy}</td>
        <td style="border-bottom:2px solid #FFF;"><fmt:formatDate pattern="yyyy-MM-dd HH:mm:ss" value="${auditedDoc.record.modificationDateAsDate}"/></td>
        <td class="delta-msg" style="border-bottom:2px solid #FFF;">
           <c:choose>
             <c:when test="${auditedDoc.revisionTypeString eq 'MOD'}">
                     ${auditedDoc.record.deltaStr}
             </c:when>
             <c:otherwise>
                     ${auditedDoc.revisionTypeString}
             </c:otherwise>          
           </c:choose>
        </td>
        <c:url value="/workspace/editor/structuredDocument/audit/view?recordId=${auditedDoc.record.id}&revision=${auditedDoc.revision}&settingsKey=${settingsKey}"
                     var="viewDocument"></c:url>
        <td style="border-bottom:2px solid #FFF;"><a href="${viewDocument}">View</a></td>
         
        <td style="border-bottom:2px solid #FFF;">
          <%-- only allow restore if document not signed and has edit permission--%>
          <rs:chkRecordPermssns record="${auditedDoc.record}" user="${user}" action="WRITE">
            <c:if test="${isSigned eq false and auditedDoc.record.deltaStr ne 'DELETED'}">
              <a href="#" class="restore">Restore</a>
              <input type="hidden" name="revision" value="${auditedDoc.revision}"/>
            </c:if>
          </rs:chkRecordPermssns>
        </td>
      </tr>
    </c:forEach>
</table>

<div class="tabularViewBottom bootstrap-custom-flat">
    <axt:paginate_new paginationList="${paginationList}"/>
</div>

<%@ include file="/common/taglibs.jsp"%>
<!-- code for handling display of messaging buttons on document/notebook toolbar !-->

<%-- Dialog for creating request --%>
<div id="createRequestDlg" style="display: none">
    <div id="createRequestDlgContent"></div>
</div>

<style>
    .extMessageRequestDlgContent {
        padding: 5px;
    }

    .extMessageRequestLabel { 
        width: 60px;
        display: inline-block; 
    }
    
    .extMessageRequestInputDiv {
        padding: 5px;
    }
    
    .extMessageRequestMessage {
        height: 80px;
        width: 320px;
        vertical-align: middle;
    }
    
    .extMessageRequestMessageLegend_singleDoc, .extMessageRequestMessageLegend_manyDocs {
        padding: 5px 10px 0 64px;
    }
</style>

<script type="text/template"  type="text/x-mustache-template" id="extMessageRequestDlg-template">
<div class="extMessageRequestDlg" id="extMessageRequestDlg_{{name}}" style="display: none">
    <div class="extMessageRequestDlgContent">
        <h3><spring:message code="messaging.sendMessageToLabel"/></h3>

        <div class="extMessageRequestInputDiv">
            <label class="extMessageRequestLabel" for="extMessageChannelsSelect"><spring:message code="messaging.channelLabel"/></label>
            <select class="channelSelect">
               {{#channels}}
                 <option value={{id}}>{{label}}</option>
               {{/channels}}
            </select>
        </div>
        <div class="extMessageRequestInputDiv">
            <label class="extMessageRequestLabel" for="extMessageRequestMessage"><spring:message code="messaging.messageLabel"/></label>
            <textarea class="extMessageRequestMessage" placeholder="<spring:message code='messaging.messagePlaceholder'/>"/></textarea>
            <div class="extMessageRequestMessageLegend_singleDoc">
                <i><spring:message code="messaging.singleDocHelp"/></i>
            </div>
            <div class="extMessageRequestMessageLegend_manyDocs">
                <i><spring:message code="messaging.manyDocsHelp"/></i>
            </div>
        </div>
    </div>
</div>
</script>


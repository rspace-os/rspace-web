<%@ include file="/common/taglibs.jsp"%>
<!-- code for handling display of messaging buttons on document/notebook toolbar !-->

<%-- Dialog for creating request --%>
<div id="createRequestDlg" style="display: none">
    <div id="createRequestDlgContent"></div>
</div>

<%-- Dialogs for the ownCloud / NextCloud TinyMCE import plugins. Both plugins
     share the same dialog ids, so render once when either integration is
     configured for this deployment. --%>
<c:if test="${not empty applicationScope['RS_DEPLOY_PROPS']['owncloud.url'] or not empty applicationScope['RS_DEPLOY_PROPS']['nextcloud.url']}">
    <div id="owncloudDialog" title="Import From ownCloud / NextCloud"></div>

    <div id="owncloudLoginDialog" title="Log in to ownCloud" style="display: none">
       <div class="container">
          <label for="owncloudUsernameField"><b>Username</b></label>
          <input class="owncloudLoginField" id="owncloudUsernameField" type="text" placeholder="Enter Username" name="owncloudUsernameField" required>

          <label for="owncloudPasswordField"><b>Password</b></label>
          <input class="owncloudLoginField" id="owncloudPasswordField" type="password" placeholder="Enter Password" name="owncloudPasswordField" required>
        </div>
    </div>
</c:if>

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
        <h3>Send a message to {{label}}</h3>
 
        <div class="extMessageRequestInputDiv">
            <label class="extMessageRequestLabel" for="extMessageChannelsSelect">Channel</label>
            <select class="channelSelect">
               {{#channels}}
                 <option value={{id}}>{{label}}</option>
               {{/channels}}
            </select>
        </div>
        <div class="extMessageRequestInputDiv">
            <label class="extMessageRequestLabel" for="extMessageRequestMessage">Message</label>
            <textarea class="extMessageRequestMessage" placeholder="Check my document!"/></textarea>
            <div class="extMessageRequestMessageLegend_singleDoc">
                <i>The message will include a link to the current document. Only RSpace users with access 
                    to the document will be able to use the link.</i>
            </div>
            <div class="extMessageRequestMessageLegend_manyDocs">
                <i>The message will include links to selected documents. Only RSpace users with access 
                    to these documents will be able to use the links.</i>
            </div>
        </div>
    </div>
</div>
</script>


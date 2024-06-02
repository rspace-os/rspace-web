<%@ include file="/common/taglibs.jsp"%>

<link href="/styles/pages/netfiles/netfiles.css" rel="stylesheet" />

<jsp:include page="netFilesMustacheTemplates.html" />

<script type="text/javascript">
	var USER_PUBLIC_KEY = '${PUBLIC_KEY}';
	var nfsGalleryView = true;
</script>

<div id="nfsMainPage">

    <div id="nfsIntroHeader" style="display:none">
        <div id="noFileSystemsMsg" style="display:none">
            <h6><spring:message code="netfilestores.msg.welcome.no.filesystems" /></h6>
        </div>
        <div id="fileStoreIntro" style="display:none">
            <h6><spring:message code="netfilestores.msg.welcome" /></h6>
        </div>
        <div id="fileStoreIntroNoFilestores" style="display:none">
            <h6><spring:message code="netfilestores.msg.welcome.no.filestores" /></h6>
        </div>
    </div>
    <div id="nfsInitErrorHeader" style="display:none">
        <h6><spring:message code="netfilestores.msg.init.error" /></h6>
    </div>
    <div id="fileStoreBrowsingHeader" style="display:none">
        <h5 id="activeFileTreeTitle"></h5>
        <p><spring:message code="netfilestores.msg.browseHint" /></p>
    </div>
    <div id="fileStoreAddHeader" style="display:none">
        <h6><spring:message code="netfilestores.add.browse.msg"/></h6>
    </div>
    <div id="fileSystemSelectHeader" style="display:none">
        <h6><spring:message code="netfilestores.add.select.system.msg"/></h6>
    </div>

    <div id="fileSystemSelectPanel" style="display:none; margin-top: 15px">
        <table id="currentlyUsedFileSystemsList" cellspacing="5" cellpadding="0" 
                style="width:247px; display:inline-block; vertical-align:top">
            <tr>
                <th colspan="2"><spring:message code="netfilestores.add.select.system.current.header"/></th>
            </tr>
        </table>
        <input id='otherFileSystemsBtn' type='button' class="btn btn-default"
                value='<spring:message code="netfilestores.add.select.system.others.button"/>' /> 
        <table id="otherFileSystemsList" cellspacing="5" cellpadding="0" 
                style="width:247px; display:inline-block; vertical-align:top">
            <tr>
                <th colspan="2"><spring:message code="netfilestores.add.select.system.all.header"/></th>
            </tr>
        </table>
    </div>

    <div id="nfsFileTreePanel" style="display:none"></div>

    <jsp:include page="netFiles_login.jsp" />

</div>

<!--  nfsInfoPanel is placed on right hand-side (info panel) on page load -->
<div id="nfsInfoPanel">
    <div id="nfsLoggedUserPanel" style="visibility:hidden;">
        <p> 
            <span id="nfsLoggedUserMsg"></span>
            <br />
            <span id="nfsLoggedUsername" style="margin-left: 30px;"></span>
            <input id="nfsLogout" type='button' class="btn btn-default" title="Logout from the File System"
                value='<spring:message code="netfilestores.button.logout.label"/>' />
        </p> 
    </div>
    
    <div id="nfsFileStoresPanel">
        <table id="fileStoreList" cellspacing="0" cellpadding="0" style="width:247px">
            <tr>
                <th colspan="2"><spring:message code="netfilestores.mystores.title"/></th>
            </tr>
            <tr>
                <td colspan="2">
                    <input id='addUserFileStoreBtn' type='button' class="btn btn-default" title="Add new Filestore" 
                        value='<spring:message code="netfilestores.button.add"/>' />
                    <input id='deleteUserFileStoreBtn' type='button' class="btn btn-default" title="Remove selected Filestore"
                        value='<spring:message code="netfilestores.button.delete"/>' />
                </td>
            </tr>
        </table>
    </div>
</div>

<div id='nfsSaveFileStoreDialog' title='Save Filestore' style='display:none'>
    <h6 style="margin-left:20px; margin-top: 10px"><spring:message code="netfilestores.add.dialog.header"/></h6>
    <table style="padding: 0.5em 0 0 1.5em; margin: 0;">
        <tr>
            <td><label for="nfsNewFolderPath"><spring:message code="netfilestores.add.dialog.path.label"/></label></td>
            <td><span id='nfsNewFolderPath' style="display:inline-block; max-width: 260px;"></span>
            	<span id='nfsSaveCurrentDirLabel' style="display:none;"><spring:message code="netfilestores.current.directory.label"/></span>
            </td>
        </tr>
        <tr>
            <td><label for="nfsNewFolderName"><spring:message code="netfilestores.add.dialog.name.label"/></label></td>
            <td><input id='nfsNewFolderName' type='text'></input></td>
        </tr>
    </table>
    
    <div id="nfsSaveStoreError" style="margin: 0.5em 0 0 1.5em; color:red;"></div>
</div>


<div id="nfsPublicKeyInfoPanel" style="display:none">

    <div id="publicKeyLoginIntro" style="display:none">
        <h6><spring:message code ="netfilestores.pubkey.login.label" /> 
            <span class="nfsFileSystemName"></span></h6>
        <input id="nfsPubKeyLogin" type='button' class="btn btn-default"
            value="<spring:message code="netfilestores.pubkey.button.login.label" arguments="${sessionScope.userInfo.username}" />"
         />
        <div class="nfsError" style="margin-left:20px; margin-top: 10px; color:red;"></div>
        <p id="publicKeyDetailsLink" style="display:none">
            <a href="#" id="showPublicKey">
                <spring:message code="netfilestores.pubkey.link.show.key.label" />
            </a>
        </p>
    </div>
     
    <div id="publicKeyGenerateIntro" style="display:none">
        <h6><spring:message code="netfilestores.msg.pubkey.register.welcome" /> 
            <span class="nfsFileSystemName"></span></h6>
    </div>

    <div id="publicKeyGenerateButton" style="display:none">
        <p><input id="nfsGenerateKey" type='button' class="btn btn-default" 
               title="<spring:message code="netfilestores.pubkey.button.generate.title" />"
               value="<spring:message code="netfilestores.pubkey.button.generate.label" />"
            />
        </p>
    </div>

    <div id="publicKeyDetails" style="display:none">
        <div id="publicKeyDetailsIntro" style="display:none">
            <h6><spring:message code="netfilestores.pubkey.key.details.intro" /></h6>
        </div>
        <div id="publicKeyAfterGenerateInstructions" style="display:none">
            <h6><spring:message code="netfilestores.pubkey.after.generate" /></h6>
        </div>
        <div id="publicKeyStringDiv">
            <p id="publicKeyString"></p>
            <input id="copyToClipboardButton" type="button" class="btn btn-default" style="display:none;"
                data-clipboard-target="publicKeyString"
                title="<spring:message code="netfilestores.pubkey.button.copy.title" />" 
                value="<spring:message code="netfilestores.pubkey.button.copy.label" />"
             />
        </div>
    </div>

    <div id="publicKeyRegisterDetailsLink" style="display:none">
        <p><a id="showPublicKeyInstructions" href="#">
            <spring:message code="netfilestores.pubkey.link.show.registration.label" />
         </a></p>
    </div>

    <div id="publicKeyRegisterDetailsLong" style="display:none">
        <h6><spring:message code="netfilestores.pubkey.registration.header" /></h6>
        <h6><ol>
            <li>
                <span id="copyToClipboardInstructions" style="display:none;">
                    <spring:message code="netfilestores.pubkey.registration.first.step" /></span>
                <span id="copyToClipboardNoButtonInstructions">
                    <spring:message code="netfilestores.pubkey.registration.first.step.no.copy.button" /></span>
            </li>
            <spring:message code="netfilestores.pubkey.registration.other.steps" />
        </ol></h6>

        <p><input type="button" id="dataStoreRegisterButton" class="btn btn-default"
                value="<spring:message code="netfilestores.pubkey.button.registration.dialog.label" />"/>
        </p>
    </div>
    
    <div id="publicKeyAfterRegistrationMsg" style="display:none">
        <h6><spring:message code="netfilestores.pubkey.registration.after" /></h6>
    </div>
    
    <div id="dataStoreRegistrationDiv" style="display:none">
        <iframe id="dataStoreIframe" frameborder="0" scrolling="yes" marginheight="0" marginwidth="0">
           <!--  src="url" -->
        </iframe>
    </div>
    
</div>

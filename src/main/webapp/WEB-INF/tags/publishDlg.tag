<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>
<%@ taglib uri="http://researchspace.com/tags" prefix="rst" %>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %>
<%@ taglib prefix="f" uri="http://researchspace.com/functions" %>

		<!-- publish dialog, initially hidden -->
	<div id="publish-dialog" style="display: none; padding-bottom: 0;">
		<div id="publish_accordion" class="accordion" style="min-height: 434px;">
			<c:if test="${publicdocs_allow_seo}">
				<h3 id="publishPublicHeader"><spring:message code="dialogs.publish.internetHeader"/>
					<span class="publishSelected boldtext warning" style="float:right;"></span>
				</h3>
				<div id="publishPublicContents" class="folderShare">
					<p class="boldtext"><spring:message code="dialogs.publish.internetViewableWarning"/></p>
					<p><spring:message code="dialogs.publish.notebookContentsWarning"/></p>
					<p class="boldtext"><spring:message code="dialogs.publish.searchEngineIndexWarning"/></p>
					<div style="padding-top:10px">
					<label ><spring:message code="dialogs.publish.summaryDescriptionLabel"/>
						<textarea maxlength="200" rows="5" id="publicationDescription" style="width:95%;height:auto"></textarea>
					</label>
					</div>
						<p><label for="displayContactDetails"><spring:message code="dialogs.publish.displayContactDetailsLabel"/></label><input type="checkbox" id="displayContactDetails"  aria-label="<spring:message code='dialogs.publish.displayContactDetailsLabel'/>"></input</>
					<p><spring:message code="dialogs.publish.confirmInstruction"/></p>
					<input id="make_public_confirmation"></input>
					<button id="clearPublish" class="btn btn-primary">
						<spring:message code="common:actions.clear"/>
					</button>
				</div>
			</c:if>
				<h3 id="publishPublicLinkHeader"><spring:message code="dialogs.publish.linkHeader"/>
					<span class="publishLinkSelected boldtext warning" style="float:right;"></span>
				</h3>
				<div id="publishPublicLinkContents" class="folderShare">
					<p class="boldtext"><spring:message code="dialogs.publish.internetViewableWarning"/></p>
					<p><spring:message code="dialogs.publish.linkNotebookContentsWarning"/></p>
					<p class="boldtext"><spring:message code="dialogs.publish.searchEngineNoIndexWarning"/></p>
					<div style="padding-top:10px">
						<label><spring:message code="dialogs.publish.summaryDescriptionLabel"/>
							<textarea maxlength="200" rows="5" id="publicationLinkDescription"
									  style="width:95%;height:auto"></textarea>
						</label>
					</div>
					<p><label for="displayLinkContactDetails"><spring:message code="dialogs.publish.displayContactDetailsLabel"/></label><input type="checkbox" id="displayLinkContactDetails"
																 aria-label="<spring:message code='dialogs.publish.displayContactDetailsLabel'/>"></input</>
					<p><spring:message code="dialogs.publish.confirmInstruction"/></p>
					<input id="make_public_link_confirmation"></input>
					<button id="clearPublishLink" class="btn btn-primary">
						<spring:message code="common:actions.clear"/>
					</button>
				</div>
		</div>

		<div id="publishFooter" style="border: 1px solid #B6B6B6; padding: 5px; line-height: 1.4em">
			<img src="/images/notice.png" style="height: 14px; vertical-align: text-top;"/>
			<spring:message code="publish.notice.manage.sharedDocument"/>
		</div>
		<div  style="border: 1px solid #B6B6B6; padding: 5px; line-height: 1.4em">
			<img src="/images/notice.png" style="height: 14px; vertical-align: text-top;"/>
			<spring:message code="publish.notice.documentationLinkText" var="publishDocumentationLinkText"/>
			<spring:message code="common:help.publicationOfDocuments" var="publicationOfDocumentsHelpSlug"/>
			<spring:message code="publish.notice.documentation">
				<spring:argument value='<a href="${f:helpDocsUrl(publicationOfDocumentsHelpSlug)}" target="_blank">${publishDocumentationLinkText}</a>'/>
			</spring:message>
		</div>

	</div>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>
<%@ taglib uri="http://researchspace.com/tags" prefix="rst" %>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %>

<fmt:bundle basename="bundles.workspace.workspace">
	<!-- publish dialog, initially hidden -->
	<div id="publish-dialog" style="display: none; padding-bottom: 0;">
		<div id="publish_accordion" class="accordion" style="min-height: 434px;">
			<c:if test="${publicdocs_allow_seo}">
				<h3 id="publishPublicHeader">Publish on the internet
					<span class="publishSelected boldtext warning" style="float:right;"></span>
				</h3>
				<div id="publishPublicContents" class="folderShare">
					<p class="boldtext">This will be viewable by non RSpace users.</p>
					<p>If this is a notebook, all current and future contents will be public.</p>
					<p class="boldtext">Search engines will index and may cache the published document.</p>
					<div style="padding-top:10px">
					<label >Summary description, max length 200 characters:
						<textarea maxlength="200" rows="5" id="publicationDescription" style="width:95%;height:auto"></textarea>
					</label>
					</div>
					<p><label for="displayContactDetails">Display contact details?</label><input type="checkbox" id="displayContactDetails"  aria-label="Display contact details"></input</>
					<p> Please type "confirm"</p>
					<input id="make_public_confirmation"></input>
					<button id="clearPublish" class="btn btn-primary">
						Clear
					</button>
				</div>
			</c:if>
				<h3 id="publishPublicLinkHeader">Publish a link
					<span class="publishLinkSelected boldtext warning" style="float:right;"></span>
				</h3>
				<div id="publishPublicLinkContents" class="folderShare">
					<p class="boldtext">This will be viewable by non RSpace users.</p>
					<p>If this is a notebook, all current and future contents will be viewable.</p>
					<p class="boldtext">Search engines will be instructed not to index the document, so it should not
						appear in search results.</p>
					<div style="padding-top:10px">
						<label>Summary description, max length 200 characters:
							<textarea maxlength="200" rows="5" id="publicationLinkDescription"
									  style="width:95%;height:auto"></textarea>
						</label>
					</div>
					<p><label for="displayLinkContactDetails">Display contact details?</label><input type="checkbox" id="displayLinkContactDetails"
																	 aria-label="Display contact details"></input</>
					<p> Please type "confirm"</p>
					<input id="make_public_link_confirmation"></input>
					<button id="clearPublishLink" class="btn btn-primary">
						Clear
					</button>
				</div>
		</div>

		<div id="publishFooter" style="border: 1px solid #B6B6B6; padding: 5px; line-height: 1.4em">
			<img src="/images/notice.png" style="height: 14px; vertical-align: text-top;"/>
			<fmt:message key="publish.notice.manage.shared.document"/>
		</div>
		<div  style="border: 1px solid #B6B6B6; padding: 5px; line-height: 1.4em">
			<img src="/images/notice.png" style="height: 14px; vertical-align: text-top;"/>
			<fmt:message key="publish.notice.documentation"/>
		</div>

	</div>


</fmt:bundle>

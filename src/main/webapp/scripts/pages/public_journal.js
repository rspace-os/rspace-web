/* jshint maxerr: 100 */
// notebookName is added in notebookEditor.jsp and is already escaped
/**
 * Modifications for public/published documents that re-route urls so that SSO will allow them and also hide parts of document not intended for public view
 **/
const publicJournalExtensions = {
  toolbarModifications : () => {
    $( '<img id="ajaxLoadingImg" src="/public/images/ajax-loading.gif "/>' ).replaceAll('#ajaxLoadingImg');
  },
  header : (notebookName, publicationSummary, contactDetails, publishOnInternet) => {
    if(publishOnInternet) {
      addMetaToHeader(publicationSummary);
    } else {
      forbidBots();
    }
    const part1 = "<H1 class='publicTitle'>"+notebookName+"</H1><H3 class='publicSummary'>"+publicationSummary+"</H3>";
    return part1 + (contactDetails?"<H3 class='publicSummary'>contact: "+contactDetails+"</H3>":"");
  },
  springMVCUrlReroutePrefix : "/public/publicView",
  imageSrcPrefix: "/public",
  scriptsPrefix: "/public",
  topHeaderModify: () => {
    hideEditButtons();
    $(".rs-global-id").hide();
  },
  loadPageModify: () => {
    makeImageLinksPublic();
    disableLinkedDocumentsAndLinkedFiles();
    makeSVGPublic();
    hideInfoPopups();
    hidePreviewButtons();
  },
  selectFileTreeBrowserRecordById: ()=> {},
  isFileTreeBrowserVisible: () => {return false},
  useFirstRecord: true
}

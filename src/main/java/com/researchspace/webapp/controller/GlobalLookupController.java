package com.researchspace.webapp.controller;

import static java.lang.String.format;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/** Given a globalID will attempt to look it up based on its type */
@Controller("globalLookupController")
@RequestMapping("/globalId")
public class GlobalLookupController extends BaseController {

  protected static final String FORM_REDIRECT_URL = "/workspace/editor/form/edit/";

  /**
   * @param oidString OID in format AB12345 where 'AB' is a GlobalIdPrefix and 12345 is a database
   *     id.
   * @return A redirect URL to the correct URL to view the resource.
   * @throws if the oid lookup is not supported. Lookup is supported for :
   *     <ul>
   *       <li>Formsjuu
   *       <li>Notebooks
   *       <li>StructuredDocuments
   *       <li>Folders
   *       <li>Groups
   *       <li>Inventory: Samples, Subsamples, Templates, Containers
   *     </ul>
   */
  @GetMapping("/{oid}")
  public String globalIdLookUp(@PathVariable(value = "oid") String oidString) {
    if (!GlobalIdentifier.isValid(oidString)) {
      throw new IllegalArgumentException(format("Invalid syntax of oid [%s]", oidString));
    }
    GlobalIdentifier oid = new GlobalIdentifier(oidString);
    String redirectString = getRedirect(oid);
    return "redirect:" + redirectString;
  }

  private static Map<GlobalIdPrefix, String> prefixToUrl = new HashMap<>();

  /*
   * Currently supported lookups.
   */
  static {
    prefixToUrl.put(GlobalIdPrefix.FL, WorkspaceController.ROOT_URL);
    prefixToUrl.put(GlobalIdPrefix.FM, FORM_REDIRECT_URL);
    prefixToUrl.put(GlobalIdPrefix.SD, StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL);
    prefixToUrl.put(GlobalIdPrefix.NB, NotebookEditorController.ROOT_URL);
    prefixToUrl.put(GlobalIdPrefix.GL, FileDownloadController.STREAM_URL);
    prefixToUrl.put(GlobalIdPrefix.GF, GalleryController.GALLERY_URL);
    prefixToUrl.put(GlobalIdPrefix.ST, GalleryController.GALLERY_ITEM_URL);
    prefixToUrl.put(GlobalIdPrefix.GP, GroupController.GROUPS_VIEW_URL);
    prefixToUrl.put(GlobalIdPrefix.US, "/userform?userId=");
    prefixToUrl.put(GlobalIdPrefix.SA, "/inventory/sample/");
    prefixToUrl.put(GlobalIdPrefix.SS, "/inventory/subsample/");
    prefixToUrl.put(GlobalIdPrefix.IT, "/inventory/sampletemplate/");
    prefixToUrl.put(GlobalIdPrefix.IC, "/inventory/container/");
    prefixToUrl.put(GlobalIdPrefix.BE, "/inventory/search?parentGlobalId=BE");
  }

  private String getRedirect(GlobalIdentifier oid) {
    GlobalIdPrefix prefix = oid.getPrefix();
    String url = null;

    if (!oid.hasVersionId()) {
      url = prefixToUrl.get(prefix);
      if (url != null) {
        url = addTrailingSlashIfNeeded(url, prefix);
        url += oid.getDbId();
      }
    }
    if (oid.hasVersionId() && GlobalIdPrefix.SD.equals(oid.getPrefix())) {
      url = StructuredDocumentController.STRUCTURED_DOCUMENT_AUDIT_VIEW_URL;
      url += "?globalId=" + oid;
    }
    if (oid.hasVersionId() && GlobalIdPrefix.GL.equals(oid.getPrefix())) {
      url = FileDownloadController.STREAM_URL;
      url += "/" + oid.getDbId() + "?version=" + oid.getVersionId();
    }

    if (url == null) {
      throw new UnsupportedOperationException(
          format("Currently this OID [%s] is unsupported.", oid));
    }
    return url;
  }

  private String addTrailingSlashIfNeeded(String url, GlobalIdPrefix prefix) {
    if (GlobalIdPrefix.US.equals(prefix) || GlobalIdPrefix.BE.equals(prefix)) {
      return url;
    }
    if (!url.endsWith("/")) {
      url = url + "/";
    }
    return url;
  }
}

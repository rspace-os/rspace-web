package com.researchspace.webapp.controller;

import com.axiope.search.SearchUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.model.views.TreeViewItem;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Contains the various views used by the file tree plugin */
@Controller
@RequestMapping("/fileTree/ajax")
public class FileTreeController extends BaseController {

  // Helper class for some controller actions
  @Data
  @NoArgsConstructor
  private static class ParametersForListDirectories {
    private String dir;

    private boolean showNotebooks = false;

    /**
     * sh if true, will filter out notebooks the user owns. Meant for sharing files in others'
     * notebooks.
     */
    private boolean showNotebooksForShare = false;

    /** if true, will get user's root directory if dir == / */
    @Getter private boolean initialLoad = false;
  }

  private static final PaginationCriteria<BaseRecord> DEFAULT_TREE_PAGINATION =
      PaginationCriteria.createForClass(
          BaseRecord.class, "name", SortOrder.ASC.toString(), 0L, Integer.MAX_VALUE);

  /**
   * Ajax call to retrieve all records in a directory, returned in the format of tree view items,
   * because it is used in tree views.
   *
   * @param dir parent directory whose child records to receive. ID or /
   * @param showGallery whether to show the gallery folder if dir == /
   * @return list of records in the given folder
   * @throws UnsupportedEncodingException if the directory cannot be parsed
   */
  @GetMapping("/files")
  public AjaxReturnObject<List<TreeViewItem>> listFilesAsJson(
      @RequestParam String dir,
      @RequestParam(defaultValue = "false", required = false) boolean showGallery,
      Principal principal)
      throws UnsupportedEncodingException {

    User user = userManager.getUserByUsername(principal.getName());
    return new AjaxReturnObject<>(listFiles(dir, showGallery, user), null);
  }

  /**
   * Shows all files for childrenTree View of Workspace and 'link to record' dialog .<br>
   * Should be a 'GET' but needs to be a post for the FileTree viewer.
   *
   * @param dir either must be '/' or an id terminated by '\' e.g. '123/'
   * @param showGallery whether to show the gallery folder if dir == /
   * @throws UnsupportedEncodingException if the directory cannot be parsed
   */
  @PostMapping("/filesInModel")
  public String listFilesInModel(
      @RequestParam("dir") String dir,
      @RequestParam(value = "showGallery", defaultValue = "true", required = false)
          boolean showGallery,
      Model model,
      Principal p)
      throws UnsupportedEncodingException {
    User user = userManager.getUserByUsername(p.getName());

    model.addAttribute("dir", dir);
    model.addAttribute("records", listFiles(dir, showGallery, user));

    //  quick fix just so that workspace page has access to UI settings. Editor pages
    //  will also load this of course. Ideally this should get its own controller so that it can
    //  be used everywhere.
    model.addAttribute(
        "clientUISettingsPref", getUserPreferenceValue(user, Preference.UI_CLIENT_SETTINGS));

    return "workspaceTreeView";
  }

  /**
   * Helper method used by the 2 above actions to retrieve files in the given folder
   *
   * @param dir the folder
   * @param showGallery whether or not to include the gallery folder
   * @param user which user is browsing their files
   * @return list of files in the given directory
   * @throws UnsupportedEncodingException if the directory cannot be parsed
   */
  private List<TreeViewItem> listFiles(String dir, boolean showGallery, User user)
      throws UnsupportedEncodingException {
    dir = java.net.URLDecoder.decode(dir, "UTF-8");

    Folder targetFolder = null;
    ISearchResults<TreeViewItem> children = null;
    PaginationCriteria<TreeViewItem> pgCrit =
        PaginationCriteria.createDefaultForClass(TreeViewItem.class);
    pgCrit.setGetAllResults();
    if ("/".equals(dir)) { // is user home folder
      targetFolder = folderManager.getRootFolderForUser(user);
      targetFolder.setName("Home");
      children = folderManager.getFolderListingForTreeView(targetFolder.getId(), pgCrit, user);
      if (!showGallery) hideGalleryFolders(children);
    } else {
      Long fId = getFolderIdFromDir(dir);
      targetFolder = folderManager.getFolder(fId, user);
      children = folderManager.getFolderListingForTreeView(targetFolder.getId(), pgCrit, user);
      if (targetFolder.isRootFolder()) {
        // hide other users Gallery folders as well, see RSPAC-1494
        if (!showGallery) hideGalleryFolders(children);
      }
    }
    List<TreeViewItem> results = new ArrayList<>();
    if (children != null) { // can be null sometimes folder retrieval fails
      results = children.getResults();
    }
    if (log.isDebugEnabled()) {
      results.forEach(
          ti ->
              log.debug(
                  "{} is notebook ? {},  is folder? {}",
                  ti.toString(),
                  ti.isNotebook(),
                  ti.isFolder()));
    }

    return results;
  }

  private void hideGalleryFolders(ISearchResults<TreeViewItem> children) {
    // don't show Gallery folder for link dialog
    // RSPAC-5
    children.getResults().removeIf(br -> Folder.MEDIAROOT.equals(br.getName()) && br.isRootMedia());
  }

  /**
   * Shows all directories for use in 'move' dialog. It is POST because that is required by the
   * jQuery file tree.
   *
   * @param dir
   * @param model
   * @return
   * @throws UnsupportedEncodingException
   */
  @PostMapping("/directoriesInModel")
  public String listDirectoriesInModel(
      ParametersForListDirectories params, Model model, Principal principal)
      throws UnsupportedEncodingException {

    List<BaseRecord> results =
        listDirectories(
            params.dir,
            params.showNotebooks,
            params.showNotebooksForShare,
            params.initialLoad,
            principal);

    model.addAttribute("results", results);

    return "moveDialogTreeView";
  }

  /**
   * Returns directories in directory dir, or just the one if initialLoad = true. Used for folder
   * selectors.
   *
   * @throws UnsupportedEncodingException if cannot decode the directory name.
   */
  @GetMapping("/directories")
  @ResponseBody
  public AjaxReturnObject<List<TreeViewItem>> listDirectoriesAsJson(
      ParametersForListDirectories params, Principal principal)
      throws UnsupportedEncodingException {

    List<BaseRecord> records =
        listDirectories(
            params.dir,
            params.showNotebooks,
            params.showNotebooksForShare,
            params.initialLoad,
            principal);
    List<TreeViewItem> directories =
        records.stream().map(TreeViewItem::fromBaseRecord).collect(Collectors.toList());

    return new AjaxReturnObject<>(directories, null);
  }

  private List<BaseRecord> listDirectories(
      String dir,
      boolean showNotebooks,
      boolean showNotebooksForShare,
      boolean initialLoad,
      Principal principal)
      throws UnsupportedEncodingException {

    User user = userManager.getUserByUsername(principal.getName());
    dir = java.net.URLDecoder.decode(dir, "UTF-8");

    // if it's '/' or a group folder , we're loading for the first time and just show home folder
    if (initialLoad) {
      Folder targetFolder = null;
      if ("/".equals(dir)) {
        targetFolder = folderManager.getRootFolderForUser(user);
        targetFolder.setName("Home");
      } else {
        Long folderId = getFolderIdFromDir(dir);
        targetFolder = folderManager.getFolder(folderId, user);
      }
      List<BaseRecord> rc = new ArrayList<>();
      rc.add(targetFolder);
      return rc;
    } else {
      Long targetFolderId = getFolderIdFromDir(dir);
      RecordTypeFilter moveDialogFilter =
          showNotebooks || showNotebooksForShare
              ? RecordTypeFilter.MOVE_DIALOGFILTER_PLUS_NOTEBOOKS
              : RecordTypeFilter.MOVE_DIALOGFILTER;
      List<BaseRecord> results =
          recordManager
              .listFolderRecords(targetFolderId, DEFAULT_TREE_PAGINATION, moveDialogFilter)
              .getResults();
      if (showNotebooksForShare) {
        filterOutNotebooksForShare(results, user);
      }
      return results;
    }
  }

  private void filterOutNotebooksForShare(List<BaseRecord> results, User user) {
    for (Iterator<BaseRecord> it = results.iterator(); it.hasNext(); ) {
      BaseRecord record = it.next();
      if (record.isNotebook()) {
        if (record.getOwner().equals(user) // can't share into own notebook
            || !permissionUtils.isPermitted(record, PermissionType.WRITE, user)) {
          it.remove();
        }
      }
    }
  }

  private Long getFolderIdFromDir(String dir) {
    if (dir.endsWith("/")) {
      dir = dir.substring(0, dir.length() - 1);
    }
    return Long.parseLong(dir);
  }

  /**
   * Shows folders for Gallery move action.
   *
   * @param dir
   * @param mediatype the name of the top level directory to include
   * @return
   * @throws UnsupportedEncodingException
   * @throws Exception
   */
  @PostMapping("/gallery")
  public String listGalleryDirectories(
      @RequestParam("dir") String dir,
      @RequestParam("mediatype") String mediatype,
      Model model,
      Principal p)
      throws UnsupportedEncodingException {

    User user = userManager.getUserByUsername(p.getName());
    dir = java.net.URLDecoder.decode(dir, "UTF-8");

    boolean isTopFolder = "/".equals(dir);
    Folder targetFolder = null;
    if (isTopFolder) {
      targetFolder = folderManager.getMediaFolderFromURLPath(dir, user);
    } else {
      targetFolder = folderManager.getInitialisedFolder(getFolderIdFromDir(dir), user, null);
    }

    if (targetFolder != null) {
      List<Folder> folders = new ArrayList<Folder>();
      for (BaseRecord record : targetFolder.getChildrens()) {
        // we only want to show regular active folders
        if (record.isFolder() && !record.isDeleted()) {
          if (!isTopFolder || record.getName().equals(mediatype)) {
            folders.add((Folder) record);
          }
        }
      }
      SearchUtils.sortList(folders, DEFAULT_TREE_PAGINATION);
      List<Folder> results =
          new SearchResultsImpl<Folder>(folders, DEFAULT_TREE_PAGINATION, folders.size())
              .getResults();
      model.addAttribute("results", results);
    }
    return "moveDialogTreeView";
  }
}

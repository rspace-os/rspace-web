package com.researchspace.webapp.controller;

import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.GalleryFilterCriteria;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalExportType;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Breadcrumb;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.service.DetailedRecordInformationProvider;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.chemistry.ChemistryProvider;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

/** This controller handle all the operations in the gallery. */
@Controller
@RequestMapping({"/gallery", "/public/publicView/gallery"})
public class GalleryController extends BaseController {

  public static final String GALLERY_VIEW = "gallery";

  /** URL to gallery page */
  public static final String GALLERY_URL = "/" + GALLERY_VIEW;

  /** URL to gallery item */
  public static final String GALLERY_ITEM_URL = "/gallery/item";

  private static final String NETFILES_GALLERY_MEDIA_TYPE = "NetworkFiles";

  private static final int NUMBER_OF_RECORDS_ON_GALERY_PAGE = 20;
  private static final int MAX_FOLDER_NAME_LENGTH = 100;
  private static final int MAX_IDS_TO_PROCESS = 50;

  private @Autowired RecordDeletionManager deletionManager;
  private @Autowired MediaManager mediaManager;
  private @Autowired DetailedRecordInformationProvider infoProvider;
  private @Autowired RSChemElementManager rsChemElementManager;
  private @Autowired ChemistryProvider chemistryProvider;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  /**
   * Gets plain HTML template for mediaGallery
   *
   * @return
   */
  @GetMapping("/ajax/galleryDialog")
  public String getDialog(Model model) {
    User currUser = userManager.getAuthenticatedUserInSession();
    boolean rc = isDMPEnabled(currUser);
    model.addAttribute("dmpEnabled", rc);
    return "mediaGallery";
  }

  @GetMapping()
  public String gallery(Model model) {

    User user = userManager.getAuthenticatedUserInSession();
    model.addAttribute("dmpEnabled", isDMPEnabled(user));
    setPublicationAllowed(model, user);
    addGroupAttributes(model, user);
    return GALLERY_VIEW;
  }

  private void addGroupAttributes(Model model, User usr) {
    Set<Group> groups = groupManager.listGroupsForUser();
    model.addAttribute("groups", groups);
    List<User> users = Group.getUniqueUsersInGroups(groups, User.LAST_NAME_COMPARATOR, usr);
    model.addAttribute("uniqueUsers", users);
  }

  private void setPublicationAllowed(Model model, User user) {
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(user, "public_sharing"));
  }

  /** List Gallery folder by id of Gallery item: */
  @GetMapping("/item/{itemId}")
  public ModelAndView listGalleryFolderByItemId(
      @PathVariable("itemId") Long itemId,
      @RequestParam(name = "term", required = false) String term,
      Model model,
      Principal principal) {
    User user = userManager.getAuthenticatedUserInSession();
    Record galleryItem = recordManager.get(itemId);
    assertAuthorisation(user, galleryItem, PermissionType.READ);
    return listGalleryFolderById(galleryItem.getParent().getId(), term, model, principal);
  }

  /**
   * Gets gallery view for specific folder id. Sets up mediaType in model, so front-end can select
   * correct subgallery.
   *
   * @param folderId
   * @param model
   * @param principal
   * @return
   */
  @GetMapping("/{folderId}")
  public ModelAndView listGalleryFolderById(
      @PathVariable("folderId") Long folderId,
      @RequestParam(name = "term", required = false) String term,
      Model model,
      Principal principal) {

    User user = getUserByUsername(principal.getName());
    Folder folder;
    try {
      folder = folderManager.getFolder(folderId, user);
    } catch (ObjectRetrievalFailureException | AuthorizationException ex) {
      throw new IllegalArgumentException("access denied");
    }

    Folder galleryRootFolder = folderManager.getGalleryRootFolderForUser(user);
    RSPath pathToFolder = folder.getShortestPathToParent(galleryRootFolder);

    int numberOfParents = pathToFolder.size();
    if (numberOfParents == 0) {
      throw new IllegalArgumentException("provided folderId doesn't point to Gallery folder");
    }

    if (numberOfParents > 1) {
      Folder subgalleryFolder = (Folder) pathToFolder.get(1).get();

      model.addAttribute("currentFolderId", folderId);
      model.addAttribute("mediaType", subgalleryFolder.getName());
      if (!StringUtils.isBlank(term)) {
        model.addAttribute("term", term);
      }
      model.addAttribute("dmpEnabled", isDMPEnabled(user));
      Breadcrumb bcrumb = breadcrumbGenerator.generateBreadcrumb(folder, subgalleryFolder);
      model.addAttribute("galleryBcrumb", bcrumb);
    }

    return new ModelAndView(GALLERY_VIEW);
  }

  /**
   * Handling links to 'netfiles' subgallery. This is a pseudo-gallery that doesn't exist in folder
   * structure, so it can't be opened by providing folder id.
   */
  @GetMapping("/netfiles")
  public ModelAndView listFilestoresSubgallery(Model model) {
    model.addAttribute("mediaType", NETFILES_GALLERY_MEDIA_TYPE);
    return new ModelAndView(GALLERY_VIEW);
  }

  /**
   * Gets media gallery folder/file listing.
   *
   * @param mediatype the type of media to load, one of the tab names in the media gallery.
   * @param currentFolderId - the parent folder id, or 0 if we're looking up root media file
   * @param foldersOnly when true returns only folders, otherwise gallery items and folders
   * @param pgCrit pagination number
   * @param filterCriteria additional gallery filter criteria used to filter by name
   * @return AjaxReturnObject of {@link GalleryData} objects for populating media gallery.
   */
  @GetMapping("/getUploadedFiles")
  @ResponseBody
  public AjaxReturnObject<GalleryData> getUploadedFiles(
      @RequestParam("mediatype") String mediatype,
      @RequestParam("currentFolderId") long currentFolderId,
      @RequestParam(value = "foldersOnly", required = false, defaultValue = "false")
      boolean foldersOnly,
      PaginationCriteria<BaseRecord> pgCrit,
      GalleryFilterCriteria filterCriteria) {
    User user = userManager.getAuthenticatedUserInSession();
    // It's a trick to show parent folder on the
    Folder galleryItemParent = recordManager.getGallerySubFolderForUser(mediatype, user);

    boolean isOnRoot = isOnGalleryRoot(currentFolderId, galleryItemParent);

    GalleryData galleryData = new GalleryData(isOnRoot);
    if (isOnRoot) {
      galleryData.setItemGrandparentId(galleryItemParent.getId());
    } else {
      galleryItemParent = folderManager.getFolder(currentFolderId, user);
      if (galleryItemParent.getParent() != null) {
        galleryData.setItemGrandparentId(galleryItemParent.getParent().getId());
      } else {
        Set<RecordToFolder> parentsOfSharedFolder = galleryItemParent.getParents();
        for (RecordToFolder r2f : parentsOfSharedFolder) {
          if (r2f.getFolder().getOwner().equals(user)) {
            galleryData.setItemGrandparentId(r2f.getFolder().getId());
          }
        }
      }
    }
    galleryData.setParentId(galleryItemParent.getId());

    int numberOfRecords = getNumberOfRecordsOnGalleryPage(isOnRoot);
    pgCrit.setResultsPerPage(numberOfRecords);

    RecordTypeFilter galleryMove = new RecordTypeFilter(EnumSet.of(
                RecordType.FOLDER,
                RecordType.ROOT_MEDIA,
                RecordType.SHARED_GROUP_FOLDER_ROOT,
                RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT,
                RecordType.API_INBOX),
            // excluded
            EnumSet.of(
                RecordType.NORMAL_EXAMPLE
                // removed for APiInbox
                // RecordType.SYSTEM
            ));


    RecordTypeFilter recordTypeFilter =
        foldersOnly ? galleryMove : RecordTypeFilter.GALLERY_FILTER;
    ISearchResults<BaseRecord> records =
        recordManager.getGalleryItems(
            galleryItemParent.getId(), pgCrit, filterCriteria, recordTypeFilter, user);
    if (records == null) {
      return new AjaxReturnObject<>(galleryData, null);
    }

    List<RecordInformation> results = new ArrayList<RecordInformation>();
    for (BaseRecord baseRecord : records.getResults()) {
      RecordInformation recordInfo = baseRecord.toRecordInfo();
      if (baseRecord instanceof EcatDocumentFile) {
        EcatDocumentFile doc = (EcatDocumentFile) baseRecord;
        recordInfo.addType(getEcatDocumentFileType(mediatype, doc.getDocumentType()));
      }

      recordInfo.setParentId(galleryItemParent.getId());
      recordInfo.setOnRoot(isOnRoot);
      results.add(recordInfo);
    }

    ISearchResults<RecordInformation> result =
        new SearchResultsImpl<RecordInformation>(
            results, records.getPageNumber(), records.getTotalHits(), numberOfRecords);
    log.debug("Returned data size=" + result.getResults().size());

    List<PaginationObject> linkPages =
        PaginationUtil.generatePagination(
            result.getTotalPages(),
            result.getPageNumber(),
            new DefaultURLPaginator("/filesUploaded/", null));
    result.setLinkPages(linkPages);
    galleryData.setItems(result);
    return new AjaxReturnObject<GalleryData>(galleryData, null);
  }

  /**
   * This method is used to retrieve a list of image ids from the root image folder. This method is
   * used in the sketcher (Zwibbler) to load the images.
   *
   * @return AjaxReturnObject<List<Long>>
   */
  @ResponseBody
  @GetMapping("/getImageListFromRootImageFolder")
  public AjaxReturnObject<List<Long>> getImageListFromRootImageFolder() {
    User user = userManager.getAuthenticatedUserInSession();
    Folder galleryItemParent =
        recordManager.getGallerySubFolderForUser(MediaUtils.IMAGES_MEDIA_FLDER_NAME, user);

    PaginationCriteria<BaseRecord> pg = new PaginationCriteria<BaseRecord>();
    pg.setResultsPerPage(Integer.MAX_VALUE);

    ISearchResults<BaseRecord> records =
        recordManager.getGalleryItems(
            galleryItemParent.getId(), pg, null, RecordTypeFilter.GALLERY_FILTER, user);
    List<Long> results = new ArrayList<Long>();
    for (BaseRecord baseRecord : records.getResults()) {
      if (baseRecord instanceof EcatImage) {
        RecordInformation recordInfo = baseRecord.toRecordInfo();
        results.add(recordInfo.getId());
      }
    }

    return new AjaxReturnObject<>(results, null);
  }

  private int getNumberOfRecordsOnGalleryPage(boolean isOnRoot) {
    int numberOfRecords = NUMBER_OF_RECORDS_ON_GALERY_PAGE;
    if (!isOnRoot) {
      numberOfRecords--;
    }
    return numberOfRecords;
  }

  private String getEcatDocumentFileType(String mediatype, String documentType) {
    if (mediatype.equals(Folder.EXPORTS_FOLDER_NAME)) {
      return Folder.EXPORTS_FOLDER_NAME;
    }
    if (documentType.equalsIgnoreCase(MediaUtils.MISC_MEDIA_FLDER_NAME)) {
      return MediaUtils.MISC_MEDIA_FLDER_NAME;
    }
    if (documentType.equalsIgnoreCase(MediaUtils.DMP_MEDIA_FLDER_NAME)) {
      return MediaUtils.DMP_MEDIA_FLDER_NAME;
    }
    return MediaUtils.DOCUMENT_MEDIA_FLDER_NAME;
  }

  /**
   * Method uploads a file on the Media Gallery.
   *
   * <h3>Authorisation notes </h3>
   *
   * This will be saved in the subject's Gallery folder, so does not need explicit access control,
   * unless we're adding this to the Gallery as a side effect of DnDing into a editor, in which case
   * we assert that the subject has write permission on the field's document.
   *
   * @param xfile
   * @param selectedMediaId (optional) id of EcatMediaFile that this upload updated. if provided,
   *     fieldId should be null, as gallery updates are field-independent.
   * @param fieldId (optional) field to which the new file is uploaded
   * @return
   * @throws IOException
   * @throws URISyntaxException
   */
  @PostMapping("/ajax/uploadFile")
  @ResponseBody
  public AjaxReturnObject<RecordInformation> uploadFile(
      @RequestParam("xfile") MultipartFile xfile, // file size limit handled in spring config
      @RequestParam(value = "selectedMediaId", required = false) Long selectedMediaId,
      @RequestParam(value = "targetFolderId", required = false) Long targetFolderId,
      @RequestParam(value = "fieldId", required = false) Long fieldId)
      throws IOException {

    if (selectedMediaId != null && fieldId != null) {
      throw new IllegalArgumentException("selectedMediaId and fieldId shouldn't be both provided");
    }

    User subject = userManager.getAuthenticatedUserInSession();
    InputStream inputStream = xfile.getInputStream();
    String originalFileName = xfile.getOriginalFilename();
    return saveMediaFile(
        inputStream, originalFileName, selectedMediaId, fieldId, targetFolderId, subject);
  }

  private AjaxReturnObject<RecordInformation> saveMediaFile(
      InputStream inputStream,
      String originalFileName,
      Long mediaFileId,
      Long fieldId,
      Long targetFolderId,
      User subject)
      throws IOException {
    try {
      Folder targetFolder = null;
      if (targetFolderId != null) {
        Optional<Folder> optFolder = folderManager.getFolderSafe(targetFolderId, subject);
        if (optFolder.isPresent()) {
          targetFolder = optFolder.get();
        }
      }
      EcatMediaFile media =
          mediaManager.saveMediaFile(
              inputStream,
              mediaFileId,
              originalFileName,
              originalFileName,
              fieldId,
              targetFolder,
              "",
              subject);
      if (media.isChemistryFile() && fieldId == null && mediaFileId == null) {
        // Create Basic Chem Element in order for searching of gallery files to work
        RSChemElement rsChemElement =
            RSChemElement.builder()
                .chemElements(
                    chemistryProvider.convert(((EcatChemistryFile) media).getChemString()))
                .chemElementsFormat(ChemElementsFormat.MRV)
                .ecatChemFileId(media.getId())
                .build();

        ChemicalExportFormat format =
            ChemicalExportFormat.builder()
                .exportType(ChemicalExportType.PNG)
                .height(1000)
                .width(1000)
                .build();
        rsChemElementManager.generateRsChemExportBytes(format, rsChemElement);
        rsChemElementManager.saveChemImagePng(
            rsChemElement, new ByteArrayInputStream(rsChemElement.getDataImage()), subject);
      }
      return new AjaxReturnObject<>(media.toRecordInfo(), null);

    } catch (IllegalStateException e) {
      ErrorList errorList = ErrorList.of("Save action failed [" + e.getMessage() + "]");
      return new AjaxReturnObject<>(null, errorList);
    }
  }

  /**
   * Method imports a file to the Media Gallery from URL. Currently, this is used to import file
   * contents from Box/Dropbox
   *
   * <h3>Authorisation notes </h3>
   *
   * This will be saved in the subject's Gallery folder, so does not need explicit access control
   *
   * @return AjaxReturnObject<RecordInformation>
   * @throws IOException, URISyntaxException
   */
  @PostMapping("/ajax/importFromURL")
  @ResponseBody
  @Deprecated
  public AjaxReturnObject<RecordInformation> importFromURL(
      @RequestParam("url") String urlString,
      @RequestParam(value = "targetFolderId", required = false) Long targetFolderId,
      @RequestParam(value = "filename", required = false) String fileName,
      @RequestParam(value = "username", required = false) String username,
      @RequestParam(value = "password", required = false) String password,
      @RequestParam(value = "accessToken", required = false) String accessToken)
      throws IOException, URISyntaxException {
    // rspac-2263
    throw new UnsupportedOperationException("Upload from URL is not supported");
  }

  /**
   * Method uploads an image file to the Media Gallery from URL.
   *
   * <h3>Authorisation notes </h3>
   *
   * This will be saved in the subject's Gallery folder, so does not need explicit access control
   *
   * @param urlImage
   * @param filenameURL
   * @param mediaType
   * @return AjaxReturnObject<RecordInformation>
   * @throws NoSuchAlgorithmException
   * @throws IOException
   */
  @PostMapping("/ajax/uploadFileFromURL")
  @ResponseBody
  public AjaxReturnObject<RecordInformation> uploadImageFromURL(
      @RequestParam("url") String urlImage,
      @RequestParam("filename") String filenameURL,
      @RequestParam("mediatype") String mediaType)
      throws NoSuchAlgorithmException, IOException {
    throw new UnsupportedOperationException("Upload from URL is not supported");
  }

  /**
   * Called when annotating image or sketching, and loads the Gallery images into RHS of sketcher
   * bar.
   *
   * @param id the image ID
   * @return
   * @throws IOException
   * @throws {@link AuthorizationException} if not resource access not authorized
   */
  @GetMapping("/getViewerImage/{id}")
  public ResponseEntity<byte[]> getViewerImage(@PathVariable("id") String id) throws IOException {

    User user = userManager.getAuthenticatedUserInSession();
    long imageId = Long.parseLong(id);
    EcatImage ecatImage = recordManager.getEcatImage(imageId, true);
    assertAuthorisation(user, ecatImage, PermissionType.READ);

    try (InputStream is = getWorkingOrOriginalImgInputStream(ecatImage)) {
      final HttpHeaders headers = new HttpHeaders();
      setHttpContentTypeHeader(ecatImage, headers);
      setCacheTimeInBrowser(ResponseUtil.YEAR, ecatImage.getModificationDateAsDate(), headers);
      log.info("Loading viewer picture " + id);
      byte[] data = IOUtils.toByteArray(is);
      return new ResponseEntity<byte[]>(data, headers, HttpStatus.CREATED);
    }
  }

  // tries in order:
  // WorkingImageFP, Working ImageBlob, OriginalImage
  private InputStream getWorkingOrOriginalImgInputStream(EcatImage ecatImage) {
    InputStream is = null;
    if (ecatImage.getWorkingImageFP() != null) {
      is = getFPInputStream(ecatImage.getWorkingImageFP());
    } else if (ecatImage.getWorkingImage() != null) {
      is = new ByteArrayInputStream(ecatImage.getWorkingImage().getData());
    } else { // return the original image
      is = getFPInputStream(ecatImage.getFileProperty());
    }
    return is;
  }

  private InputStream getThumbnailImgInputStream(EcatImage ecatImage) {
    InputStream is = null;
    if (ecatImage.getThumbnailImageFP() != null) {
      is = getFPInputStream(ecatImage.getThumbnailImageFP());
    } else if (ecatImage.getImageThumbnailed() != null) {
      is = new ByteArrayInputStream(ecatImage.getImageThumbnailed().getData());
    }
    return is;
  }

  private InputStream getFPInputStream(FileProperty imgSrcFP) {
    InputStream is = null;
    Optional<FileInputStream> fis = fileStore.retrieve(imgSrcFP);
    if (fis.isPresent()) {
      is = fis.get();
    } else {
      log.error("Could not retrieve file {}", imgSrcFP.getId());
    }
    return is;
  }

  /**
   * Creates a new folder in the Gallery
   *
   * @param parentId
   * @param folderName
   * @param isMedia
   * @return
   * @throws {@link AuthorizationException} if no create permission in folder identified by folderId
   */
  @PostMapping("/ajax/createFolder")
  @ResponseBody
  public AjaxReturnObject<Boolean> createFolder(
      @RequestParam("parentId") Long parentId,
      @RequestParam("folderName") String folderName,
      @RequestParam(value = "isMedia", defaultValue = "false", required = false) boolean isMedia) {

    folderName = validateFolderName(folderName);
    User user = userManager.getAuthenticatedUserInSession();
    Folder newRecord = folderManager.createNewFolder(parentId, folderName, user);
    newRecord.setName(folderName);
    folderManager.save(newRecord, user);
    return new AjaxReturnObject<Boolean>(true, null);
  }

  private String validateFolderName(String folderName) {
    if (folderName.length() > MAX_FOLDER_NAME_LENGTH) {
      folderName = folderName.substring(0, MAX_FOLDER_NAME_LENGTH);
    }
    return folderName;
  }

  /**
   * This gets called from a Drag and drop operation in the Media Gallery
   *
   * @param targetFolderId The target folder id
   * @param filesId The list of files/folders to move
   * @param mediatype
   * @return
   */
  @PostMapping("/ajax/moveFiles")
  @ResponseBody
  public AjaxReturnObject<Boolean> moveFiles(
      @RequestParam("folderId") Long targetFolderId,
      @RequestParam("filesId[]") Long[] filesId,
      @RequestParam("mediaType") String mediatype) {

    if (filesId.length > MAX_IDS_TO_PROCESS) {
      return generateTooManyItemsFailureMsg();
    }
    User user = userManager.getAuthenticatedUserInSession();
    Folder target = folderManager.getFolder(targetFolderId, user);

    for (Long id : filesId) {
      if (!target.getId().equals(id)) {
        doMove(user, target, id);
      }
    }
    return new AjaxReturnObject<Boolean>(true, null);
  }

  /**
   * This gets called from a move in the media gallery using the file tree navigator
   *
   * @param filesId
   * @param mediatype
   * @param targetFolderId
   * @return <code>true</code> on success
   */
  @PostMapping("/ajax/moveGalleriesElements")
  @ResponseBody
  public AjaxReturnObject<Boolean> moveGalleriesElements(
      @RequestParam("filesId[]") Long[] filesId,
      @RequestParam("mediaType") String mediatype,
      @RequestParam("target") Long targetFolderId) {

    if (filesId.length > MAX_IDS_TO_PROCESS) {
      return generateTooManyItemsFailureMsg();
    }
    User user = userManager.getAuthenticatedUserInSession();

    Folder galleryItemParent = recordManager.getGallerySubFolderForUser(mediatype, user);

    if (isOnGalleryRoot(targetFolderId, galleryItemParent)) {
      targetFolderId = galleryItemParent.getId();
    }
    Folder target = folderManager.getFolder(targetFolderId, user);

    for (Long id : filesId) {
      if (!target.getId().equals(id)) {
        doMove(user, target, id);
      }
    }
    return new AjaxReturnObject<Boolean>(true, null);
  }

  private static boolean isOnGalleryRoot(long currentFolderId, Folder galleryItemParent) {
    // As per convention UI/Backend if the currentFolder 0 then is root folder
    return galleryItemParent.getId().equals(currentFolderId) || (currentFolderId == 0L);
  }

  private AjaxReturnObject<Boolean> generateTooManyItemsFailureMsg() {
    return new AjaxReturnObject<Boolean>(
        null, ErrorList.of(getText("errors.too.manyitems", MAX_IDS_TO_PROCESS + "")));
  }

  private void doMove(User user, Folder target, Long id) {
    boolean isRecord = isRecord(id);
    if (isRecord) {
      recordManager.move(id, target.getId(), null, user);
    } else {
      long parentFolderId = folderManager.getFolder(id, user).getParent().getId();
      folderManager.move(id, target.getId(), parentFolderId, user);
    }
  }

  /**
   * @param idsToDelete
   * @return
   * @throws DocumentAlreadyEditedException
   * @throws IllegalAddChildOperation
   */
  @PostMapping("/ajax/deleteElementFromGallery")
  @ResponseBody
  public AjaxReturnObject<Boolean> deleteElementFromGallery(
      @RequestParam("idsToDelete[]") Long[] idsToDelete)
      throws IllegalAddChildOperation, DocumentAlreadyEditedException {

    if (idsToDelete.length > MAX_IDS_TO_PROCESS) {
      return generateTooManyItemsFailureMsg();
    }
    User user = userManager.getAuthenticatedUserInSession();
    for (Long id : idsToDelete) {
      boolean isRecord = isRecord(id);
      if (isRecord) {
        deletionManager.deleteRecord(null, id, user);
      } else {
        deletionManager.deleteFolder(null, id, user);
      }
    }
    return new AjaxReturnObject<Boolean>(true, null);
  }

  /**
   * Gets a thumbnail image of an image in the Image section of the Gallery
   *
   * @param imageId the ecatimage id
   * @param unused this is the modification date of the file to get a thumbnail for, its important
   *     this is set to get the latest image for files which have been updated with a new version
   * @return the byte array of the image
   * @throws IOException
   * @throws {@link AuthorizationException} if not resource access not authorized
   */
  @GetMapping("/getThumbnail/{id}/{unused}")
  public ResponseEntity<byte[]> getThumbnail(
      @PathVariable("id") long imageId, @PathVariable("unused") Long unused) throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    EcatImage ecatImage = recordManager.getEcatImage(imageId, true);
    assertAuthorisation(user, ecatImage, PermissionType.READ);

    try (InputStream is = getThumbnailImgInputStream(ecatImage)) {
      log.debug("Loading Thumbnail picture {}", imageId);
      byte[] data = IOUtils.toByteArray(is);
      return getResponseEntityWithImageBytes(ecatImage.getCreationDate(), data);
    }
  }

  /**
   * Gets a thumbnail image of a chemical file in the Chemistry section of the Gallery
   *
   * @param ecatChemFileId the id of the chemistry file
   * @param unused this is the modification date of the file to get a thumbnail for, its important
   *     this is set to get the latest image for files which have been updated with a new version
   * @return the byte array of the image representing the chemical
   */
  @GetMapping("/getChemThumbnail/{id}/{unused}")
  public ResponseEntity<byte[]> getChemThumbnail(
      @PathVariable("id") long ecatChemFileId, @PathVariable("unused") Long unused) {
    User user = userManager.getAuthenticatedUserInSession();
    List<RSChemElement> chemElements =
        rsChemElementManager.getRSChemElementsLinkedToFile(ecatChemFileId, user);
    if (!chemElements.isEmpty()) {
      RSChemElement chemElement = chemElements.get(0);
      log.debug("Loading Chemical Image from Chem Element: {}", chemElement.getId());
      byte[] data = chemElement.getDataImage();
      return getResponseEntityWithImageBytes(chemElement.getCreationDate(), data);

    } else {
      return null;
    }
  }

  private ResponseEntity<byte[]> getResponseEntityWithImageBytes(Date creationDate, byte[] data) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_JPEG);
    setCacheTimeInBrowser(ResponseUtil.YEAR, creationDate, headers);
    return new ResponseEntity<>(data, headers, HttpStatus.OK);
  }

  private void setHttpContentTypeHeader(EcatImage ecatImage, final HttpHeaders headers) {
    if (ecatImage.getExtension().equals("jpeg") || ecatImage.getExtension().equals("jpg")) {
      headers.setContentType(MediaType.IMAGE_JPEG);
    } else if (ecatImage.getExtension().equals("gif")) {
      headers.setContentType(MediaType.IMAGE_GIF);
    } else if (ecatImage.getExtension().equals("png")) {
      headers.setContentType(MediaType.IMAGE_PNG);
    }
  }

  /**
   * Copies one or more records from Gallery, all with the same parent.
   *
   * @param idToCopy A Long [] of record ids of records to copy
   * @param newnames A String of new names for the records
   * @return A
   * @throws {@link AuthorizationException} if not resource access not authorized with 'Copy'
   *     permission
   */
  @PostMapping("/ajax/copyGalleries")
  @ResponseBody
  public AjaxReturnObject<Boolean> copyGalleries(
      @RequestParam("idToCopy[]") Long[] idToCopy,
      @RequestParam("newName[]") String[] newnames,
      Principal principal) {

    // throttle the number of items to copy in 1 go.
    if (idToCopy.length > MAX_IDS_TO_PROCESS) {
      return generateTooManyItemsFailureMsg();
    }
    User user = getUserByUsername(principal.getName());

    for (int i = 0; i < idToCopy.length; i++) {

      Long id = idToCopy[i];
      String newName = newnames[i];
      boolean isRecord = isRecord(id);

      if (isRecord) {
        Record original = recordManager.get(id);
        RecordCopyResult result = recordManager.copy(id, newName, user, null);
        Record copy = (Record) result.getCopy(original);
        log.debug(copy.getSharingACL().getString());
        // needs an addtional save here to persist the copied ACL RSPAC-1270
        recordManager.save(copy, user);

      } else {
        folderManager.copy(id, user, newName);
      }
    }
    return new AjaxReturnObject<Boolean>(true, null);
  }

  @GetMapping("/ajax/getLinkedDocuments/{mediaId}")
  @ResponseBody
  public AjaxReturnObject<List<RecordInformation>> getDocumentsLinkedToAttachment(
      @PathVariable("mediaId") Long mediaId) {
    return new AjaxReturnObject<List<RecordInformation>>(
        mediaManager.getIdsOfLinkedDocuments(mediaId), null);
  }

  /**
   * GEts record information for a list of IDs of EcatMediaFiles If info cannot be retrived, value
   * of map will be null and there will be an error
   *
   * @param ids
   * @param revisions
   * @return
   */
  @ResponseBody
  @GetMapping("/getMediaFileSummaryInfo")
  public AjaxReturnObject<Map<Long, RecordInformation>> getMediaFileSummaryInfo(
      @RequestParam(value = "id[]") Long[] ids,
      @RequestParam(value = "revision[]") Long[] revisions) {

    User user = userManager.getAuthenticatedUserInSession();

    if (revisions.length != ids.length) {
      /* Spring MVC considers incoming 1-element array with empty value to be empty array,
       * so let's assume that's what happened if revisions array is empty */
      if (revisions.length == 0 && ids.length == 1) {
        revisions = new Long[] {null};
      } else {
        throw new IllegalArgumentException("Revisions and ids must be same length");
      }
    }

    Map<Long, RecordInformation> info = infoProvider.getRecordInformation(ids, revisions, user);
    ErrorList el = new ErrorList();
    info.entrySet().stream()
        .filter(e -> e.getValue() == null)
        .map(e -> String.format("Could not retrieve information for id [%d]", +e.getKey()))
        .forEach(msg -> el.addErrorMsg(msg));
    return new AjaxReturnObject<>(info, el);
  }
}

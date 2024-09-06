package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.MediaUtils.getContentTypeForFileExtension;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.FileStoreMetaManager;
import com.researchspace.service.inventory.BasketApiManager;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.service.inventory.impl.InventoryEditLockTracker;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

public class BaseApiInventoryController extends BaseApiController {

  public static final String API_INVENTORY_V1 = "/api/inventory/v1";
  public static final String SAMPLES_ENDPOINT = "/samples";
  public static final String SAMPLE_TEMPLATES_ENDPOINT = "/sampleTemplates";
  public static final String SUBSAMPLES_ENDPOINT = "/subSamples";
  public static final String CONTAINERS_ENDPOINT = "/containers";
  public static final String SEARCH_ENDPOINT = "/search";
  public static final String BARCODES_ENDPOINT = "/barcodes";

  @Autowired
  @Qualifier("compositeFileStore")
  private FileStore fileStore;

  @Autowired private FileStoreMetaManager fileStoreMetaManager;

  protected @Autowired InventoryPermissionUtils invPermissions;
  protected @Autowired InventoryEditLockTracker tracker;

  protected @Autowired SampleApiManager sampleApiMgr;
  protected @Autowired SubSampleApiManager subSampleApiMgr;
  protected @Autowired ContainerApiManager containerApiMgr;
  protected @Autowired BasketApiManager basketApiMgr;

  protected UriComponentsBuilder getInventoryApiBaseURIBuilder() {
    return UriComponentsBuilder.fromHttpUrl(getServerURL()).path(API_INVENTORY_V1);
  }

  protected void setLinksInInventoryRecordInfoList(List<? extends ApiInventoryRecordInfo> result) {
    for (ApiInventoryRecordInfo invRecInfo : result) {
      buildAndAddInventoryRecordLinks(invRecInfo);
    }
  }

  protected void buildAndAddInventoryRecordLinks(ApiInventoryRecordInfo invRecInfo) {
    invRecInfo.buildAndAddInventoryRecordLinks(getInventoryApiBaseURIBuilder());
    addFileLinksForAttachments(invRecInfo);
    addBarcodeLinks(invRecInfo);
  }

  private void addFileLinksForAttachments(ApiInventoryRecordInfo recInfo) {
    List<ApiInventoryFile> allAttachments = recInfo.getAllAttachments();
    if (allAttachments != null) {
      for (ApiInventoryFile file : allAttachments) {
        addInventoryFileLink(file);
      }
    }
  }

  private void addBarcodeLinks(ApiInventoryRecordInfo recInfo) {
    if (recInfo.getBarcodes() != null) {
      for (ApiBarcode barcode : recInfo.getBarcodes()) {
        if (StringUtils.isNotBlank(barcode.getData())) { // rsinv-847
          addBarcodeImageLink(barcode);
        }
      }
    }
  }

  FileProperty getFilePropertyByFileName(String fileName, String userName) {
    Map<String, String> properties =
        Map.ofEntries(
            Map.entry("fileGroup", userName),
            Map.entry("fileName", fileName),
            Map.entry("fileOwner", userName));
    return fileStoreMetaManager.findProperties(properties).stream().findFirst().orElse(null);
  }

  // helper method to stream an image direct to response, from a file identified by the FileProperty
  ResponseEntity<byte[]> doImageResponse(User user, Supplier<FileProperty> fPSupplier)
      throws IOException {

    FileProperty fileProp = fPSupplier.get();
    if (fileProp == null) {
      throw new IllegalArgumentException(" no image found");
    }
    byte[] bytes = getImageBytes(fileProp);
    final HttpHeaders headers = new HttpHeaders();
    String contentType = getContentTypeForFileExtension(getExtension(fileProp.getFileName()));
    MediaType mt = null;
    try {
      mt = MediaType.parseMediaType(contentType);
    } catch (InvalidMediaTypeException e) {
      mt = MediaType.IMAGE_PNG;
    }
    headers.setContentType(mt);
    headers.setCacheControl("max-age=" + ResponseUtil.YEAR);
    if (fileProp.getUpdateDate() != null) {
      headers.setLastModified(fileProp.getUpdateDate().getTime());
    }

    return new ResponseEntity<byte[]>(bytes, headers, HttpStatus.OK);
  }

  private byte[] getImageBytes(FileProperty imageFileProp) throws IOException {

    Optional<FileInputStream> fis = fileStore.retrieve(imageFileProp);
    byte[] data = IOUtils.toByteArray(fis.get());
    return data;
  }

  protected void addInventoryFileLink(ApiInventoryFile file) {
    file.addSelfLink(buildInventoryFileLink(file, false));
    file.addEnclosureLink(buildInventoryFileLink(file, true));
  }

  private String buildInventoryFileLink(ApiInventoryFile file, boolean fileDataLink) {
    String path = FILES_ENDPOINT + "/" + file.getId() + (fileDataLink ? "/file" : "");
    return getInventoryApiBaseURIBuilder().path(path).build().encode().toUriString();
  }

  private void addBarcodeImageLink(ApiBarcode barcode) {
    barcode.addEnclosureLink(buildBarcodeLink(barcode));
  }

  private String buildBarcodeLink(ApiBarcode barcode) {
    String path =
        BARCODES_ENDPOINT
            + "?content="
            + URLEncoder.encode(barcode.getData(), StandardCharsets.UTF_8)
            + "&barcodeType=QR";
    return getInventoryApiBaseURIBuilder().path(path).build().encode().toUriString();
  }

  protected InventoryRecord assertUserCanEditInventoryRecord(
      GlobalIdentifier recordOid, User user) {
    switch (recordOid.getPrefix()) {
      case SA:
      case IT:
        return sampleApiMgr.assertUserCanEditSample(recordOid.getDbId(), user);
      case SS:
        return subSampleApiMgr.assertUserCanEditSubSample(recordOid.getDbId(), user);
      case IC:
        return containerApiMgr.assertUserCanEditContainer(recordOid.getDbId(), user);
      case SF:
        return sampleApiMgr.assertUserCanEditSampleField(recordOid.getDbId(), user);
      default:
        throw new IllegalArgumentException(
            "unsupported global id type: " + recordOid.getIdString());
    }
  }

  protected InventoryRecord assertUserCanReadInventoryRecord(
      GlobalIdentifier recordOid, User user) {
    switch (recordOid.getPrefix()) {
      case SA:
      case IT:
        return sampleApiMgr.assertUserCanReadSample(recordOid.getDbId(), user);
      case SS:
        return subSampleApiMgr.assertUserCanReadSubSample(recordOid.getDbId(), user);
      case IC:
        return containerApiMgr.assertUserCanReadContainer(recordOid.getDbId(), user);
      case SF:
        return sampleApiMgr.assertUserCanReadSampleField(recordOid.getDbId(), user);
      default:
        throw new IllegalArgumentException(
            "unsupported global id type: " + recordOid.getIdString());
    }
  }
}

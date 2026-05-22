/**
 * RSpace API Access your Irods Filestores programmatically. All requests require authentication
 * using an API key set as the value of the header `RSpace-API-Key`.
 */
package com.researchspace.api.v1;

import static com.researchspace.api.v1.GalleryIrodsApi.API_V1_GALLERY_IRODS;

import com.researchspace.api.v1.model.ApiExternalStorageInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.model.User;
import com.researchspace.netfiles.ApiNfsCredentials;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping(API_V1_GALLERY_IRODS)
public interface GalleryIrodsApi {

  String API_V1_GALLERY_IRODS = "/api/v1/gallery/irods";
  String PARAM_FILESTORE_PATH_ID = "filestorePathId";
  String PARAM_RECORD_IDS = "recordIds";

  /***
   * Returns to locations of the irods server when invoked without parameters.
   * If invoked with IDs parameters, returns also the address of the end points to hit in order
   * to complete each operation
   *
   * @param recordIds the array of the record IDs you want to perform an operation
   * @param user
   * @return link and infos regarding the operations and the relative end points
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  ApiExternalStorageInfo getExternalLocationsInfo(List<Long> recordIds, User user);

  /***
   * @deprecated Use {@code POST /api/v1/gallery/filestores/{filestoreId}/copy} (see {@link
   *     GalleryFilestoresApi}) instead. This endpoint delegates to that handler internally and
   *     will be removed in a future release.
   */
  @Deprecated
  @PostMapping(
      value = "/copy",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(code = HttpStatus.OK)
  ApiExternalStorageOperationResult copyToIRODS(
      Set<Long> recordIds,
      Long filestorePathId,
      ApiNfsCredentials credentials,
      BindingResult errors,
      User user)
      throws BindException;

  /***
   * @deprecated Use {@code POST /api/v1/gallery/filestores/{filestoreId}/move} (see {@link
   *     GalleryFilestoresApi}) instead. This endpoint delegates to that handler internally and
   *     will be removed in a future release.
   */
  @Deprecated
  @PostMapping(
      value = "/move",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(code = HttpStatus.OK)
  ApiExternalStorageOperationResult moveToIRODS(
      Set<Long> recordIds,
      Long filestorePathId,
      ApiNfsCredentials credentials,
      BindingResult errors,
      User user)
      throws BindException;
}

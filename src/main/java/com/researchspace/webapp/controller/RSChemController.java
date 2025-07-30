package com.researchspace.webapp.controller;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.Stoichiometry;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemConversionInputDto;
import com.researchspace.model.dtos.chemistry.ChemElementDataDto;
import com.researchspace.model.dtos.chemistry.ChemElementImageUpdateDto;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ChemicalImageDTO;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequestDTO;
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.impl.RSChemService.ChemicalSearchResults;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Methods related to chemical elements. */
@Controller
@RequestMapping({"/chemical", "/public/publicView/chemical"})
public class RSChemController extends BaseController {

  @Autowired private ChemistryService chemistryService;

  /**
   * Method called to save a chemical element to the database, return RSChemElement object.
   *
   * @param chemicalDataDTO The Chemical data to save
   * @param error BindingResult
   * @return AjaxReturnObject<RSChemElement>
   * @throws IOException
   */
  @PostMapping("ajax/saveChemElement")
  @ResponseBody
  public AjaxReturnObject<RSChemElement> saveChemElement(
      @Valid ChemicalDataDTO chemicalDataDTO, BindingResult error) throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    RSChemElement saved = chemistryService.saveChemicalElement(chemicalDataDTO, subject);
    return new AjaxReturnObject<>(saved);
  }

  /***
   * A version of the above saveChemElement method, which accepts the chemical data as a @RequestBody
   * rather than request params to avoid hitting uri limits for large chemical files.
   */
  @PostMapping("/save")
  @ResponseBody
  public RSChemElement save(@RequestBody ChemicalDataDTO chemicalDataDTO) throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    return chemistryService.saveChemicalElement(chemicalDataDTO, subject);
  }

  /**
   * Method called to save an {@link RSChemElement} to the database, return ChemElementDataDto
   * object.
   *
   * @param dto The Chemical data to save
   * @return AjaxReturnObject<RSChemElement>
   * @throws IOException
   */
  @PostMapping("ajax/createChemElement")
  @ResponseBody
  public AjaxReturnObject<ChemElementDataDto> createChemElement(
      @RequestBody @Valid ChemElementDataDto dto) {

    User subject = userManager.getAuthenticatedUserInSession();
    try {
      ChemElementDataDto created = chemistryService.createChemicalElement(dto, subject);
      return new AjaxReturnObject<>(created);
    } catch (Exception e) {
      String errorMsg = String.format("Error creating chemical element: %s", e.getMessage());
      return new AjaxReturnObject<>(ErrorList.of(errorMsg));
    }
  }

  /**
   * Method called to save an image connected to chemical element in filestore.
   *
   * @param chemImageDto The chemical image dto to save
   * @param error BindingResult with errors
   * @throws IOException
   */
  @PostMapping("ajax/saveChemImage")
  @ResponseBody
  public AjaxReturnObject<Boolean> saveChemImage(ChemicalImageDTO chemImageDto, BindingResult error)
      throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    chemistryService.saveChemicalImage(chemImageDto, subject);
    return new AjaxReturnObject<>(true);
  }

  private ChemicalSearchResults getChemicalSearchResults(ChemicalSearchRequestDTO request) {
    log.info("Searching for chemical structure: {}", request.searchInput);
    if (request.pageSize <= 0) {
      request.pageSize = PaginationCriteria.getDefaultResultsPerPage();
    }

    User user = userManager.getAuthenticatedUserInSession();

    return chemistryService.searchChemicals(
        request.searchInput, request.searchType, request.pageNumber, request.pageSize, user);
  }

  @PostMapping("/search")
  public ChemSearchResultsPage searchChemicals(@RequestBody ChemicalSearchRequestDTO request) {
    // default to substructure search
    if (request.searchType == null
        || !(request.searchType.equalsIgnoreCase("SUBSTRUCTURE")
            || request.searchType.equalsIgnoreCase("EXACT"))) {
      request.searchType = "SUBSTRUCTURE";
    }
    ChemicalSearchResults results = getChemicalSearchResults(request);

    ChemSearchResultsPage page =
        ChemSearchResultsPage.builder()
            .hits(
                results.getPagedRecords().stream()
                    .map(
                        item ->
                            new ChemSearchHit(
                                item.getRecord().getOid().toString(),
                                item.getRecordName(),
                                item.getChemId(),
                                results
                                    .getBreadcrumbMap()
                                    .get(item.getRecordId())
                                    .getAsStringPath(),
                                item.getRecord().getOwner().getFullName(),
                                item.getRecord().getModificationDateAsDate().toString()))
                    .collect(Collectors.toList()))
            .build();
    page.setStartHit(results.getStartHit());
    page.setEndHit(results.getEndHit());
    page.setTotalHitCount(results.getTotalHitCount());
    page.setTotalPageCount(results.getTotalPageCount());
    return page;
  }

  @Data
  @Builder
  static class ChemSearchHit {
    String globalId;
    String recordName;
    long chemId;
    String breadcrumb;
    String owner;
    String lastModified;
  }

  @Data
  @Builder
  public static class ChemSearchResultsPage {
    List<ChemSearchHit> hits;
    Integer totalHitCount;
    Integer totalPageCount;
    int startHit;
    int endHit;
  }

  /**
   * Converts into required input format, returning this to client
   *
   * @param input
   * @param errors
   * @return ResponseEntity<ConvertedStructure>
   */
  @PostMapping("/ajax/convert")
  @ResponseBody
  public ResponseEntity<ConvertedStructureDto> convert(
      @Valid @RequestBody ChemConversionInputDto input, BindingResult errors) {

    if (errors.hasErrors()) {
      log.warn(
          "Errors with converting: {} with params: {} {}",
          input.getStructure(),
          input.getInputFormat(),
          input.getParameters());
      ErrorList el = new ErrorList();
      el = inputValidator.populateErrorList(errors, el);
      return badConversion(el.getAllErrorMessagesAsStringsSeparatedBy(","));
    }
    try {
      log.info(
          "Calling convert with params: {}, {}", input.getInputFormat(), input.getParameters());
      ConvertedStructureDto convertedStruc = chemistryService.convert(input);
      return ResponseEntity.ok(convertedStruc);
    } catch (Exception e) {
      return badConversion(e.getMessage());
    }
  }

  /**
   * Method to get a chemistry file by its id and checking user access rights.
   *
   * @param chemistryFileId id of chemistry file to return
   * @return AjaxReturnObject<ChemElementDataDto>
   */
  @GetMapping("/ajax/getChemFile/{chemistryFileId}")
  @ResponseBody
  public AjaxReturnObject<ChemElementDataDto> getChemFile(
      @PathVariable("chemistryFileId") Long chemistryFileId) {
    try {
      ChemElementDataDto dto =
          chemistryService.getChemicalsForFile(
              chemistryFileId, userManager.getAuthenticatedUserInSession());
      return new AjaxReturnObject<>(dto);
    } catch (Exception e) {
      String errorMsg =
          String.format(
              "Chemistry File with Id: %d could not be retrieved. Details: %s",
              chemistryFileId, e.getMessage());
      return new AjaxReturnObject<>(ErrorList.of(errorMsg));
    }
  }

  /**
   * Method to get a list of chem elements associated with a chemistry file by its id and checking
   * user access rights.
   *
   * @param chemistryFileId id of chemistry file to update
   * @return AjaxReturnObject<ChemElementDataDto>
   */
  @GetMapping("/ajax/getUpdatableChems/{chemistryFileId}")
  @ResponseBody
  public AjaxReturnObject<ChemElementDataDto> getUpdatableChems(
      @PathVariable("chemistryFileId") Long chemistryFileId) {
    try {
      User user = userManager.getAuthenticatedUserInSession();
      ChemElementDataDto dto = chemistryService.getUpdatableChemicals(chemistryFileId, user);
      return new AjaxReturnObject<>(dto);
    } catch (Exception e) {
      String errorMsg =
          String.format(
              "RSChemElements with Chemistry File Id: %d could not be retrieved. Details: %s",
              chemistryFileId, e.getMessage());
      return new AjaxReturnObject<>(ErrorList.of(errorMsg));
    }
  }

  /**
   * This method updates all the images for the list of {@link RSChemElement} linked to the {@link
   * EcatChemistryFile} id posted.
   *
   * @param dto the {@link ChemElementImageUpdateDto} containing the list of chemical elements to
   *     update and the parameters of the image to generate.
   * @return the byte array of the generated image.
   */
  @PostMapping("/ajax/updateChemImages")
  @ResponseBody
  public AjaxReturnObject<List<RSChemElement>> updateChemElementImages(
      @Valid ChemElementImageUpdateDto dto) {
    try {
      User user = userManager.getAuthenticatedUserInSession();
      List<RSChemElement> updatedChemElements =
          chemistryService.updateChemicalElementImages(dto, user);
      return new AjaxReturnObject<>(updatedChemElements);
    } catch (Exception e) {
      String errorMsg =
          String.format(
              "An Error Occurred While Updating Chemical Element Images Details: %s",
              e.getMessage());
      return new AjaxReturnObject<>(ErrorList.of(errorMsg));
    }
  }

  /**
   * Method to get a list of RSChemElements by its EcatChemistryFile id
   *
   * @param chemistryFileId the {@link EcatChemistryFile} id to retrieve rschemelements by
   * @return AjaxReturnObject<RSChemElement>
   */
  @GetMapping("/ajax/getRsChemElements/{chemistryFileId}")
  @ResponseBody
  public AjaxReturnObject<List<RSChemElement>> getRsChemElement(
      @PathVariable("chemistryFileId") Long chemistryFileId) {
    User user = userManager.getAuthenticatedUserInSession();
    List<RSChemElement> rsChemElements =
        chemistryService.getChemicalElementsForFile(chemistryFileId, user);
    return new AjaxReturnObject<>(rsChemElements);
  }

  /**
   * Gets a list of chemical file suffixes supported by chemistry library.
   *
   * @return
   */
  @GetMapping("/ajax/supportedFileTypes")
  @IgnoreInLoggingInterceptor
  @ResponseBody
  public AjaxReturnObject<List<String>> getSupportedFileTypes() {
    List<String> supportedFileTypes = chemistryService.getSupportedFileTypes();
    return new AjaxReturnObject<>(new ArrayList<>(supportedFileTypes));
  }

  private ResponseEntity<ConvertedStructureDto> badConversion(String msg) {
    log.error("Error converting structure: {}", msg);
    return ResponseEntity.badRequest()
        .body(ConvertedStructureDto.builder().errorMessage(msg).build());
  }

  /**
   * Gets the chemistry structure image and streams direct to response,
   *
   * @param chemId the RSChemElementId
   * @param unused the timestamp, used on the front end to get the most up-to-date image
   * @param revision optional argument. If provided, it should be a <em>Document</em> revision
   *     number.
   * @return ResponseEntity<byte [ ]>
   * @throws IOException
   */
  @GetMapping("/getImageChem/{id}/{unused}")
  public ResponseEntity<byte[]> getImageChem(
      @PathVariable("id") Long chemId,
      @PathVariable("unused") Long unused,
      Principal principal,
      @RequestParam(value = "revision", required = false) Integer revision)
      throws IOException {
    User subject = getUserByUsername(principal.getName());
    RSChemElement rsChemElement =
        chemistryService.getChemicalElementByRevision(chemId, revision, subject);
    try (InputStream is = new ByteArrayInputStream(rsChemElement.getDataImage())) {
      final HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.IMAGE_PNG);
      byte[] data = IOUtils.toByteArray(is);
      return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
  }

  /**
   * Gets JSON string of chemical data for populating chem sketcher
   *
   * @param chemId id of {@link RSChemElement}
   * @param revision an optional revision number.
   * @param principal the user currently logged in
   * @return An {@link AjaxReturnObject} with a String of the chemical data or <code>null</code> if
   *     no chem element found
   */
  @GetMapping("ajax/loadChemElements")
  @ResponseBody
  public AjaxReturnObject<ChemEditorInputDto> loadChemElements(
      @RequestParam("chemId") long chemId,
      @RequestParam(value = "revision", required = false) Integer revision,
      Principal principal) {
    User subject = getUserByUsername(principal.getName());
    ChemEditorInputDto chemicalEditorInput =
        chemistryService.getChemicalEditorInput(chemId, revision, subject);
    return new AjaxReturnObject<>(chemicalEditorInput);
  }

  /***
   * Returns the string contents of a chemistry file. Contents will be base64-encoded where the file is a binary format.
   */
  @GetMapping("file/contents")
  @ResponseBody
  public String getFileAsString(
      @RequestParam("chemId") long chemId,
      @RequestParam(required = false) Integer revision,
      Principal principal) {
    User subject = getUserByUsername(principal.getName());
    return chemistryService.getChemicalFileContents(chemId, revision, subject);
  }

  /**
   * Gets a map of key-value pairs for a given RSChemElement Id. <br>
   * If no such ChemElement exists for the given id, will return an error message in
   * AjaxReturnObject. <br>
   * If no properties exist, returns an empty map.
   *
   * @param chemId the chemId pointing to the RSChemElement
   * @param revision the current revision of the chem element
   * @param principal the current principle
   * @return AjaxReturnObject<Map < String, String>>
   */
  @GetMapping("ajax/getInfo")
  @ResponseBody
  public AjaxReturnObject<ElementalAnalysisDTO> getInfo(
      @RequestParam("chemId") long chemId,
      @RequestParam(value = "revision", required = false) Integer revision,
      Principal principal) {
    User subject = getUserByUsername(principal.getName());
    Optional<ElementalAnalysisDTO> elementalAnalysis =
        chemistryService.getElementalAnalysis(chemId, revision, subject);
    if (elementalAnalysis == null) {
      log.info("No chem element found for id {} and revision {}", chemId, revision);
      return new AjaxReturnObject<>(ErrorList.of("No chem element with id " + chemId));
    }
    if (elementalAnalysis.isPresent()) {
      return new AjaxReturnObject<>(elementalAnalysis.get());
    } else {
      log.info(
          "Couldn't retrieve elementalAnalysis for chemId {} and revision {}", chemId, revision);
      return new AjaxReturnObject<>(ErrorList.of("Couldn't retrieve info for chemId: " + chemId));
    }
  }

  /**
   * Gets stoichiometry information for a given RSChemElement Id. <br>
   * If no such ChemElement exists for the given id, will return an error message in
   * AjaxReturnObject. <br>
   * If no stoichiometry information exists, returns an empty result.
   *
   * @param chemId the chemId pointing to the RSChemElement
   * @param revision the current revision of the chem element
   * @param principal the current principal
   * @return AjaxReturnObject with stoichiometry information
   */
  @GetMapping("stoichiometry")
  @ResponseBody
  @Transactional
  public AjaxReturnObject<Stoichiometry> getStoichiometry(
      @RequestParam("chemId") long chemId,
      @RequestParam(value = "revision", required = false) Integer revision,
      Principal principal) {
    User subject = getUserByUsername(principal.getName());
    // First check if the chemical element exists
    RSChemElement chemical =
        chemistryService.getChemicalElementByRevision(chemId, revision, subject);
    if (chemical == null) {
      log.info("No chem element found for id {} and revision {}", chemId, revision);
      return new AjaxReturnObject<>(ErrorList.of("No chem element with id " + chemId));
    }

    // Get existing stoichiometry for this reaction
    Stoichiometry stoichiometry = chemistryService.getStoichiometryByParentReactionId(chemId);
    if (stoichiometry != null) {
      return new AjaxReturnObject<>(stoichiometry);
    } else {
      log.info("Couldn't retrieve stoichiometry for chemId {} and revision {}", chemId, revision);
      return new AjaxReturnObject<>(
          ErrorList.of("Couldn't retrieve stoichiometry for chemId: " + chemId));
    }
  }

  /**
   * Saves stoichiometry information for a given RSChemElement Id. <br>
   * If no such ChemElement exists for the given id, will return an error message in
   * AjaxReturnObject.
   *
   * @param chemId the chemId pointing to the RSChemElement
   * @param revision the current revision of the chem element
   * @param principal the current principal
   * @return AjaxReturnObject with the saved stoichiometry
   */
  @PostMapping("stoichiometry")
  @ResponseBody
  public AjaxReturnObject<Stoichiometry> saveStoichiometry(
      @RequestParam("chemId") long chemId,
      @RequestParam(value = "revision", required = false) Integer revision,
      Principal principal) {
    User subject = getUserByUsername(principal.getName());
    Stoichiometry stoichiometry =
        chemistryService.getStoichiometryAndSave(chemId, revision, subject);
    if (stoichiometry == null) {
      log.info("Couldn't save stoichiometry for chemId {} and revision {}", chemId, revision);
      return new AjaxReturnObject<>(
          ErrorList.of("Couldn't save stoichiometry for chemId: " + chemId));
    }
    return new AjaxReturnObject<>(stoichiometry);
  }

  //  /**
  //   * Updates stoichiometry information in the database. <br>
  //   * If no such Stoichiometry exists for the given id, will return an error message in
  //   * AjaxReturnObject.
  //   *
  //   * @param stoichiometryId the ID of the stoichiometry to update
  //   * @param stoichiometryDTO the updated stoichiometry information
  //   * @param principal the current principal
  //   * @return AjaxReturnObject with the updated stoichiometry
  //   */
  //  @PutMapping("stoichiometry")
  //  @ResponseBody
  //  public AjaxReturnObject<Stoichiometry> updateStoichiometry(
  //      @RequestParam("stoichiometryId") long stoichiometryId,
  //      @RequestBody StoichiometryDTO stoichiometryDTO,
  //      Principal principal) {
  //    User subject = getUserByUsername(principal.getName());
  //    try {
  //      Stoichiometry stoichiometry =
  //          chemistryService.updateStoichiometry(stoichiometryId, stoichiometryDTO, subject);
  //      return new AjaxReturnObject<>(stoichiometry);
  //    } catch (Exception e) {
  //      log.error("Error updating stoichiometry with id {}: {}", stoichiometryId, e.getMessage());
  //      return new AjaxReturnObject<>(
  //          ErrorList.of("Error updating stoichiometry: " + e.getMessage()));
  //    }
  //  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ChemEditorInputDto {
    private Long chemId;
    private String chemElements;
    private ChemElementsFormat format;
    private Long chemFileId;
  }
}

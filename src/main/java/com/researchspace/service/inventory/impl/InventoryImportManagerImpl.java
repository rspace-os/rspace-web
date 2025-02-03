package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.controller.ApiControllerAdvice;
import com.researchspace.api.v1.controller.ContainersApiController;
import com.researchspace.api.v1.controller.InventoryImportApiController.ApiInventoryImportSamplesSettings;
import com.researchspace.api.v1.controller.InventoryImportApiController.ApiInventoryImportSettings;
import com.researchspace.api.v1.controller.InventoryImportApiController.ApiInventoryImportSettingsPost;
import com.researchspace.api.v1.controller.InventoryImportPostFullValidator.ApiInventoryImportPostFull;
import com.researchspace.api.v1.controller.SampleTemplatesApiController;
import com.researchspace.api.v1.controller.SamplesApiController;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.ApiInventoryBulkOperationRecordResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryImportParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportPartialResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportSubSampleImportResult;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.core.util.DateUtil;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InventoryImportManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.service.inventory.csvimport.CsvContainerImporter;
import com.researchspace.service.inventory.csvimport.CsvSampleImporter;
import com.researchspace.service.inventory.csvimport.CsvSubSampleImporter;
import com.researchspace.service.inventory.csvimport.exception.InventoryImportException;
import com.researchspace.service.inventory.csvimport.exception.InventoryImportPrevalidationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;

/** To deal with import from CSV. */
@Slf4j
@Setter
@Service("inventoryImportManager")
public class InventoryImportManagerImpl implements InventoryImportManager {

  @Autowired private SampleTemplatesApiController templatesController;
  @Autowired private SamplesApiController samplesController;
  @Autowired private ContainersApiController containersController;
  @Autowired private ContainerApiManager containerManager;
  @Autowired private SampleApiManager sampleManager;
  @Autowired private SubSampleApiManager subSampleManager;

  @Autowired private InventoryBulkOperationHandler bulkOperationHandler;
  @Autowired protected InventoryPermissionUtils invPermissions;
  @Autowired private ApiControllerAdvice apiControllerAdvice;

  @Autowired private CsvContainerImporter containerCsvImporter;
  @Autowired private CsvSampleImporter sampleCsvImporter;
  @Autowired private CsvSubSampleImporter subSampleCsvImporter;

  @Override
  public ApiInventoryImportSampleParseResult parseSamplesCsvFile(
      String filename, InputStream inputStream, User createdBy) throws IOException {
    return sampleCsvImporter.parseSamplesCsvFile(filename, inputStream, createdBy);
  }

  @Override
  public ApiInventoryImportParseResult parseContainersCsvFile(
      InputStream inputStream, User createdBy) throws IOException {
    return containerCsvImporter.readCsvIntoParseResults(inputStream);
  }

  @Override
  public ApiInventoryImportParseResult parseSubSamplesCsvFile(
      InputStream inputStream, User createdBy) throws IOException {
    return subSampleCsvImporter.readCsvIntoParseResults(inputStream);
  }

  @Override
  public ApiInventoryImportResult importInventoryCsvFiles(
      ApiInventoryImportPostFull importPostFull, User user) throws IOException {

    ApiInventoryImportResult csvResult = new ApiInventoryImportResult(user);

    ApiInventoryImportSettingsPost importSettings = importPostFull.getImportSettings();
    ApiInventoryImportSamplesSettings sampleSettings = importSettings.getSampleSettings();

    // first deal with sample template (if present)
    if (sampleSettings != null) {
      log.info("create/retrieve requested sample template");

      ApiSampleTemplatePost templateInfo = sampleSettings.getTemplateInfo();
      log.info("templateInfo: {} ", templateInfo);

      csvResult.setSampleResult(createSampleResultWithRequestedTemplate(templateInfo, user));
    }

    readCsvFiles(csvResult, importPostFull);

    if (csvResult.hasPrevalidationError()) {
      throw new InventoryImportPrevalidationException(csvResult);
    }

    // start import
    ApiInventoryImportResult importResult = new ApiInventoryImportResult(user);
    if (csvResult.getContainerResult() != null) {
      importContainers(importResult, csvResult);
      importResult.getContainerResult().setStatus(InventoryBulkOperationStatus.COMPLETED);
    }

    if (csvResult.getSampleResult() != null) {
      if (csvResult.getSubSampleResult() != null) {
        populateSamplesToImportWithSubSamplesToImport(csvResult);
      }
      importSamples(importResult, csvResult);
      importResult.getSampleResult().setStatus(InventoryBulkOperationStatus.COMPLETED);
    }

    if (csvResult.getSubSampleResult() != null) {
      populateSubSampleResultsWithImportedSubSamples(importResult, csvResult.getSubSampleResult());
      importSubSamplesIntoPreexistingSamples(importResult, csvResult.getSubSampleResult());
      moveImportedSubSamplesIntoRequestedContainers(csvResult, importResult);

      reloadSamplesInImportResult(importResult);
      importResult.getSubSampleResult().setStatus(InventoryBulkOperationStatus.COMPLETED);
    }

    reloadContainersInImportResult(importResult);
    return importResult;
  }

  ApiInventoryImportSampleImportResult createSampleResultWithRequestedTemplate(
      ApiSampleTemplatePost templateInfo, User user) {

    ApiInventoryImportSampleImportResult templateResult =
        new ApiInventoryImportSampleImportResult();
    ApiSampleTemplate template;
    try {
      if (templateInfo.getId() != null) {
        // if template id provided retrieve from db
        template = templatesController.getSampleTemplateById(templateInfo.getId(), user);
        templateResult.addExistingTemplateResult(template);
      } else {
        // if no id create new template
        BindException errors = new BindException(templateInfo, "templateInfo");
        template = templatesController.createNewSampleTemplate(templateInfo, errors, user);
        templateResult.addCreatedTemplateResult(template);
      }
    } catch (Exception e) {
      log.warn("Error on creating/retrieving sample template", e);
      ApiError error = bulkOperationHandler.convertExceptionToApiError(e);
      templateResult.addTemplateError(convertToTemplateErrorException(e));
      ApiInventoryImportResult importResult = new ApiInventoryImportResult(user);
      importResult.setSampleResult(templateResult);
      throw new InventoryImportException(error, importResult);
    }

    return templateResult;
  }

  private ApiError convertToTemplateErrorException(Exception e) {
    log.warn("problem with template to use for import: " + e.getMessage(), e);
    return bulkOperationHandler.convertExceptionToApiError(e);
  }

  private void readCsvFiles(
      ApiInventoryImportResult csvResult, ApiInventoryImportPostFull importPostFull)
      throws IOException {

    ApiInventoryImportSettingsPost importSettings = importPostFull.getImportSettings();
    ApiInventoryImportSamplesSettings sampleSettings = importSettings.getSampleSettings();
    ApiInventoryImportSettings containerSettings = importSettings.getContainerSettings();
    ApiInventoryImportSettings subSampleSettings = importSettings.getSubSampleSettings();

    MultipartFile containersFile = importPostFull.getContainersFile();
    MultipartFile samplesFile = importPostFull.getSamplesFile();
    MultipartFile subSamplesFile = importPostFull.getSubSamplesFile();

    setFileNamesInImportResult(csvResult, containersFile, samplesFile, subSamplesFile);

    try (InputStream containersIS =
            containersFile != null ? containersFile.getInputStream() : null;
        InputStream samplesIS = samplesFile != null ? samplesFile.getInputStream() : null;
        InputStream subSamplesIS =
            subSamplesFile != null ? subSamplesFile.getInputStream() : null) {

      // read containers
      if (containerSettings != null && containersIS != null) {
        log.info("reading containers file");
        Map<String, String> fieldMappings = containerSettings.getFieldMappings();
        log.info("container fieldMappings: {} ", fieldMappings);

        containerCsvImporter.readCsvIntoImportResult(containersIS, fieldMappings, csvResult);
        prevalidateContainers(csvResult);
      }

      // read samples
      if (sampleSettings != null && samplesIS != null) {
        log.info("reading samples file");
        Map<String, String> fieldMappings = sampleSettings.getFieldMappings();
        log.info("sample fieldMappings: {} ", fieldMappings);

        boolean templateRetrieved = csvResult.getSampleResult().getTemplate().getRecord() != null;
        if (templateRetrieved) {
          sampleCsvImporter.readCsvIntoImportResult(samplesIS, fieldMappings, csvResult);

          boolean validTemplate = csvResult.getSampleResult().getTemplate().getRecord() != null;
          if (validTemplate) {
            prevalidateSamples(csvResult);
          }
        }
      }

      // read subsamples
      if (subSampleSettings != null && subSamplesIS != null) {
        log.info("reading subSamples file");
        Map<String, String> subSampleFieldMappings = subSampleSettings.getFieldMappings();
        log.info("subSample fieldMappings: {} ", subSampleFieldMappings);

        subSampleCsvImporter.readCsvIntoImportResult(
            subSamplesIS, subSampleFieldMappings, csvResult);
        prevalidateSubSamples(csvResult);
      }
    }
  }

  private void setFileNamesInImportResult(
      ApiInventoryImportResult importResult,
      MultipartFile containersFile,
      MultipartFile samplesFile,
      MultipartFile subSamplesFile) {

    importResult.setContainerCsvFilename(
        containersFile != null ? containersFile.getOriginalFilename() : null);
    importResult.setSampleCsvFilename(
        samplesFile != null ? samplesFile.getOriginalFilename() : null);
    importResult.setSubSampleCsvFilename(
        subSamplesFile != null ? subSamplesFile.getOriginalFilename() : null);
  }

  void prevalidateContainers(ApiInventoryImportResult csvResult) {

    ApiInventoryImportPartialResult containerResults = csvResult.getContainerResult();

    int resultNumber = 0;
    for (ApiInventoryBulkOperationRecordResult containerResult : containerResults.getResults()) {
      if (containerResult.getRecord() != null) {
        ApiContainer container = (ApiContainer) containerResult.getRecord();
        BindException recordErrors = new BindException(container, "record");

        prevalidateParentContainerIds(
            containerResults,
            containerResults,
            resultNumber,
            recordErrors,
            csvResult.getCurrentUser());

        // validate container request
        containersController.validateCreateContainerInput(container, recordErrors);

        // put together all the errors for row
        if (recordErrors.hasErrors()) {
          containerResults.changeIntoErrorResult(
              containerResult, apiControllerAdvice.getApiErrorFromBindException(recordErrors));
        }
      }
      resultNumber++;
    }

    if (containerResults.getErrorCount() > 0) {
      containerResults.setErrorStatusAndResetSuccessCount(
          InventoryBulkOperationStatus.PREVALIDATION_ERROR);
    } else {
      containerResults.setStatus(InventoryBulkOperationStatus.PREVALIDATED);
    }
  }

  void prevalidateSamples(ApiInventoryImportResult csvResult) {

    ApiInventoryImportSampleImportResult sampleResults = csvResult.getSampleResult();
    ApiInventoryImportPartialResult containerResults = csvResult.getContainerResult();
    User user = csvResult.getCurrentUser();

    int resultNumber = 0;
    for (ApiInventoryBulkOperationResult.ApiInventoryBulkOperationRecordResult sampleResult :
        sampleResults.getResults()) {
      if (sampleResult.getRecord() != null) {
        ApiSampleWithFullSubSamples sample = (ApiSampleWithFullSubSamples) sampleResult.getRecord();
        BindException recordErrors = new BindException(sample, "record");

        prevalidateParentContainerIds(
            sampleResults, containerResults, resultNumber, recordErrors, user);

        try {
          samplesController.validateCreateSampleInput(sample, recordErrors, user);
        } catch (BindException be) {
          sampleResults.changeIntoErrorResult(
              sampleResult, apiControllerAdvice.getApiErrorFromBindException(recordErrors));
        }
      }
      resultNumber++;
    }

    if (sampleResults.getErrorCount() > 0) {
      sampleResults.setErrorStatusAndResetSuccessCount(
          ApiInventoryBulkOperationResult.InventoryBulkOperationStatus.PREVALIDATION_ERROR);
    } else {
      sampleResults.setStatus(
          ApiInventoryBulkOperationResult.InventoryBulkOperationStatus.PREVALIDATED);
    }
  }

  private void prevalidateParentContainerIds(
      ApiInventoryImportPartialResult resultsToPrevalidate,
      ApiInventoryImportPartialResult containerResults,
      int resultNumber,
      BindException recordErrors,
      User user) {

    // validate parent container import identifier
    String parentContainerImportId =
        resultsToPrevalidate.getParentContainerImportIdForResultNumber(resultNumber);
    GlobalIdentifier parentContainerGlobalId =
        resultsToPrevalidate.getParentContainerGlobalIdForResultNumber(resultNumber);
    if (parentContainerImportId != null && parentContainerGlobalId != null) {
      recordErrors.rejectValue(
          "id",
          "errors.inventory.import.parent.container.importId.with.globalId",
          "parent container set via both import and global id");
    } else {
      if (parentContainerImportId != null
          && (containerResults == null
              || containerResults.getResultForImportId(parentContainerImportId) == null)) {
        recordErrors.rejectValue(
            "id",
            "errors.inventory.import.parent.container.not.found",
            new Object[] {parentContainerImportId},
            "parent container not found");
      }
      if (parentContainerGlobalId != null) {
        try {
          Container container =
              containerManager.getContainerById(parentContainerGlobalId.getDbId(), user);
          invPermissions.assertUserCanEditInventoryRecord(container, user);

          if (!container.isListLayoutContainer() && !container.isWorkbench()) {
            recordErrors.rejectValue(
                "id",
                "errors.inventory.import.parent.container.not.list.container",
                new Object[] {
                  parentContainerGlobalId,
                  container.getContainerType().toString().toLowerCase(Locale.ROOT)
                },
                "parent not a list container");
          }
        } catch (RuntimeException re) {
          recordErrors.rejectValue(
              "id",
              "errors.inventory.import.parent.container.not.editable",
              new Object[] {parentContainerGlobalId},
              "parent container not found or no permission");
        }
      }
    }
  }

  void prevalidateSubSamples(ApiInventoryImportResult csvResult) {

    ApiInventoryImportSubSampleImportResult subSampleCsvResult = csvResult.getSubSampleResult();
    ApiInventoryImportSampleImportResult sampleCsvResult = csvResult.getSampleResult();
    ApiInventoryImportPartialResult containerCsvResult = csvResult.getContainerResult();

    int resultNumber = 0;
    for (ApiInventoryBulkOperationRecordResult subSampleResult : subSampleCsvResult.getResults()) {
      if (subSampleResult.getRecord() != null) {
        ApiSubSample subSample = (ApiSubSample) subSampleResult.getRecord();
        BindException recordErrors = new BindException(subSample, "record");

        prevalidateParentSampleIdAndQuantityUnit(
            subSampleCsvResult,
            sampleCsvResult,
            resultNumber,
            recordErrors,
            csvResult.getCurrentUser());
        prevalidateParentContainerIds(
            subSampleCsvResult,
            containerCsvResult,
            resultNumber,
            recordErrors,
            csvResult.getCurrentUser());

        if (recordErrors.hasErrors()) {
          subSampleCsvResult.changeIntoErrorResult(
              subSampleResult, apiControllerAdvice.getApiErrorFromBindException(recordErrors));
        }
      }
      resultNumber++;
    }

    if (subSampleCsvResult.getErrorCount() > 0) {
      subSampleCsvResult.setErrorStatusAndResetSuccessCount(
          InventoryBulkOperationStatus.PREVALIDATION_ERROR);
    } else {
      subSampleCsvResult.setStatus(InventoryBulkOperationStatus.PREVALIDATED);
    }
  }

  private void prevalidateParentSampleIdAndQuantityUnit(
      ApiInventoryImportSubSampleImportResult subSampleCsvResult,
      ApiInventoryImportSampleImportResult sampleCsvResult,
      int resultNumber,
      BindException recordErrors,
      User user) {

    // validate parent sample import identifier
    ApiSubSample subSampleToImport =
        (ApiSubSample) subSampleCsvResult.getResults().get(resultNumber).getRecord();
    String sampleImportId = subSampleCsvResult.getParentSampleImportIdForResultNumber(resultNumber);
    GlobalIdentifier sampleGlobalId =
        subSampleCsvResult.getParentSampleGlobalIdForResultNumber(resultNumber);

    if (sampleImportId == null && sampleGlobalId == null) {
      recordErrors.rejectValue(
          "id", "errors.inventory.import.parent.sample.not.set", "parent sample not set");
      return;
    }
    if (sampleImportId != null && sampleGlobalId != null) {
      recordErrors.rejectValue(
          "id",
          "errors.inventory.import.parent.sample.importId.with.globalId",
          "parent sample set via both import and global id");
      return;
    }

    Integer parentSampleQuantityUnitId = null;
    if (sampleImportId != null
        && (sampleCsvResult == null
            || sampleCsvResult.getResultForImportId(sampleImportId) == null)) {
      recordErrors.rejectValue(
          "id",
          "errors.inventory.import.parent.sample.not.found",
          new Object[] {sampleImportId},
          "parent sample not found");
    } else if (sampleImportId != null && sampleCsvResult.getTemplate() != null) {
      ApiSampleTemplate template = (ApiSampleTemplate) sampleCsvResult.getTemplate().getRecord();
      parentSampleQuantityUnitId = template.getDefaultUnitId();
    }
    if (sampleGlobalId != null) {
      try {
        Sample sample = sampleManager.getSampleById(sampleGlobalId.getDbId(), user);
        invPermissions.assertUserCanEditInventoryRecord(sample, user);

        if (sample.getQuantityInfo() != null) {
          parentSampleQuantityUnitId = sample.getQuantityInfo().getUnitId();
        }
      } catch (RuntimeException re) {
        recordErrors.rejectValue(
            "id",
            "errors.inventory.import.parent.sample.not.editable",
            new Object[] {sampleGlobalId},
            "parent sample not found or no permission");
      }
    }
    // verify quantity unit of subsample matches one of a parent sample
    if (subSampleToImport.getQuantity() != null && parentSampleQuantityUnitId != null) {
      RSUnitDef subSampleUnit = RSUnitDef.getUnitById(subSampleToImport.getQuantity().getUnitId());
      RSUnitDef sampleUnit = RSUnitDef.getUnitById(parentSampleQuantityUnitId);
      if (!subSampleUnit.isComparable(sampleUnit)) {
        recordErrors.rejectValue(
            "quantity",
            "errors.inventory.subsample.unit.incompatible.with.sample",
            new Object[] {
              subSampleToImport.getQuantity().toQuantityInfo().toPlainString(), sampleUnit.name()
            },
            "incompatible subsample unit");
      }
    }
  }

  void populateSamplesToImportWithSubSamplesToImport(ApiInventoryImportResult csvResult) {

    ApiInventoryImportSubSampleImportResult subSampleCsvResult = csvResult.getSubSampleResult();
    if (subSampleCsvResult == null) {
      return;
    }

    ApiInventoryImportSampleImportResult sampleCsvResult = csvResult.getSampleResult();
    List<ApiInventoryBulkOperationRecordResult> subSamples = subSampleCsvResult.getResults();
    for (int resultCount = 0; resultCount < subSamples.size(); resultCount++) {
      ApiInventoryBulkOperationRecordResult result = subSamples.get(resultCount);
      ApiSubSample subSample = (ApiSubSample) result.getRecord();
      String parentSampleImportId =
          subSampleCsvResult.getParentSampleImportIdForResultNumber(resultCount);
      if (parentSampleImportId == null) {
        continue; // must be subsample providing parent sample global id, it will be imported
        // separately
      }
      ApiInventoryBulkOperationRecordResult sampleResultForImportId =
          sampleCsvResult.getResultForImportId(parentSampleImportId);
      if (sampleResultForImportId == null) {
        throw new IllegalArgumentException(
            "No sample found with import id: " + parentSampleImportId);
      }
      ApiSampleWithFullSubSamples sample =
          (ApiSampleWithFullSubSamples) sampleResultForImportId.getRecord();
      sample.getSubSamples().add(subSample);

      Integer sampleResultNumber = sampleCsvResult.getResultNumberForImportId(parentSampleImportId);
      sampleCsvResult.addSampleRecordNumberWithNonDefaultSubSample(sampleResultNumber);
    }
  }

  void importContainers(ApiInventoryImportResult importResult, ApiInventoryImportResult csvResult) {

    ApiInventoryImportPartialResult containerCsvResult = csvResult.getContainerResult();
    ApiInventoryImportPartialResult containerImportResult =
        containerCsvResult.copyWithImportIdMapsOnly();
    importResult.setContainerResult(containerImportResult);

    // create all containers

    for (int containerCount = 0;
        containerCount < containerCsvResult.getResults().size();
        containerCount++) {
      ApiContainer containerToImport =
          (ApiContainer) containerCsvResult.getResults().get(containerCount).getRecord();
      try {
        BindException errors = new BindException(containerToImport, "sample");
        ApiContainer importedContainer =
            containersController.createNewContainer(
                containerToImport, errors, importResult.getCurrentUser());
        containerImportResult.addSuccessResult(importedContainer);

      } catch (Exception e) {
        log.warn("Error on saving container from line: " + (containerCount + 1), e);
        ApiError error = bulkOperationHandler.convertExceptionToApiError(e);
        containerImportResult.addError(error);
        throw new InventoryImportException(error, importResult);
      }
    }

    // move containers into requested parent, or into default workbench container
    for (int containerCount = 0;
        containerCount < containerImportResult.getSuccessCount();
        containerCount++) {
      ApiInventoryBulkOperationRecordResult importedRecordResult =
          containerImportResult.getResults().get(containerCount);
      ApiContainer importedRecord = (ApiContainer) importedRecordResult.getRecord();
      String parentImportId =
          containerCsvResult.getParentContainerImportIdForResultNumber(containerCount);
      GlobalIdentifier parentGlobalId =
          containerCsvResult.getParentContainerGlobalIdForResultNumber(containerCount);

      ApiContainer targetParent = null;
      if (parentImportId != null) {
        ApiInventoryBulkOperationRecordResult importedParentResult =
            containerImportResult.getResultForImportId(parentImportId);
        targetParent =
            importedParentResult != null ? (ApiContainer) importedParentResult.getRecord() : null;
      } else if (parentGlobalId != null) {
        targetParent = new ApiContainer(); // permission were checked during prevalidation
        targetParent.setId(parentGlobalId.getDbId());
      }
      if (targetParent == null) {
        targetParent = getDefaultContainerForImportedItems(importResult, csvResult);
      }
      try {
        ApiContainer moveRequest = new ApiContainer();
        moveRequest.setId(importedRecord.getId());
        moveRequest.setParentContainer(targetParent);
        ApiContainer updatedContainer =
            containerManager.updateApiContainer(moveRequest, importResult.getCurrentUser());
        importedRecordResult.setRecord(updatedContainer);

      } catch (Exception e) {
        log.warn(
            "Error on moving container ["
                + importedRecord.getName()
                + "] into parent container ["
                + (targetParent != null ? targetParent.getName() : null)
                + "]",
            e);
        ApiError error = bulkOperationHandler.convertExceptionToApiError(e);
        containerImportResult.changeIntoErrorResult(importedRecordResult, error);
        throw new InventoryImportException(error, importResult);
      }
    }
  }

  private ApiContainer getDefaultContainerForImportedItems(
      ApiInventoryImportResult importResult, ApiInventoryImportResult csvResult) {

    if (importResult.getDefaultContainer() == null) {
      createDefaultContainerForImportedItems(importResult, csvResult);
    }
    return importResult.getDefaultContainer();
  }

  private void createDefaultContainerForImportedItems(
      ApiInventoryImportResult importResult, ApiInventoryImportResult csvResult) {

    String name =
        "imported items " + DateUtil.convertDateToISOFormat(new Date(), TimeZone.getDefault());
    ApiContainer containerToCreate = new ApiContainer(name, ContainerType.LIST);
    containerToCreate.setDescription(generateDefaultContainerDescription(csvResult));
    ApiContainer createdContainer =
        containerManager.createNewApiContainer(containerToCreate, importResult.getCurrentUser());
    importResult.setDefaultContainer(createdContainer);
  }

  private String generateDefaultContainerDescription(ApiInventoryImportResult csvResult) {
    return String.format(
        "Default container for items imported from CSV file(s): %s %s %s",
        (csvResult.getContainerCsvFilename() == null
            ? ""
            : "<br> * " + csvResult.getContainerCsvFilename()),
        (csvResult.getSampleCsvFilename() == null
            ? ""
            : "<br> * " + csvResult.getSampleCsvFilename()),
        (csvResult.getSubSampleCsvFilename() == null
            ? ""
            : "<br> * " + csvResult.getSubSampleCsvFilename()));
  }

  void importSamples(ApiInventoryImportResult importResult, ApiInventoryImportResult csvResult) {

    ApiInventoryImportSampleImportResult sampleCsvResult = csvResult.getSampleResult();
    ApiInventoryImportSampleImportResult sampleImportResult =
        sampleCsvResult.copyWithTemplateResultAndImportIdsOnly();
    importResult.setSampleResult(sampleImportResult);

    int sampleCount = 0;
    for (ApiInventoryBulkOperationRecordResult toImport : sampleCsvResult.getResults()) {
      ApiSampleWithFullSubSamples sampleToImport =
          (ApiSampleWithFullSubSamples) toImport.getRecord();
      try {
        // try saving a sample
        BindException errors = new BindException(sampleToImport, "sample");
        ApiSampleWithFullSubSamples importedSample =
            samplesController.createNewSample(
                sampleToImport, errors, importResult.getCurrentUser());
        sampleImportResult.addSuccessResult(importedSample);
      } catch (Exception e) {
        log.warn("Error on saving sample from line: " + (sampleCount + 1), e);
        ApiError error = bulkOperationHandler.convertExceptionToApiError(e);
        sampleImportResult.addError(error);
        throw new InventoryImportException(error, importResult);
      }
      sampleCount++;
    }

    moveDefaultSubSamplesIntoSampleRequestedContainer(csvResult, importResult);
  }

  private void moveDefaultSubSamplesIntoSampleRequestedContainer(
      ApiInventoryImportResult csvResult, ApiInventoryImportResult importResult) {

    ApiInventoryImportSampleImportResult sampleCsvResult = csvResult.getSampleResult();
    ApiInventoryImportSampleImportResult sampleImportResult = importResult.getSampleResult();

    // move default subsamples into requested parent container, or into default workbench container
    for (int sampleCount = 0; sampleCount < sampleImportResult.getSuccessCount(); sampleCount++) {
      // non-default subsamples, i.e. ones added from subsamples csv, will be moved later
      if (sampleCsvResult.isSampleRecordNumberWithNonDefaultSubSample(sampleCount)) {
        continue;
      }

      // find parent import id parent of
      ApiInventoryBulkOperationRecordResult importedRecordResult =
          sampleImportResult.getResults().get(sampleCount);
      ApiSampleWithFullSubSamples importedSample =
          (ApiSampleWithFullSubSamples) importedRecordResult.getRecord();

      List<ApiSubSample> movedSubSamples = new ArrayList<>();
      for (ApiSubSample importedSubSample : importedSample.getSubSamples()) {
        String parentImportId =
            sampleCsvResult.getParentContainerImportIdForResultNumber(sampleCount);
        GlobalIdentifier parentGlobalId =
            sampleCsvResult.getParentContainerGlobalIdForResultNumber(sampleCount);
        ApiContainer targetParent = null;
        if (parentImportId != null) {
          ApiInventoryBulkOperationRecordResult importedParentContainerResult =
              importResult.getContainerResult().getResultForImportId(parentImportId);
          targetParent =
              importedParentContainerResult != null
                  ? (ApiContainer) importedParentContainerResult.getRecord()
                  : null;
        } else if (parentGlobalId != null) {
          targetParent = new ApiContainer(); // permission were checked during prevalidation
          targetParent.setId(parentGlobalId.getDbId());
        }
        if (targetParent == null) {
          targetParent = getDefaultContainerForImportedItems(importResult, csvResult);
        }
        try {
          ApiSubSample moveRequest = new ApiSubSample();
          moveRequest.setId(importedSubSample.getId());
          moveRequest.setParentContainer(targetParent);
          ApiSubSample updatedSubSample =
              subSampleManager.updateApiSubSample(moveRequest, importResult.getCurrentUser());
          movedSubSamples.add(updatedSubSample);

        } catch (Exception e) {
          log.warn(
              "Error on moving subsample of sample ["
                  + importedSample.getName()
                  + "] into parent container ["
                  + (targetParent != null ? targetParent.getName() : null)
                  + "]",
              e);
          ApiError error = bulkOperationHandler.convertExceptionToApiError(e);
          sampleImportResult.changeIntoErrorResult(importedRecordResult, error);
          throw new InventoryImportException(error, importResult);
        }
      }
      importedSample.setSubSamples(movedSubSamples);
    }
  }

  void populateSubSampleResultsWithImportedSubSamples(
      ApiInventoryImportResult importResult,
      ApiInventoryImportSubSampleImportResult subSampleCsvResult) {

    if (subSampleCsvResult == null) {
      return;
    }

    ApiInventoryImportSubSampleImportResult subSampleImportResult =
        new ApiInventoryImportSubSampleImportResult();
    importResult.setSubSampleResult(subSampleImportResult);

    ApiInventoryImportSampleImportResult importedSamples = importResult.getSampleResult();
    Map<String, Integer> processedSubSamplesForSampleId = new HashMap<>();

    for (int resultCount = 0; resultCount < subSampleCsvResult.getResults().size(); resultCount++) {
      String parentSampleImportId =
          subSampleCsvResult.getParentSampleImportIdForResultNumber(resultCount);
      if (parentSampleImportId == null) {
        /* must be subsample providing parent sample global id, it will be imported & updated separately,
        just copy it into results now so the ordering is kept */
        subSampleImportResult.addSuccessResult(
            subSampleCsvResult.getResults().get(resultCount).getRecord());
        continue;
      }
      ApiInventoryBulkOperationRecordResult sampleResultForImportId =
          importedSamples.getResultForImportId(parentSampleImportId);
      ApiSampleWithFullSubSamples sample =
          (ApiSampleWithFullSubSamples) sampleResultForImportId.getRecord();

      Integer subSampleIndex = processedSubSamplesForSampleId.get(parentSampleImportId);
      subSampleIndex = subSampleIndex == null ? 0 : subSampleIndex + 1;
      processedSubSamplesForSampleId.put(parentSampleImportId, subSampleIndex);

      ApiSubSample subSampleAtIndex = sample.getSubSamples().get(subSampleIndex);
      subSampleImportResult.addSuccessResult(subSampleAtIndex);
    }
  }

  void importSubSamplesIntoPreexistingSamples(
      ApiInventoryImportResult importResult,
      ApiInventoryImportSubSampleImportResult subSampleCsvResult) {
    List<ApiInventoryBulkOperationRecordResult> csvSubSamples = subSampleCsvResult.getResults();
    for (int resultCount = 0; resultCount < csvSubSamples.size(); resultCount++) {
      ApiInventoryBulkOperationRecordResult csvResult = csvSubSamples.get(resultCount);
      ApiSubSample subSample = (ApiSubSample) csvResult.getRecord();
      GlobalIdentifier parentSampleGlobalId =
          subSampleCsvResult.getParentSampleGlobalIdForResultNumber(resultCount);
      if (parentSampleGlobalId == null) {
        continue; // must be subsample providing parent sample import id, already imported
      }

      ApiInventoryBulkOperationRecordResult currentResult =
          importResult.getSubSampleResult().getResults().get(resultCount);
      try {
        ApiSubSample importedSubSample =
            subSampleManager.addNewApiSubSampleToSample(
                subSample, parentSampleGlobalId.getDbId(), importResult.getCurrentUser());
        currentResult.setRecord(importedSubSample);

      } catch (Exception e) {
        log.warn("Error on saving subsample from line: " + (resultCount + 1), e);
        ApiError error = bulkOperationHandler.convertExceptionToApiError(e);
        importResult.getSubSampleResult().changeIntoErrorResult(currentResult, error);
        throw new InventoryImportException(error, importResult);
      }
    }
  }

  void moveImportedSubSamplesIntoRequestedContainers(
      ApiInventoryImportResult csvResult, ApiInventoryImportResult importResult) {

    ApiInventoryImportSubSampleImportResult subSampleCsvResult = csvResult.getSubSampleResult();
    for (int subSampleCount = 0;
        subSampleCount < subSampleCsvResult.getSuccessCount();
        subSampleCount++) {
      String parentContainerImportId =
          subSampleCsvResult.getParentContainerImportIdForResultNumber(subSampleCount);
      GlobalIdentifier parentContainerGlobalId =
          subSampleCsvResult.getParentContainerGlobalIdForResultNumber(subSampleCount);

      // if subsample doesn't set parent container check if imported parent sample maybe does
      String parentSampleImportId =
          subSampleCsvResult.getParentSampleImportIdForResultNumber(subSampleCount);
      if (parentContainerImportId == null
          && parentContainerGlobalId == null
          && parentSampleImportId != null) {
        Integer parentSampleResultNumber =
            csvResult.getSampleResult().getResultNumberForImportId(parentSampleImportId);
        parentContainerImportId =
            csvResult
                .getSampleResult()
                .getParentContainerImportIdForResultNumber(parentSampleResultNumber);
        parentContainerGlobalId =
            csvResult.getSampleResult().getParentContainerGlobalIdForResultNumber(subSampleCount);
      }

      ApiContainer targetParent = null;
      if (parentContainerImportId != null) {
        ApiInventoryBulkOperationRecordResult importedParentContainerResult =
            importResult.getContainerResult().getResultForImportId(parentContainerImportId);
        targetParent =
            importedParentContainerResult != null
                ? (ApiContainer) importedParentContainerResult.getRecord()
                : null;
      } else if (parentContainerGlobalId != null) {
        targetParent = new ApiContainer(); // permission were checked during prevalidation
        targetParent.setId(parentContainerGlobalId.getDbId());
      }
      if (targetParent == null) {
        targetParent = getDefaultContainerForImportedItems(importResult, csvResult);
      }
      ApiInventoryBulkOperationRecordResult importedSubSample =
          importResult.getSubSampleResult().getResults().get(subSampleCount);
      try {
        ApiSubSample moveRequest = new ApiSubSample();
        moveRequest.setId(importedSubSample.getRecord().getId());
        moveRequest.setParentContainer(targetParent);
        ApiSubSample updatedSubSample =
            subSampleManager.updateApiSubSample(moveRequest, importResult.getCurrentUser());
        importedSubSample.setRecord(updatedSubSample);

      } catch (Exception e) {
        log.warn(
            "Error on moving subsample "
                + importedSubSample.getRecord().getName()
                + "] into parent container ["
                + (targetParent != null ? targetParent.getName() : null)
                + "]",
            e);
        ApiError error = bulkOperationHandler.convertExceptionToApiError(e);
        importResult.getSubSampleResult().changeIntoErrorResult(importedSubSample, error);
        throw new InventoryImportException(error, importResult);
      }
    }
  }

  private void reloadSamplesInImportResult(ApiInventoryImportResult importResult) {
    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    if (sampleResults != null) {
      for (ApiInventoryBulkOperationRecordResult result : sampleResults.getResults()) {
        ApiSample reloadedSample =
            sampleManager.getApiSampleById(
                result.getRecord().getId(), importResult.getCurrentUser());
        result.setRecord(reloadedSample);
      }
    }
  }

  private void reloadContainersInImportResult(ApiInventoryImportResult importResult) {
    if (importResult.getDefaultContainer() != null) {
      Long defaultContainerId = importResult.getDefaultContainer().getId();
      importResult.setDefaultContainer(
          containerManager.getApiContainerById(defaultContainerId, importResult.getCurrentUser()));
    }
    if (importResult.getContainerResult() != null) {
      for (ApiInventoryBulkOperationRecordResult result :
          importResult.getContainerResult().getResults()) {
        Long containerId = result.getRecord().getId();
        result.setRecord(
            containerManager.getApiContainerById(containerId, importResult.getCurrentUser()));
      }
    }
  }
}

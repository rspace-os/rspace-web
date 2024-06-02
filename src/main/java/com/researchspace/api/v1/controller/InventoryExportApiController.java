package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.InventoryExportApi;
import com.researchspace.api.v1.model.ApiExportJobResult;
import com.researchspace.api.v1.model.ApiGlobalIdsRequest;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.service.impl.ApiExportJobResultFactory;
import com.researchspace.api.v1.service.impl.ExportTasklet;
import com.researchspace.api.v1.service.impl.JobExecutionFacade;
import com.researchspace.archive.ArchiveUtils;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.service.inventory.InventoryExportManager;
import com.researchspace.service.inventory.csvexport.CsvContentToExport;
import com.researchspace.service.inventory.csvexport.CsvExportMode;
import com.researchspace.service.inventory.csvexport.InventoryExportFileCreator;
import com.researchspace.webapp.config.WebConfig;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@ApiController
public class InventoryExportApiController extends BaseApiInventoryController
    implements InventoryExportApi {

  @Autowired private JobsApiController jobsApiController;

  @Autowired private InventoryExportManager exportManager;

  @Autowired private InventoryExportPostValidator exportPostValidator;

  @Autowired private InventoryExportFileCreator exportFileCreator;

  @Autowired private ApiExportJobResultFactory exportJobResultFactory;

  @Data
  @EqualsAndHashCode(callSuper = true)
  @NoArgsConstructor
  public static class ApiInventoryExportSettingsPost extends ApiGlobalIdsRequest {

    @Pattern(
        regexp = "(FULL)|(COMPACT)",
        flags = {Pattern.Flag.CASE_INSENSITIVE},
        message = "exportMode should be either 'FULL' or 'COMPACT'")
    @JsonProperty("exportMode")
    private String exportMode;

    @Pattern(
        regexp = "(ZIP)|(SINGLE_CSV)",
        flags = {Pattern.Flag.CASE_INSENSITIVE},
        message = "resultFileType should be either 'ZIP' or 'SINGLE_CSV'")
    @JsonProperty("resultFileType")
    private String resultFileType;

    @JsonProperty("includeContainerContent")
    private boolean includeContainerContent = false;

    @JsonProperty("includeSubsamplesInSample")
    private boolean includeSubsamplesInSample = true;

    @JsonProperty("users")
    private List<String> usernamesToExport;
  }

  /**
   * Converts json object coming together with uploaded import file. As other custom converters, is
   * registered in {@link WebConfig#mvcConversionService()}
   */
  public static class ApiInventoryExportPostConverter
      implements Converter<String, ApiInventoryExportSettingsPost> {
    @Override
    public ApiInventoryExportSettingsPost convert(String source) {
      try {
        return new ObjectMapper().readValue(source, ApiInventoryExportSettingsPost.class);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public ApiJob exportToCsv(
      @RequestParam("exportSettings") ApiInventoryExportSettingsPost settings,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws BindException, IOException {

    BindingResult errors = new BeanPropertyBindingResult(settings, "exportSettings");
    inputValidator.validate(settings, exportPostValidator, errors);
    throwBindExceptionIfErrors(errors);

    CsvContentToExport csvContent = null;
    CsvExportMode exportMode = CsvExportMode.FULL;
    if (StringUtils.isNotBlank(settings.getExportMode())) {
      exportMode = CsvExportMode.valueOf(settings.getExportMode());
    }
    boolean includeSampleContent = settings.isIncludeSubsamplesInSample();
    boolean includeContainerContent = settings.isIncludeContainerContent();

    List<String> globalIdStrings = settings.getGlobalIds();
    if (globalIdStrings != null && !globalIdStrings.isEmpty()) {
      List<GlobalIdentifier> globalIds =
          globalIdStrings.stream().map(GlobalIdentifier::new).collect(Collectors.toList());
      csvContent =
          exportManager.exportSelectedItemsAsCsvContent(
              globalIds, exportMode, includeSampleContent, includeContainerContent, user);
    }

    List<String> usernamesToExport = settings.getUsernamesToExport();
    if (usernamesToExport != null && !usernamesToExport.isEmpty()) {
      csvContent =
          exportManager.exportUserItemsAsCsvContent(
              usernamesToExport, exportMode, includeContainerContent, user);
    }

    File exportedFile = prepareExportFile(csvContent, settings.getResultFileType());
    String exportedFileName = exportedFile.getName();
    log.info("saved exported csv to: " + exportedFile.getAbsolutePath());

    ApiJob job = new ApiJob(null, BatchStatus.COMPLETED.toString());
    job.setCompleted(true);
    job.setPercentComplete(100);

    ApiExportJobResult exportJobResult = getApiExportJobResult(job, exportedFileName, "");
    exportJobResult.setChecksum(ArchiveUtils.calculateChecksum(exportedFile) + "");
    exportJobResult.setAlgorithm("CRC32");
    exportJobResult.setSize(exportedFile.length());
    job.setResult(exportJobResult);

    job.addEnclosureLink(
        ArchiveUtils.getApiExportDownloadLink(properties.getServerUrl(), exportedFileName));
    job.addLink(
        ArchiveUtils.getExportDownloadLink(properties.getServerUrl(), exportedFileName),
        ApiLinkItem.DOWNLOAD_LINK_REL);

    return job;
  }

  private File prepareExportFile(CsvContentToExport csvContent, String resultFileType)
      throws IOException {
    if ("SINGLE_CSV".equals(resultFileType)) {
      return exportFileCreator.saveCsvContentIntoExportFolder(
          csvContent.getCombinedContent(), null, null);
    }
    return exportAndZipCsvContent(csvContent);
  }

  private File exportAndZipCsvContent(CsvContentToExport csvContent) throws IOException {

    String currentIsoDate =
        Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-");
    String filenamePrefix = "rsinventory_export_" + currentIsoDate;

    File assemblyFolder = exportFileCreator.createCsvArchiveAssemblyFolder();
    for (Entry<String, String> entry : csvContent.getNamesAndContentMap().entrySet()) {
      exportFileCreator.saveCsvContentIntoExportFolder(
          entry.getValue(), filenamePrefix + "_" + entry.getKey() + ".csv", assemblyFolder);
    }
    return exportFileCreator.zipAssemblyFolder(assemblyFolder);
  }

  private ApiExportJobResult getApiExportJobResult(ApiJob job, String fileName, String csumId) {
    JobExecution jobExecution = new JobExecution((Long) null);
    jobExecution.setStatus(BatchStatus.COMPLETED);
    JobExecutionFacade jobExecutionFacade = new JobExecutionFacade(jobExecution);
    jobExecutionFacade.addStringToContext(ExportTasklet.JOB_TYPE_KEY, ExportTasklet.JOB_TYPE);
    jobExecutionFacade.addStringToContext(ExportTasklet.EXPORT_RESULT_KEY, fileName);
    jobExecutionFacade.addStringToContext(ExportTasklet.EXPORT_ID, csumId);
    return exportJobResultFactory.createResult(jobExecution, job);
  }
}

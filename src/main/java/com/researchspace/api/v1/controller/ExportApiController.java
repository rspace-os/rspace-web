package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.ExportApi;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.service.ExportApiHandler;
import com.researchspace.model.User;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.export.ExportFailureException;
import java.util.Set;
import java.util.TreeSet;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;

/** Only active in 'run' mode while dev */
@ApiController
@Slf4j
public class ExportApiController extends BaseApiController implements ExportApi {
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ExportApiConfig {
    @NotNull
    @Pattern(regexp = "(xml)|(html)", message = "format {errors.required.field}")
    private String format;

    @NotNull
    @Pattern(regexp = "(user)|(group)|(selection)", message = "scope {errors.required.field}")
    private String scope;

    private Long id = null;
    private Set<Long> selections = new TreeSet<>();

    private boolean includeRevisionHistory = false;

    @Min(0)
    private Integer maxLinkLevel = 1;

    public ExportApiConfig(String format, String scope) {
      super();
      this.format = format;
      this.scope = scope;
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ExportRetrievalConfig {
    @NotBlank
    @Pattern(regexp = ".*(\\.zip)?", message = "File must be a zip name")
    private String resource;
  }

  private @Autowired ExportApiHandler handler;
  private @Autowired ExportImport exportImportService;

  @Override
  public ApiJob export(
      @Valid ExportApiConfig cfg, BindingResult errors, @RequestAttribute User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    ApiJob job =
        handler
            .export(cfg, user)
            .orElseThrow(() -> new ExportFailureException("couldn't launch export job"));
    addJobProgressLink(job);
    return job;
  }

  // config has none-null id
  @Override
  public ApiJob exportById(
      ExportApiConfig config, BindingResult errors, @RequestAttribute User user)
      throws BindException {
    return export(config, errors, user);
  }

  private void addJobProgressLink(ApiJob job) {
    job.addLink(buildJobLink(job), ApiLinkItem.SELF_REL);
  }

  private String buildJobLink(final ApiJob job) {
    String path = JOBS_INTERNAL_ENDPOINT + "/" + job.getId();
    return getApiBaseURI().path(path).build().encode().toUriString();
  }

  @Override
  public void getExport(
      @Valid ExportRetrievalConfig cfg, BindingResult errors, HttpServletResponse response)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    exportImportService.streamArchiveDownload(cfg.getResource(), response);
  }
}

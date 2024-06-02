package com.researchspace.export.pdf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.IExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.preference.ExportPageSize;
import java.util.Date;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

/** user defined configuration details for export to external format */
@Data
@AuditTrailData(auditDomain = AuditDomain.RECORD)
public class ExportToFileConfig implements StructuredDocumentHTMLViewConfig, IExportConfig {

  /** Various dates that could be put into exported file footer. */
  public enum DATE_FOOTER_PREF {
    /** Date of export */
    EXP,
    /** Date document was created */
    NEW,
    /** Last modified date */
    UPD
  }

  private Date createDt;
  @JsonIgnore private User exporter;
  private int startPage;

  /**
   * Gets export scope - default is ExportScope.SELECTION
   *
   * @return
   */
  private ExportScope exportScope = ExportScope.SELECTION;

  private ExportFormat exportFormat = ExportFormat.PDF;

  @NotEmpty(message = "PDF name {errors.required.field}")
  private String exportName = "PDF export";

  private boolean provenance = true;
  private boolean comments = true;
  private boolean annotations = true;
  private boolean pageName = true;
  private boolean includeFieldLastModifiedDate = true;

  @NotNull private ExportPageSize pageSize = ExportPageSize.A4;

  @NotNull private DATE_FOOTER_PREF dateType = DATE_FOOTER_PREF.EXP;
  private boolean includeFooterAtEndOnly = true;

  @NotNull private Boolean setPageSizeAsDefault = false;

  @AuditTrailProperty(name = "format")
  public ExportFormat getExportFormat() {
    return exportFormat;
  }

  @AuditTrailProperty(name = "scope")
  public ExportScope getExportScope() {
    return exportScope;
  }

  /**
   * A name() String of an {@link ExportFormat} enum
   *
   * @param exportFormat
   */
  public void setExportFormat(String exportFormat) {
    this.exportFormat = ExportFormat.valueOf(exportFormat);
  }

  public String getPageSize() {
    return pageSize.name();
  }

  public ExportPageSize getPageSizeEnum() {
    return pageSize;
  }

  public void setPageSize(String pageSize) {
    this.pageSize = ExportPageSize.valueOf(pageSize);
  }

  public String getDateType() {
    return dateType.name();
  }

  public DATE_FOOTER_PREF getDateTypeEnum() {
    return dateType;
  }

  public void setDateType(String dateType) {
    this.dateType = DATE_FOOTER_PREF.valueOf(dateType);
  }

  @Override
  public String toString() {
    return "PdfConfigInfo [startPage="
        + startPage
        + ", provenance="
        + provenance
        + ", comments="
        + comments
        + ", annotations="
        + annotations
        + ", pageName="
        + pageName
        + ", pageSize="
        + pageSize
        + ", dateType="
        + dateType
        + ", includeFooter="
        + includeFooterAtEndOnly
        + ", exportName="
        + exportName
        + "]";
  }

  public boolean isSelectionScope() {
    return ExportScope.SELECTION.equals(exportScope);
  }

  @Override
  public boolean isUserScope() {
    return ExportScope.USER.equals(exportScope);
  }

  @Override
  public boolean isGroupScope() {
    return ExportScope.GROUP.equals(exportScope);
  }
}

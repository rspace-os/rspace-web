package com.researchspace.service.inventory.csvexport;

import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.inventory.field.SampleField;
import java.io.IOException;
import java.io.OutputStream;
import org.springframework.stereotype.Component;

@Component
public class CsvSampleTemplateExporter extends CsvSampleExporter {

  // additional props to export for templates
  private static ExportableInvRecProperty[] TEMPLATE_EXPORTABLE_PROPS = {
    ExportableInvRecProperty.EXPIRY_DATE,
    ExportableInvRecProperty.SAMPLE_SOURCE,
    ExportableInvRecProperty.STORAGE_TEMPERATURE_MIN,
    ExportableInvRecProperty.STORAGE_TEMPERATURE_MAX
  };

  protected ExportableInvRecProperty[] getExportableProps() {
    return TEMPLATE_EXPORTABLE_PROPS;
  }

  protected String getColumnNameForSampleField(SampleField sf) {
    return String.format("%s (%s)", sf.getName(), sf.getType());
  }

  public OutputStream getCsvCommentFragmentForSamples(
      ExportScope selection, CsvExportMode exportMode, User user) throws IOException {
    return getCsvCommentFragment(
        ExportedContentType.SAMPLE_TEMPLATES.toString(), selection, exportMode, user);
  }
}

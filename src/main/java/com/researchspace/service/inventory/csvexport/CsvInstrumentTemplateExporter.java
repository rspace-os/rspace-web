package com.researchspace.service.inventory.csvexport;

import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.io.IOException;
import java.io.OutputStream;
import org.springframework.stereotype.Component;

/**
 * CSV exporter for {@link com.researchspace.model.inventory.InstrumentTemplate}. Reuses
 * field/extra-field column emission from {@link CsvInstrumentExporter} and drops the
 * instance-specific properties (parent template / parent container) that don't apply to a template.
 */
@Component
public class CsvInstrumentTemplateExporter extends CsvInstrumentExporter {

  private static final ExportableInvRecProperty[] TEMPLATE_EXPORTABLE_PROPS = {};

  @Override
  protected ExportableInvRecProperty[] getExportableProps() {
    return TEMPLATE_EXPORTABLE_PROPS;
  }

  @Override
  protected String getColumnNameForInstrumentField(InventoryEntityField sf) {
    return String.format("%s (%s)", sf.getName(), sf.getType());
  }

  public OutputStream getCsvCommentFragmentForInstrumentTemplates(
      ExportScope selection, CsvExportMode exportMode, User user) throws IOException {
    return getCsvCommentFragment(
        ExportedContentType.INSTRUMENT_TEMPLATES.toString(), selection, exportMode, user);
  }
}

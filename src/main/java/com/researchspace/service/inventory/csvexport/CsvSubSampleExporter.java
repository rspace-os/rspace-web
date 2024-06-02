package com.researchspace.service.inventory.csvexport;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.SubSampleNote;
import com.researchspace.service.inventory.csvexport.model.CsvSubSampleNote;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;
import org.jsoup.helper.Validate;
import org.springframework.stereotype.Component;

@Component
public class CsvSubSampleExporter extends InventoryItemCsvExporter {

  // additional props to export for subsamples
  private static ExportableInvRecProperty[] SUBSAMPLE_EXPORTABLE_PROPS = {
    ExportableInvRecProperty.SAMPLE_GLOBAL_ID,
    ExportableInvRecProperty.CONTAINER_GLOBAL_ID,
    ExportableInvRecProperty.QUANTITY,
    ExportableInvRecProperty.NOTES
  };

  public List<String> writeSubSampleCsvHeaderIntoOutput(
      List<SubSample> subSamples,
      CsvExportMode exportMode,
      CsvMapper mapper,
      OutputStream outputStream)
      throws IOException {
    if (mapper == null) {
      mapper = getCsvMapper();
    }
    Validate.notNull(outputStream, "output stream cannot be null");

    List<String> columnNames = getSubSampleColumnNamesForCsv(subSamples, exportMode);
    writeCsvLine(mapper, outputStream, columnNames);

    return columnNames;
  }

  private List<String> getSubSampleColumnNamesForCsv(
      List<SubSample> subSamples, CsvExportMode exportMode) {
    List<String> columnNames = super.getBasicColumnNamesForCsv(exportMode);
    for (ExportableInvRecProperty prop : SUBSAMPLE_EXPORTABLE_PROPS) {
      columnNames.add(prop.getCsvColumnHeader());
    }
    columnNames.addAll(
        getExtraFieldColumnNamesForCsv(getExtraFieldsFromAllItems(subSamples), exportMode));
    return columnNames;
  }

  protected List<String> writeSubSampleCsvDetailsIntoOutput(
      SubSample subSample,
      List<String> csvColumnNames,
      CsvExportMode exportMode,
      CsvMapper mapper,
      OutputStream outputStream)
      throws IOException {

    if (mapper == null) {
      mapper = getCsvMapper();
    }
    Validate.notNull(outputStream, "output stream cannot be null");

    List<String> properties = getSubSamplePropertiesForCsv(subSample, csvColumnNames, exportMode);
    writeCsvLine(mapper, outputStream, properties);

    return properties;
  }

  private List<String> getSubSamplePropertiesForCsv(
      SubSample subSample, List<String> csvColumnNames, CsvExportMode exportMode) {
    List<String> itemProperties = getBasicItemPropertiesForExport(subSample, exportMode);
    addSubSampleSpecificPropertiesForExport(subSample, itemProperties);
    addVariableItemPropertiesForExport(subSample, exportMode, csvColumnNames, itemProperties);
    return itemProperties;
  }

  private void addSubSampleSpecificPropertiesForExport(
      SubSample subSample, List<String> itemProperties) {
    for (ExportableInvRecProperty prop : SUBSAMPLE_EXPORTABLE_PROPS) {
      String valueForProp = null;
      switch (prop) {
        case QUANTITY:
          valueForProp =
              subSample.getQuantityInfo() != null
                  ? subSample.getQuantityInfo().toPlainString()
                  : null;
          break;
        case CONTAINER_GLOBAL_ID:
          valueForProp =
              subSample.getParentContainer() != null
                  ? subSample.getParentContainer().getGlobalIdentifier()
                  : null;
          break;
        case SAMPLE_GLOBAL_ID:
          valueForProp =
              subSample.getSample() != null ? subSample.getSample().getGlobalIdentifier() : null;
          break;
        case NOTES:
          valueForProp =
              subSample.getNotes() != null
                  ? convertNotesToCsvStringValue(subSample.getNotes())
                  : null;
          break;
        default:
          throw new IllegalStateException("unhandled property: " + prop);
      }
      itemProperties.add(valueForProp != null ? valueForProp : "");
    }
  }

  String convertNotesToCsvStringValue(List<SubSampleNote> notes) {
    if (notes.isEmpty()) {
      return "";
    }

    List<String> convertedNotes =
        notes.stream().map(n -> new CsvSubSampleNote(n).toCsvString()).collect(Collectors.toList());
    return convertListToCsvStringValue(convertedNotes);
  }

  public OutputStream getCsvFragmentForSubSamples(
      List<SubSample> subSamples, CsvExportMode exportMode) throws IOException {
    CsvMapper mapper = getCsvMapper();
    OutputStream outputStream = new ByteArrayOutputStream();

    if (!subSamples.isEmpty()) {
      List<String> columnNames =
          writeSubSampleCsvHeaderIntoOutput(subSamples, exportMode, mapper, outputStream);
      for (SubSample ss : subSamples) {
        writeSubSampleCsvDetailsIntoOutput(ss, columnNames, exportMode, mapper, outputStream);
      }
    }
    return outputStream;
  }

  public OutputStream getCsvCommentFragmentForSubSamples(
      ExportScope selection, CsvExportMode exportMode, User user) throws IOException {
    return getCsvCommentFragment(
        ExportedContentType.SUBSAMPLES.toString(), selection, exportMode, user);
  }
}

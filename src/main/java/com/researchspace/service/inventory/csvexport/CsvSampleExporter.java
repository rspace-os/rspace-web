package com.researchspace.service.inventory.csvexport;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.field.SampleField;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jsoup.helper.Validate;
import org.springframework.stereotype.Component;

@Component
public class CsvSampleExporter extends InventoryItemCsvExporter {

  // additional props to export for samples
  private static ExportableInvRecProperty[] SAMPLE_EXPORTABLE_PROPS = {
    ExportableInvRecProperty.TEMPLATE_GLOBAL_ID,
    ExportableInvRecProperty.TEMPLATE_NAME,
    ExportableInvRecProperty.TOTAL_QUANTITY,
    ExportableInvRecProperty.EXPIRY_DATE,
    ExportableInvRecProperty.SAMPLE_SOURCE,
    ExportableInvRecProperty.STORAGE_TEMPERATURE_MIN,
    ExportableInvRecProperty.STORAGE_TEMPERATURE_MAX
  };

  public List<String> writeSampleCsvHeaderIntoOutput(
      List<Sample> samples, CsvExportMode exportMode, CsvMapper mapper, OutputStream outputStream)
      throws IOException {
    if (mapper == null) {
      mapper = getCsvMapper();
    }
    Validate.notNull(outputStream, "output stream cannot be null");

    List<String> columnNames = getSampleColumnNamesForCsv(samples, exportMode);
    writeCsvLine(mapper, outputStream, columnNames);

    return columnNames;
  }

  protected ExportableInvRecProperty[] getExportableProps() {
    return SAMPLE_EXPORTABLE_PROPS;
  }

  private List<String> getSampleColumnNamesForCsv(List<Sample> samples, CsvExportMode exportMode) {
    List<String> columnNames = super.getBasicColumnNamesForCsv(exportMode);
    for (ExportableInvRecProperty prop : getExportableProps()) {
      columnNames.add(prop.getCsvColumnHeader());
    }
    columnNames.addAll(
        getSampleFieldColumnNamesForCsv(getSampleFieldsFromAllSamples(samples), exportMode));
    columnNames.addAll(
        getExtraFieldColumnNamesForCsv(getExtraFieldsFromAllItems(samples), exportMode));
    return columnNames;
  }

  private List<SampleField> getSampleFieldsFromAllSamples(List<Sample> samples) {
    return samples.stream().flatMap(s -> s.getActiveFields().stream()).collect(Collectors.toList());
  }

  protected List<String> getSampleFieldColumnNamesForCsv(
      List<SampleField> extraFields, CsvExportMode exportMode) {
    List<String> columnNames = new ArrayList<>();
    if (CsvExportMode.FULL.equals(exportMode) && extraFields != null) {
      for (SampleField sf : extraFields) {
        String sampleFieldColumName = getColumnNameForSampleField(sf);
        if (!columnNames.contains(sampleFieldColumName)) {
          columnNames.add(sampleFieldColumName);
        }
      }
    }
    return columnNames;
  }

  protected String getColumnNameForSampleField(SampleField sf) {
    String connectedTemplateGlobalId = sf.getTemplateField().getSample().getGlobalIdentifier();
    return String.format("%s (%s, %s)", sf.getName(), sf.getType(), connectedTemplateGlobalId);
  }

  protected List<String> writeSampleCsvDetailsIntoOutput(
      Sample sample,
      List<String> csvColumnNames,
      CsvExportMode exportMode,
      CsvMapper mapper,
      OutputStream outputStream)
      throws IOException {

    if (mapper == null) {
      mapper = getCsvMapper();
    }
    Validate.notNull(outputStream, "output stream cannot be null");

    List<String> properties = getSamplePropertiesForCsv(sample, csvColumnNames, exportMode);
    writeCsvLine(mapper, outputStream, properties);

    return properties;
  }

  private List<String> getSamplePropertiesForCsv(
      Sample sample, List<String> csvColumnNames, CsvExportMode exportMode) {
    List<String> itemProperties = getBasicItemPropertiesForExport(sample, exportMode);
    addSampleSpecificPropertiesForExport(sample, itemProperties);
    addVariableSamplePropertiesForExport(sample, exportMode, csvColumnNames, itemProperties);
    addVariableItemPropertiesForExport(sample, exportMode, csvColumnNames, itemProperties);
    return itemProperties;
  }

  private void addSampleSpecificPropertiesForExport(Sample sample, List<String> itemProperties) {
    for (ExportableInvRecProperty prop : getExportableProps()) {
      String valueForProp = null;
      switch (prop) {
        case TOTAL_QUANTITY:
          valueForProp =
              sample.getQuantityInfo() != null ? sample.getQuantityInfo().toPlainString() : null;
          break;
        case EXPIRY_DATE:
          valueForProp = sample.getExpiryDate() != null ? sample.getExpiryDate().toString() : null;
          break;
        case SAMPLE_SOURCE:
          valueForProp = sample.getSampleSource() != null ? sample.getSampleSource().name() : null;
          break;
        case STORAGE_TEMPERATURE_MIN:
          valueForProp =
              sample.getStorageTempMin() != null
                  ? sample.getStorageTempMin().toPlainString()
                  : null;
          break;
        case STORAGE_TEMPERATURE_MAX:
          valueForProp =
              sample.getStorageTempMax() != null
                  ? sample.getStorageTempMax().toPlainString()
                  : null;
          break;
        case TEMPLATE_GLOBAL_ID:
          valueForProp =
              sample.getSTemplate() != null ? sample.getSTemplate().getGlobalIdentifier() : null;
          break;
        case TEMPLATE_NAME:
          valueForProp = sample.getSTemplate() != null ? sample.getSTemplate().getName() : null;
          break;
        default:
          throw new IllegalStateException("unhandled property: " + prop);
      }
      itemProperties.add(valueForProp != null ? valueForProp : "");
    }
  }

  private void addVariableSamplePropertiesForExport(
      Sample sample,
      CsvExportMode exportMode,
      List<String> csvColumnNames,
      List<String> itemProperties) {

    int numberOfCsvColumns = csvColumnNames.size();
    populateItemPropertiesWithNotAvailableValuesForRemainingColumns(
        numberOfCsvColumns, itemProperties);

    if (CsvExportMode.FULL.equals(exportMode) && sample.getActiveExtraFields() != null) {
      for (SampleField sf : sample.getActiveFields()) {
        String valueForProp = sf.getData();
        int columnIndexForValue = csvColumnNames.indexOf(getColumnNameForSampleField(sf));
        itemProperties.set(columnIndexForValue, valueForProp != null ? valueForProp : "");
      }
    }
  }

  public OutputStream getCsvFragmentForSamples(List<Sample> samples, CsvExportMode exportMode)
      throws IOException {
    CsvMapper mapper = getCsvMapper();
    OutputStream outputStream = new ByteArrayOutputStream();
    if (!samples.isEmpty()) {
      List<String> columnNames =
          writeSampleCsvHeaderIntoOutput(samples, exportMode, mapper, outputStream);
      for (Sample s : samples) {
        writeSampleCsvDetailsIntoOutput(s, columnNames, exportMode, mapper, outputStream);
      }
    }
    return outputStream;
  }

  public OutputStream getCsvCommentFragmentForSamples(
      ExportScope selection, CsvExportMode exportMode, User user) throws IOException {
    return getCsvCommentFragment(
        ExportedContentType.SAMPLES.toString(), selection, exportMode, user);
  }
}

package com.researchspace.service.inventory.csvexport;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jsoup.helper.Validate;
import org.springframework.stereotype.Component;

@Component
public class CsvInstrumentExporter extends InventoryItemCsvExporter {

  // additional props to export for instruments (instances)
  private static ExportableInvRecProperty[] INSTRUMENT_EXPORTABLE_PROPS = {
    ExportableInvRecProperty.TEMPLATE_GLOBAL_ID,
    ExportableInvRecProperty.TEMPLATE_NAME,
    ExportableInvRecProperty.CONTAINER_GLOBAL_ID
  };

  public List<String> writeInstrumentCsvHeaderIntoOutput(
      List<? extends InstrumentEntity> instruments,
      CsvExportMode exportMode,
      CsvMapper mapper,
      OutputStream outputStream)
      throws IOException {
    if (mapper == null) {
      mapper = getCsvMapper();
    }
    Validate.notNull(outputStream, "output stream cannot be null");

    List<String> columnNames = getInstrumentColumnNamesForCsv(instruments, exportMode);
    writeCsvLine(mapper, outputStream, columnNames);

    return columnNames;
  }

  protected ExportableInvRecProperty[] getExportableProps() {
    return INSTRUMENT_EXPORTABLE_PROPS;
  }

  private List<String> getInstrumentColumnNamesForCsv(
      List<? extends InstrumentEntity> instruments, CsvExportMode exportMode) {
    List<String> columnNames = super.getBasicColumnNamesForCsv(exportMode);
    for (ExportableInvRecProperty prop : getExportableProps()) {
      columnNames.add(prop.getCsvColumnHeader());
    }
    columnNames.addAll(
        getInstrumentFieldColumnNamesForCsv(
            getInstrumentFieldsFromAllInstruments(instruments), exportMode));
    columnNames.addAll(
        getExtraFieldColumnNamesForCsv(getExtraFieldsFromAllItems(instruments), exportMode));
    return columnNames;
  }

  private List<InventoryEntityField> getInstrumentFieldsFromAllInstruments(
      List<? extends InstrumentEntity> instruments) {
    return instruments.stream()
        .flatMap(i -> i.getActiveFields().stream())
        .collect(Collectors.toList());
  }

  protected List<String> getInstrumentFieldColumnNamesForCsv(
      List<InventoryEntityField> instrumentFields, CsvExportMode exportMode) {
    List<String> columnNames = new ArrayList<>();
    if (CsvExportMode.FULL.equals(exportMode) && instrumentFields != null) {
      for (InventoryEntityField sf : instrumentFields) {
        String instrumentFieldColumName = getColumnNameForInstrumentField(sf);
        if (!columnNames.contains(instrumentFieldColumName)) {
          columnNames.add(instrumentFieldColumName);
        }
      }
    }
    return columnNames;
  }

  protected String getColumnNameForInstrumentField(InventoryEntityField sf) {
    String connectedTemplateGlobalId = "";
    if (sf.getTemplateField() != null) {
      InstrumentEntity parent = sf.getTemplateField().getInstrumentEntity();
      if (parent != null) {
        connectedTemplateGlobalId = parent.getGlobalIdentifier();
      }
    }
    return String.format("%s (%s, %s)", sf.getName(), sf.getType(), connectedTemplateGlobalId);
  }

  protected List<String> writeInstrumentCsvDetailsIntoOutput(
      InstrumentEntity instrument,
      List<String> csvColumnNames,
      CsvExportMode exportMode,
      CsvMapper mapper,
      OutputStream outputStream)
      throws IOException {

    if (mapper == null) {
      mapper = getCsvMapper();
    }
    Validate.notNull(outputStream, "output stream cannot be null");

    List<String> properties = getInstrumentPropertiesForCsv(instrument, csvColumnNames, exportMode);
    writeCsvLine(mapper, outputStream, properties);

    return properties;
  }

  private List<String> getInstrumentPropertiesForCsv(
      InstrumentEntity instrument, List<String> csvColumnNames, CsvExportMode exportMode) {
    List<String> itemProperties = getBasicItemPropertiesForExport(instrument, exportMode);
    addInstrumentSpecificPropertiesForExport(instrument, itemProperties);
    addVariableInstrumentPropertiesForExport(
        instrument, exportMode, csvColumnNames, itemProperties);
    addVariableItemPropertiesForExport(instrument, exportMode, csvColumnNames, itemProperties);
    return itemProperties;
  }

  protected void addInstrumentSpecificPropertiesForExport(
      InstrumentEntity entity, List<String> itemProperties) {
    Instrument instrument = entity.isInstrument() ? (Instrument) entity : null;
    for (ExportableInvRecProperty prop : getExportableProps()) {
      String valueForProp = null;
      switch (prop) {
        case TEMPLATE_GLOBAL_ID:
          valueForProp =
              instrument != null && instrument.getInstrumentTemplate() != null
                  ? instrument.getInstrumentTemplate().getGlobalIdentifier()
                  : null;
          break;
        case TEMPLATE_NAME:
          valueForProp =
              instrument != null && instrument.getInstrumentTemplate() != null
                  ? instrument.getInstrumentTemplate().getName()
                  : null;
          break;
        case CONTAINER_GLOBAL_ID:
          valueForProp =
              instrument != null && instrument.getParentContainer() != null
                  ? instrument.getParentContainer().getGlobalIdentifier()
                  : null;
          break;
        default:
          throw new IllegalStateException("unhandled property: " + prop);
      }
      itemProperties.add(valueForProp != null ? valueForProp : "");
    }
  }

  private void addVariableInstrumentPropertiesForExport(
      InstrumentEntity instrument,
      CsvExportMode exportMode,
      List<String> csvColumnNames,
      List<String> itemProperties) {

    int numberOfCsvColumns = csvColumnNames.size();
    populateItemPropertiesWithNotAvailableValuesForRemainingColumns(
        numberOfCsvColumns, itemProperties);

    if (CsvExportMode.FULL.equals(exportMode) && instrument.getActiveFields() != null) {
      for (InventoryEntityField sf : instrument.getActiveFields()) {
        String valueForProp = sf.getData();
        int columnIndexForValue = csvColumnNames.indexOf(getColumnNameForInstrumentField(sf));
        if (columnIndexForValue >= 0) {
          itemProperties.set(columnIndexForValue, valueForProp != null ? valueForProp : "");
        }
      }
    }
  }

  public OutputStream getCsvFragmentForInstruments(
      List<? extends InstrumentEntity> instruments, CsvExportMode exportMode) throws IOException {
    CsvMapper mapper = getCsvMapper();
    OutputStream outputStream = new ByteArrayOutputStream();
    if (!instruments.isEmpty()) {
      List<String> columnNames =
          writeInstrumentCsvHeaderIntoOutput(instruments, exportMode, mapper, outputStream);
      for (InstrumentEntity i : instruments) {
        writeInstrumentCsvDetailsIntoOutput(i, columnNames, exportMode, mapper, outputStream);
      }
    }
    return outputStream;
  }

  public OutputStream getCsvCommentFragmentForInstruments(
      ExportScope selection, CsvExportMode exportMode, User user) throws IOException {
    return getCsvCommentFragment(
        ExportedContentType.INSTRUMENTS.toString(), selection, exportMode, user);
  }
}

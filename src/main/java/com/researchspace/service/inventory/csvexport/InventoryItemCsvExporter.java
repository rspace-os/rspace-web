package com.researchspace.service.inventory.csvexport;

import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.service.inventory.csvexport.CsvExportCommentGenerator.ExportedCommentProperty;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class InventoryItemCsvExporter {

  public static final String CSV_COMMENT_PREFIX = "#";
  public static final String CSV_COMMENT_HEADER = "RSpace Inventory Export";

  public static final String CSV_VALUE_LIST_SEPARATOR = "\n----\n";

  public static final String CSV_VALUE_UNAVAILABLE_ITEM_PROPERTY = "#N/A";

  public enum ExportableInvRecProperty {
    GLOBAL_ID("Global ID"),
    NAME("Name"),
    DESCRIPTION("Description"),
    TAGS("Tags"),
    OWNER("Owner"),
    EXTRA_FIELDS("Extra Fields"),

    CONTAINER_GLOBAL_ID("Parent Container (Global ID)"),
    SAMPLE_GLOBAL_ID("Parent Sample (Global ID)"),
    QUANTITY("Quantity"),
    NOTES("Notes"),
    EXPIRY_DATE("Expiry Date"),

    SAMPLE_SOURCE("Sample Source"),
    STORAGE_TEMPERATURE_MIN("Storage Temperature (min)"),
    STORAGE_TEMPERATURE_MAX("Storage Temperature (max)"),
    TOTAL_QUANTITY("Total Quantity"),
    TEMPLATE_GLOBAL_ID("Parent Template (Global ID)"),
    TEMPLATE_NAME("Parent Template (name)"),

    CONTAINER_TYPE("Container Type"),
    CAN_STORE_CONTAINERS("Can Store Containers (Y/N)"),
    CAN_STORE_SUBSAMPLES("Can Store Subsamples (Y/N)"),
    STORED_CONTAINERS_COUNT("Number of Stored Containers"),
    STORED_SUBSAMPLES_COUNT("Number of Stored Subsamples"),

    LOM_GLOBAL_ID("List of Materials (Global ID)"),
    LOM_NAME("List of Materials (Name)"),
    MATERIAL_GLOBAL_ID("Used Material (Global ID)"),
    MATERIAL_TYPE("Used Material (Type)"),
    MATERIAL_NAME("Used Material (Name)"),
    USED_QUANTITY("Used Quantity");

    @Getter private final String csvColumnHeader;

    private ExportableInvRecProperty(String csvColumnHeader) {
      this.csvColumnHeader = csvColumnHeader;
    }
  }

  // common props to export for any item type
  private static ExportableInvRecProperty[] INV_REC_CORE_PROPS = {
    ExportableInvRecProperty.GLOBAL_ID,
    ExportableInvRecProperty.NAME,
    ExportableInvRecProperty.TAGS,
    ExportableInvRecProperty.OWNER,
    ExportableInvRecProperty.DESCRIPTION
  };

  public enum ExportedContentType {
    SAMPLES,
    SAMPLE_TEMPLATES,
    SUBSAMPLES,
    CONTAINERS,
    LIST_OF_MATERIALS
  }

  protected @Autowired CsvExportCommentGenerator exportCommentGenerator;

  protected OutputStream getCsvCommentFragment(
      String exportedContentString, ExportScope selection, CsvExportMode exportMode, User user)
      throws IOException {

    Map<ExportedCommentProperty, String> propertiesForCsvComment =
        exportCommentGenerator.getPropertiesForCsvCommentFragment(
            exportedContentString, selection, exportMode, user);

    OutputStream outputStream = new ByteArrayOutputStream();
    CsvMapper csvMapper = getCsvMapper();

    writeCsvCommentLine(csvMapper, outputStream, CSV_COMMENT_HEADER);
    for (Map.Entry<ExportedCommentProperty, String> commentEntry :
        propertiesForCsvComment.entrySet()) {
      writeCsvCommentLine(
          csvMapper,
          outputStream,
          commentEntry.getKey().getCsvPropertyName() + ": " + commentEntry.getValue());
    }
    return outputStream;
  }

  private void writeCsvCommentLine(
      CsvMapper csvtMapper, OutputStream outputStream, String commentText) throws IOException {
    writeCsvLine(csvtMapper, outputStream, List.of(CSV_COMMENT_PREFIX + " " + commentText));
  }

  protected List<String> getBasicItemPropertiesForExport(
      InventoryRecord item, CsvExportMode exportMode) {
    List<String> itemProperties = new ArrayList<>();
    for (ExportableInvRecProperty prop : INV_REC_CORE_PROPS) {
      String valueForProp = null;
      switch (prop) {
        case NAME:
          valueForProp = item.getName();
          break;
        case GLOBAL_ID:
          valueForProp = item.getGlobalIdentifier();
          break;
        case TAGS:
          valueForProp = item.getTags();
          break;
        case DESCRIPTION:
          valueForProp = item.getDescription();
          break;
        case OWNER:
          valueForProp = item.getOwner() != null ? item.getOwner().getUsername() : null;
          break;
      }
      itemProperties.add(valueForProp != null ? valueForProp : "");
    }
    return itemProperties;
  }

  protected void addVariableItemPropertiesForExport(
      InventoryRecord item,
      CsvExportMode exportMode,
      List<String> csvColumnNames,
      List<String> itemProperties) {

    int numberOfCsvColumns = csvColumnNames.size();
    populateItemPropertiesWithNotAvailableValuesForRemainingColumns(
        numberOfCsvColumns, itemProperties);

    if (CsvExportMode.FULL.equals(exportMode) && item.getActiveExtraFields() != null) {
      for (ExtraField ef : item.getActiveExtraFields()) {
        String valueForProp = ef.getData();
        int columnIndexForValue = csvColumnNames.indexOf(getColumnNameForExtraField(ef));
        itemProperties.set(columnIndexForValue, valueForProp != null ? valueForProp : "");
      }
    }
  }

  protected void populateItemPropertiesWithNotAvailableValuesForRemainingColumns(
      int numberOfCsvColumns, List<String> itemProperties) {
    int numberOfColumnsToAdd = numberOfCsvColumns - itemProperties.size();
    if (numberOfColumnsToAdd > 0) {
      itemProperties.addAll(
          Collections.nCopies(numberOfColumnsToAdd, CSV_VALUE_UNAVAILABLE_ITEM_PROPERTY));
    }
  }

  protected String convertListToCsvStringValue(List<?> items) {
    return StringUtils.join(items, CSV_VALUE_LIST_SEPARATOR);
  }

  protected List<String> getBasicColumnNamesForCsv(CsvExportMode exportMode) {
    List<String> columnNames = new ArrayList<>();
    for (ExportableInvRecProperty prop : INV_REC_CORE_PROPS) {
      columnNames.add(prop.getCsvColumnHeader());
    }
    return columnNames;
  }

  protected List<ExtraField> getExtraFieldsFromAllItems(List<? extends InventoryRecord> items) {
    return items.stream()
        .flatMap(item -> item.getActiveExtraFields().stream())
        .collect(Collectors.toList());
  }

  protected List<String> getExtraFieldColumnNamesForCsv(
      List<ExtraField> extraFields, CsvExportMode exportMode) {
    List<String> columnNames = new ArrayList<>();
    if (CsvExportMode.FULL.equals(exportMode) && extraFields != null) {
      for (ExtraField ef : extraFields) {
        String extraFieldColumName = getColumnNameForExtraField(ef);
        if (!columnNames.contains(extraFieldColumName)) {
          columnNames.add(extraFieldColumName);
        }
      }
    }
    return columnNames;
  }

  protected String getColumnNameForExtraField(ExtraField ef) {
    return String.format(
        "%s (%s, %s)", ef.getName(), ef.getType(), ef.getConnectedRecordGlobalIdentifier());
  }

  protected void writeCsvLine(
      CsvMapper mapper, OutputStream outputStream, List<String> csvLineValues) throws IOException {
    mapper
        .writerFor(String[].class)
        .writeValues(outputStream)
        .write(csvLineValues.toArray(new String[0]));
  }

  protected CsvMapper getCsvMapper() {
    CsvMapper mapper = new CsvMapper();
    mapper.enable(CsvParser.Feature.TRIM_SPACES);
    mapper.enable(CsvGenerator.Feature.STRICT_CHECK_FOR_QUOTING); // so comments are left unquoted
    return mapper;
  }
}

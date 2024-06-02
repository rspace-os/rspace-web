package com.researchspace.service.inventory.csvexport;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.field.SampleField;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Component;

@Component
public class CsvContainerExporter extends InventoryItemCsvExporter {

  // additional props to export for samples
  private static ExportableInvRecProperty[] CONTAINER_EXPORTABLE_PROPS = {
    ExportableInvRecProperty.CONTAINER_GLOBAL_ID,
    ExportableInvRecProperty.CONTAINER_TYPE,
    ExportableInvRecProperty.CAN_STORE_CONTAINERS,
    ExportableInvRecProperty.CAN_STORE_SUBSAMPLES,
    ExportableInvRecProperty.STORED_CONTAINERS_COUNT,
    ExportableInvRecProperty.STORED_SUBSAMPLES_COUNT
  };

  public List<String> writeContainerCsvHeaderIntoOutput(
      List<Container> containers,
      CsvExportMode exportMode,
      CsvMapper mapper,
      OutputStream outputStream)
      throws IOException {
    if (mapper == null) {
      mapper = getCsvMapper();
    }
    Validate.notNull(outputStream, "output stream cannot be null");

    List<String> columnNames = getContainerColumnNamesForCsv(containers, exportMode);
    writeCsvLine(mapper, outputStream, columnNames);

    return columnNames;
  }

  private List<String> getContainerColumnNamesForCsv(
      List<Container> containers, CsvExportMode exportMode) {
    List<String> columnNames = super.getBasicColumnNamesForCsv(exportMode);
    for (ExportableInvRecProperty prop : CONTAINER_EXPORTABLE_PROPS) {
      columnNames.add(prop.getCsvColumnHeader());
    }
    columnNames.addAll(
        getExtraFieldColumnNamesForCsv(getExtraFieldsFromAllItems(containers), exportMode));
    return columnNames;
  }

  protected String getColumnNameForContainerField(SampleField sf) {
    String connectedTemplateGlobalId = sf.getTemplateField().getSample().getGlobalIdentifier();
    return String.format("%s (%s, %s)", sf.getName(), sf.getType(), connectedTemplateGlobalId);
  }

  protected List<String> writeContainerCsvDetailsIntoOutput(
      Container container,
      List<String> csvColumnNames,
      CsvExportMode exportMode,
      CsvMapper mapper,
      OutputStream outputStream)
      throws IOException {

    if (mapper == null) {
      mapper = getCsvMapper();
    }
    Validate.notNull(outputStream, "output stream cannot be null");

    List<String> properties = getContainerPropertiesForCsv(container, csvColumnNames, exportMode);
    writeCsvLine(mapper, outputStream, properties);

    return properties;
  }

  private List<String> getContainerPropertiesForCsv(
      Container container, List<String> csvColumnNames, CsvExportMode exportMode) {
    List<String> itemProperties = getBasicItemPropertiesForExport(container, exportMode);
    addContainerSpecificPropertiesForExport(container, itemProperties);
    addVariableItemPropertiesForExport(container, exportMode, csvColumnNames, itemProperties);
    return itemProperties;
  }

  private void addContainerSpecificPropertiesForExport(
      Container container, List<String> itemProperties) {
    for (ExportableInvRecProperty prop : CONTAINER_EXPORTABLE_PROPS) {
      String valueForProp = null;
      switch (prop) {
        case CONTAINER_TYPE:
          valueForProp = getContainerTypeCsvValue(container);
          break;
        case CAN_STORE_CONTAINERS:
          valueForProp = container.isCanStoreContainers() ? "Y" : "N";
          break;
        case CAN_STORE_SUBSAMPLES:
          valueForProp = container.isCanStoreSamples() ? "Y" : "N";
          break;
        case STORED_CONTAINERS_COUNT:
          valueForProp = container.getContentCountContainers() + "";
          break;
        case STORED_SUBSAMPLES_COUNT:
          valueForProp = container.getContentCountSubSamples() + "";
          break;
        case CONTAINER_GLOBAL_ID:
          valueForProp =
              container.getParentContainer() != null
                  ? container.getParentContainer().getGlobalIdentifier()
                  : null;
          break;
        default:
          throw new IllegalStateException("unhandled property: " + prop);
      }
      itemProperties.add(valueForProp != null ? valueForProp : "");
    }
  }

  private String getContainerTypeCsvValue(Container container) {
    String result = container.getContainerType().toString();
    if (ContainerType.GRID.equals(container.getContainerType())) {
      result +=
          String.format(
              "(%dx%d)",
              container.getGridLayoutColumnsNumber(), container.getGridLayoutRowsNumber());
    }
    return result;
  }

  public OutputStream getCsvFragmentForContainers(
      List<Container> containers, CsvExportMode exportMode) throws IOException {
    CsvMapper mapper = getCsvMapper();
    OutputStream outputStream = new ByteArrayOutputStream();
    if (!containers.isEmpty()) {
      List<String> columnNames =
          writeContainerCsvHeaderIntoOutput(containers, exportMode, mapper, outputStream);
      for (Container c : containers) {
        writeContainerCsvDetailsIntoOutput(c, columnNames, exportMode, mapper, outputStream);
      }
    }
    return outputStream;
  }

  public OutputStream getCsvCommentFragmentForContainers(
      ExportScope selection, CsvExportMode exportMode, User user) throws IOException {
    return getCsvCommentFragment(
        ExportedContentType.CONTAINERS.toString(), selection, exportMode, user);
  }
}

package com.researchspace.service.inventory.csvexport;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.helper.Validate;
import org.springframework.stereotype.Component;

@Component
public class CsvListOfMaterialsExporter extends InventoryItemCsvExporter {

  // all props to export for list of materials
  private static ExportableInvRecProperty[] LOM_ALL_EXPORTABLE_PROPS = {
    ExportableInvRecProperty.LOM_GLOBAL_ID,
    ExportableInvRecProperty.LOM_NAME,
    ExportableInvRecProperty.MATERIAL_GLOBAL_ID,
    ExportableInvRecProperty.MATERIAL_NAME,
    ExportableInvRecProperty.MATERIAL_TYPE,
    ExportableInvRecProperty.USED_QUANTITY
  };

  public List<String> writeLomCsvHeaderIntoOutput(CsvMapper mapper, OutputStream outputStream)
      throws IOException {
    if (mapper == null) {
      mapper = getCsvMapper();
    }
    Validate.notNull(outputStream, "output stream cannot be null");

    List<String> columnNames = new ArrayList<>();
    for (ExportableInvRecProperty prop : LOM_ALL_EXPORTABLE_PROPS) {
      columnNames.add(prop.getCsvColumnHeader());
    }
    writeCsvLine(mapper, outputStream, columnNames);

    return columnNames;
  }

  protected List<String> writeMaterialUsageCsvDetailsIntoOutput(
      ListOfMaterials lom, MaterialUsage mu, CsvMapper mapper, OutputStream outputStream)
      throws IOException {

    if (mapper == null) {
      mapper = getCsvMapper();
    }
    Validate.notNull(outputStream, "output stream cannot be null");

    List<String> properties = new ArrayList<>();
    addMaterialUsagePropertiesForExport(lom, mu, properties);
    writeCsvLine(mapper, outputStream, properties);

    return properties;
  }

  private void addMaterialUsagePropertiesForExport(
      ListOfMaterials lom, MaterialUsage mu, List<String> itemProperties) {
    for (ExportableInvRecProperty prop : LOM_ALL_EXPORTABLE_PROPS) {
      String valueForProp = null;
      switch (prop) {
        case LOM_GLOBAL_ID:
          valueForProp = lom.getOid().toString();
          break;
        case LOM_NAME:
          valueForProp = lom.getName();
          break;
        case MATERIAL_GLOBAL_ID:
          valueForProp = mu.getInventoryRecord().getGlobalIdentifier();
          break;
        case USED_QUANTITY:
          valueForProp = mu.getUsedQuantityPlainString();
          break;
        case MATERIAL_TYPE:
          valueForProp = mu.getInventoryRecord().getType().toString();
          break;
        case MATERIAL_NAME:
          valueForProp = mu.getInventoryRecord().getName();
          break;
        default:
          throw new IllegalStateException("unhandled property: " + prop);
      }
      itemProperties.add(valueForProp != null ? valueForProp : "");
    }
  }

  public OutputStream getCsvFragmentForListsOfMaterials(List<ListOfMaterials> loms)
      throws IOException {
    CsvMapper mapper = getCsvMapper();
    OutputStream outputStream = new ByteArrayOutputStream();
    if (!loms.isEmpty()) {
      writeLomCsvHeaderIntoOutput(mapper, outputStream);
      for (ListOfMaterials lom : loms) {
        for (MaterialUsage mu : lom.getMaterials()) {
          writeMaterialUsageCsvDetailsIntoOutput(lom, mu, mapper, outputStream);
        }
      }
    }
    return outputStream;
  }

  public OutputStream getCsvCommentFragmentForLom(
      ExportScope selection, CsvExportMode exportMode, User user) throws IOException {
    return getCsvCommentFragment(
        ExportedContentType.LIST_OF_MATERIALS.toString(), selection, exportMode, user);
  }
}

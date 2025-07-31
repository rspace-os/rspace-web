package com.researchspace.service.fieldmark.impl;

import static com.researchspace.api.v1.model.ApiField.ApiFieldType.ATTACHMENT;
import static com.researchspace.api.v1.model.ApiField.ApiFieldType.DATE;
import static com.researchspace.api.v1.model.ApiField.ApiFieldType.NUMBER;
import static com.researchspace.api.v1.model.ApiField.ApiFieldType.RADIO;
import static com.researchspace.api.v1.model.ApiField.ApiFieldType.TEXT;
import static com.researchspace.api.v1.model.ApiField.ApiFieldType.TIME;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleField.ApiInventoryFieldDef;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples.ApiSampleSubSampleTargetLocation;
import com.researchspace.fieldmark.model.FieldmarkNotebookMetadata;
import com.researchspace.fieldmark.model.utils.FieldmarkDateExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkDatetimeExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkLocationExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkTypeExtractor;
import com.researchspace.model.User;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import com.researchspace.model.dtos.fieldmark.FieldmarkRecordDTO;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public class FieldmarkToRSpaceApiConverter {

  private static final String TRUE = "True";
  private static final String FALSE = "False";

  private FieldmarkToRSpaceApiConverter() {}

  public static ApiSampleTemplatePost createSampleTemplateRequest(
      FieldmarkNotebookDTO notebookDTO) {
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName(
        "Sample Template " + notebookDTO.getName() + " - " + notebookDTO.getTimestamp());

    Optional<FieldmarkRecordDTO> entryRecordDTO =
        notebookDTO.getRecords().values().stream().findFirst();
    if (entryRecordDTO.isPresent()) {
      FieldmarkRecordDTO currentRecordDTO = entryRecordDTO.get();
      int columnIndex = 1;
      for (Entry<String, FieldmarkTypeExtractor> fieldDTO :
          currentRecordDTO.getFields().entrySet()) {
        if (!fieldDTO.getValue().isDoiIdentifier()) {
          columnIndex =
              createSampleTemplateFieldFromDTO(
                  fieldDTO, columnIndex, sampleTemplatePost.getFields());
        }
      }
    }
    sampleTemplatePost.setDefaultUnitId(RSUnitDef.DIMENSIONLESS.getId());
    return sampleTemplatePost;
  }

  private static int createSampleTemplateFieldFromDTO(
      Entry<String, FieldmarkTypeExtractor> fieldDTO,
      int columnIndex,
      List<ApiSampleField> fieldsPost) {
    switch (fieldDTO.getValue().getSimpleTypeName()) {
      case "String":
        columnIndex = addFieldToSampleTemplate(fieldDTO.getKey(), TEXT, columnIndex, fieldsPost);
        break;
      case "Integer":
        columnIndex = addFieldToSampleTemplate(fieldDTO.getKey(), NUMBER, columnIndex, fieldsPost);
        break;
      case "byte[]":
        columnIndex =
            addFieldToSampleTemplate(fieldDTO.getKey(), ATTACHMENT, columnIndex, fieldsPost);
        break;
      case "FieldmarkLocation":
        columnIndex =
            addFieldToSampleTemplate(
                fieldDTO.getKey() + "_latitude", NUMBER, columnIndex, fieldsPost);
        columnIndex =
            addFieldToSampleTemplate(
                fieldDTO.getKey() + "_longitude", NUMBER, columnIndex, fieldsPost);
        break;
      case "Boolean":
        columnIndex = addFieldToSampleTemplate(fieldDTO.getKey(), RADIO, columnIndex, fieldsPost);
        break;
      case "Date":
        columnIndex = addFieldToSampleTemplate(fieldDTO.getKey(), DATE, columnIndex, fieldsPost);
        break;
      case "Datetime":
        columnIndex =
            addFieldToSampleTemplate(fieldDTO.getKey() + "_date", DATE, columnIndex, fieldsPost);
        columnIndex =
            addFieldToSampleTemplate(fieldDTO.getKey() + "_time", TIME, columnIndex, fieldsPost);
        break;
      default:
        throw new UnsupportedOperationException(
            "The type \""
                + fieldDTO.getValue().getFieldType().getSimpleName()
                + "\" is not yet supported while importing from Fieldmark");
    }
    return columnIndex;
  }

  private static int addFieldToSampleTemplate(
      String name,
      ApiFieldType type,
      int columnIndex,
      List<ApiSampleField> sampleTemplateFieldList) {
    ApiSampleField fieldRSpace = new ApiSampleField();
    fieldRSpace.setName(name);
    fieldRSpace.setType(type);
    fieldRSpace.setColumnIndex(columnIndex++);
    if (RADIO.equals(type)) {
      fieldRSpace.setDefinition(new ApiInventoryFieldDef(List.of(TRUE, FALSE), false));
    }
    sampleTemplateFieldList.add(fieldRSpace);
    return columnIndex;
  }

  public static ApiContainer createContainerRequest(FieldmarkNotebookDTO notebookDTO, User owner) {
    Container baseContainer = new Container(ContainerType.LIST);
    baseContainer.setOwner(owner);

    FieldmarkNotebookMetadata metadataDTO = notebookDTO.getMetadata();

    ApiContainer apiContainer = new ApiContainer(baseContainer);
    apiContainer.setName("Container " + metadataDTO.getName() + " - " + notebookDTO.getTimestamp());
    addExtraFieldsToContainer(metadataDTO, apiContainer);

    return apiContainer;
  }

  private static void addExtraFieldsToContainer(
      FieldmarkNotebookMetadata metadataDTO, ApiContainer apiContainer) {
    addExtraFieldToContainer("item name", metadataDTO.getName(), apiContainer);
    addExtraFieldToContainer("projectId", metadataDTO.getProjectId(), apiContainer);
    addExtraFieldToContainer("leadInstitution", metadataDTO.getLeadInstitution(), apiContainer);
    addExtraFieldToContainer("projectLead", metadataDTO.getProjectLead(), apiContainer);
    addExtraFieldToContainer("projectStatus", metadataDTO.getProjectStatus(), apiContainer);
    addExtraFieldToContainer("age", metadataDTO.getAge(), apiContainer);
    addExtraFieldToContainer("size", metadataDTO.getSize(), apiContainer);
    addExtraFieldToContainer("preDescription", metadataDTO.getPreDescription(), apiContainer);
    addExtraFieldToContainer("isPublic", metadataDTO.getIsPublic().toString(), apiContainer);
    addExtraFieldToContainer("isRequest", metadataDTO.getIsRequest().toString(), apiContainer);
    addExtraFieldToContainer("showQRCodeButton", metadataDTO.getShowQRCodeButton(), apiContainer);
    addExtraFieldToContainer("notebookVersion", metadataDTO.getNotebookVersion(), apiContainer);
    addExtraFieldToContainer("schemaVersion", metadataDTO.getSchemaVersion(), apiContainer);
  }

  private static void addExtraFieldToContainer(
      String name, String content, ApiContainer apiContainer) {
    ApiExtraField field = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    field.setName(name);
    field.setContent(content);
    apiContainer.getExtraFields().add(field);
  }

  public static ApiSampleWithFullSubSamples createSampleRequest(
      FieldmarkRecordDTO recordDTO, ApiSampleTemplate sampleTemplate, Long containerId) {
    ApiSampleWithFullSubSamples samplePost =
        new ApiSampleWithFullSubSamples(
            recordDTO.getIdentifier() + " - " + recordDTO.getTimestamp());

    Map<String, Long> idsByFieldName = new HashMap<>();
    for (ApiSampleField field : sampleTemplate.getFields()) {
      idsByFieldName.put(field.getName(), field.getId());
    }

    List<ApiSampleField> currentFieldList = new LinkedList<>();
    int columnIndex = 1;
    for (Entry<String, FieldmarkTypeExtractor> currentFieldDTO : recordDTO.getFields().entrySet()) {
      if (!currentFieldDTO.getValue().isDoiIdentifier()) {
        columnIndex =
            createSampleFieldFromDTO(
                currentFieldDTO, currentFieldList, idsByFieldName, columnIndex);
      }
    }
    samplePost.setFields(currentFieldList);

    // set template and container
    samplePost.setNewSampleSubSamplesCount(1);
    samplePost.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(1), RSUnitDef.DIMENSIONLESS));
    samplePost.setNewSampleSubSampleTargetLocations(
        List.of(new ApiSampleSubSampleTargetLocation(containerId, null)));
    samplePost.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(-15), RSUnitDef.CELSIUS));
    samplePost.setStorageTempMax(new ApiQuantityInfo(BigDecimal.valueOf(30), RSUnitDef.CELSIUS));

    samplePost.setTemplate(false);
    samplePost.setTemplateId(sampleTemplate.getId());

    return samplePost;
  }

  private static int createSampleFieldFromDTO(
      Entry<String, FieldmarkTypeExtractor> currentFieldDTO,
      List<ApiSampleField> currentFieldList,
      Map<String, Long> idsByFieldName,
      int columnIndex) {
    switch (currentFieldDTO.getValue().getSimpleTypeName()) {
      case "String":
        columnIndex =
            addFieldToSample(
                currentFieldDTO.getKey(),
                idsByFieldName.get(currentFieldDTO.getKey()),
                currentFieldDTO.getValue().extractStringFieldValue(),
                TEXT,
                columnIndex,
                currentFieldList);
        break;
      case "Integer":
        columnIndex =
            addFieldToSample(
                currentFieldDTO.getKey(),
                idsByFieldName.get(currentFieldDTO.getKey()),
                currentFieldDTO.getValue().extractStringFieldValue(),
                NUMBER,
                columnIndex,
                currentFieldList);
        break;
      case "byte[]":
        columnIndex =
            addFieldToSample(
                currentFieldDTO.getKey(),
                idsByFieldName.get(currentFieldDTO.getKey()),
                null,
                ATTACHMENT,
                columnIndex,
                currentFieldList);
        break;
      case "FieldmarkLocation":
        FieldmarkLocationExtractor locationTypeExtractor =
            (FieldmarkLocationExtractor) currentFieldDTO.getValue();

        String locationName = currentFieldDTO.getKey() + "_latitude";
        columnIndex =
            addFieldToSample(
                locationName,
                idsByFieldName.get(locationName),
                locationTypeExtractor.getLatitudeStringValue(),
                NUMBER,
                columnIndex,
                currentFieldList);

        locationName = currentFieldDTO.getKey() + "_longitude";
        columnIndex =
            addFieldToSample(
                locationName,
                idsByFieldName.get(locationName),
                locationTypeExtractor.getLongitudeStringValue(),
                NUMBER,
                columnIndex,
                currentFieldList);
        break;
      case "Boolean":
        columnIndex =
            addFieldToSample(
                currentFieldDTO.getKey(),
                idsByFieldName.get(currentFieldDTO.getKey()),
                currentFieldDTO.getValue().extractStringFieldValue(),
                RADIO,
                columnIndex,
                currentFieldList);
        break;
      case "Date":
        FieldmarkDateExtractor dateTypeExtractor =
            (FieldmarkDateExtractor) currentFieldDTO.getValue();

        columnIndex =
            addFieldToSample(
                currentFieldDTO.getKey(),
                idsByFieldName.get(currentFieldDTO.getKey()),
                dateTypeExtractor.extractStringFieldValue(),
                DATE,
                columnIndex,
                currentFieldList);
        break;
      case "Datetime":
        FieldmarkDatetimeExtractor datetimeTypeExtractor =
            (FieldmarkDatetimeExtractor) currentFieldDTO.getValue();

        String datetimeName = currentFieldDTO.getKey() + "_date";
        columnIndex =
            addFieldToSample(
                datetimeName,
                idsByFieldName.get(datetimeName),
                datetimeTypeExtractor.extractDateStringValue(),
                DATE,
                columnIndex,
                currentFieldList);

        datetimeName = currentFieldDTO.getKey() + "_time";
        columnIndex =
            addFieldToSample(
                datetimeName,
                idsByFieldName.get(datetimeName),
                datetimeTypeExtractor.extractTimeStringValue(),
                TIME,
                columnIndex,
                currentFieldList);
        break;
      default:
        throw new UnsupportedOperationException(
            "The type \""
                + currentFieldDTO.getValue().getFieldType().getSimpleName()
                + "\" is not yet supported while importing from Fieldmark");
    }
    return columnIndex;
  }

  private static int addFieldToSample(
      String name,
      Long globalId,
      String content,
      ApiFieldType type,
      int columnIndex,
      List<ApiSampleField> sampleTemplateFieldList) {
    ApiSampleField fieldRSpace = new ApiSampleField();
    fieldRSpace.setDeleteFieldRequest(false);
    fieldRSpace.setDeleteFieldOnSampleUpdate(false);
    fieldRSpace.setMandatory(false);
    fieldRSpace.setColumnIndex(columnIndex++);
    fieldRSpace.setName(name);
    fieldRSpace.setId(globalId);
    fieldRSpace.setType(type);

    if (RADIO.equals(type)) {
      fieldRSpace.setSelectedOptions(List.of(content));
      fieldRSpace.setDefinition(new ApiInventoryFieldDef(List.of(TRUE, FALSE), false));
    } else {
      fieldRSpace.setContent(content);
    }
    sampleTemplateFieldList.add(fieldRSpace);
    return columnIndex;
  }
}

package com.researchspace.service.fieldmark.impl;

import static com.researchspace.service.IntegrationsHandler.FIELDMARK_APP_NAME;

import com.researchspace.fieldmark.client.FieldmarkClient;
import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.fieldmark.model.FieldmarkRecord;
import com.researchspace.fieldmark.model.FieldmarkRecordsCsvExport;
import com.researchspace.fieldmark.model.FieldmarkRecordsJsonExport;
import com.researchspace.fieldmark.model.utils.FieldmarkFileExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkTypeExtractor;
import com.researchspace.model.User;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import com.researchspace.model.dtos.fieldmark.FieldmarkRecordDTO;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.fieldmark.FieldmarkServiceClientAdapter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

@Slf4j
@Service
public class FieldmarkServiceClientAdapterImpl implements FieldmarkServiceClientAdapter {

  private static final String CSV_RECORD_IDENTIFIER = "identifier";
  @Autowired private FieldmarkClient fieldmarkClient;
  @Autowired private UserConnectionManager userConnectionManager;

  public static Set<String> RESERVED_FIELD_NAMES =
      Set.of("name", "description", "tags", "source", "expiry date");

  @Override
  public List<FieldmarkNotebook> getFieldmarkNotebookList(User user)
      throws MalformedURLException, URISyntaxException, HttpServerErrorException {
    UserConnection existingConnection = getExistingConnection(user);

    return fieldmarkClient.getNotebooks(existingConnection.getAccessToken());
  }

  @Override
  public FieldmarkNotebookDTO getFieldmarkNotebook(User user, String notebookId)
      throws IOException, HttpServerErrorException {
    UserConnection existingConnection = getExistingConnection(user);

    FieldmarkNotebook fieldmarkNotebook =
        fieldmarkClient.getNotebook(existingConnection.getAccessToken(), notebookId);

    FieldmarkRecordsJsonExport jsonRecords =
        fieldmarkClient.getNotebookRecords(existingConnection.getAccessToken(), notebookId);

    // Create DTO
    FieldmarkNotebookDTO notebookDTO =
        new FieldmarkNotebookDTO(
            fieldmarkNotebook.getMetadata().getProjectId(),
            fieldmarkNotebook.getMetadata().getName());
    notebookDTO.setMetadata(fieldmarkNotebook.getMetadata());

    // Fetch files if existing
    Map<String, byte[]> filesInRecords = null;
    String formId = jsonRecords.getFormId();
    if (jsonRecords.hasFiles()) {
      filesInRecords =
          fieldmarkClient.getNotebookFiles(existingConnection.getAccessToken(), notebookId, formId);
    }
    FieldmarkRecordsCsvExport csvRecords =
        fieldmarkClient.getNotebookCsv(existingConnection.getAccessToken(), notebookId, formId);

    // for each record found then copy the fields into the DTO
    for (Map.Entry<String, FieldmarkRecord> currentRecord :
        jsonRecords.getRecordsById().entrySet()) {
      FieldmarkRecordDTO currentRecordDTO = new FieldmarkRecordDTO(notebookDTO.getTimestamp());
      currentRecordDTO.setRecordId(currentRecord.getKey());
      currentRecordDTO.setIdentifier(
          csvRecords.getStringFieldValue(currentRecord.getKey(), CSV_RECORD_IDENTIFIER));
      // for each field
      for (Map.Entry<String, Object> currentField :
          currentRecord.getValue().getFieldList().entrySet()) {
        String fieldName = currentField.getKey();
        if (RESERVED_FIELD_NAMES.contains(fieldName.toLowerCase(Locale.getDefault()))) {
          fieldName = "Item-" + fieldName;
        }
        copyToDTOFields(
            currentRecord, currentField, fieldName, csvRecords, currentRecordDTO, filesInRecords);
      }
      notebookDTO.addRecord(currentRecordDTO);
    }

    return notebookDTO;
  }

  private UserConnection getExistingConnection(User user) {
    return userConnectionManager
        .findByUserNameProviderName(user.getUsername(), FIELDMARK_APP_NAME)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No UserConnection exists for: " + FIELDMARK_APP_NAME));
  }

  private static void copyToDTOFields(
      Entry<String, FieldmarkRecord> recordEntry,
      Entry<String, Object> fieldEntry,
      String fieldName,
      FieldmarkRecordsCsvExport csvRecords,
      FieldmarkRecordDTO currentRecordDTO,
      Map<String, byte[]> filesInRecords) {
    try {
      FieldmarkTypeExtractor typeExtractor =
          recordEntry.getValue().createFieldTypeExtractor(fieldEntry.getKey());
      // for each field FILE the fill the gap
      if (typeExtractor != null && byte[].class.equals(typeExtractor.getFieldType())) {
        // grab the path from CSV
        String filePath = csvRecords.getStringFieldValue(currentRecordDTO.getRecordId(), fieldName);
        if (StringUtils.isNotBlank(filePath)) {
          // grab the file from the ZIP and attach it to the extractor
          typeExtractor.setFieldValue(filesInRecords.get(filePath));
          ((FieldmarkFileExtractor) typeExtractor).setFileName(filePath);
        }
      }
      currentRecordDTO.addField(fieldName, typeExtractor);
    } catch (NoSuchElementException ex2) {
      log.warn("Not able to extract the type of the field \"" + fieldEntry.getKey() + "\"");
    }
  }
}

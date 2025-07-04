package com.researchspace.service.fieldmark;

import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.model.User;
import com.researchspace.webapp.integrations.fieldmark.FieldmarkApiImportRequest;
import com.researchspace.webapp.integrations.fieldmark.FieldmarkApiImportResult;
import java.util.List;

public interface FieldmarkServiceManager {

  FieldmarkApiImportResult importNotebook(FieldmarkApiImportRequest importRequest, User user);

  List<FieldmarkNotebook> getFieldmarkNotebookList(User user);

  List<String> getIgsnCandidateFields(User user, String notebookId);
}

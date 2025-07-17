package com.researchspace.service.fieldmark;

import com.researchspace.model.User;
import com.researchspace.webapp.integrations.fieldmark.FieldmarkApiImportRequest;
import com.researchspace.webapp.integrations.fieldmark.FieldmarkApiImportResult;

public interface FieldmarkServiceManager {

  FieldmarkApiImportResult importNotebook(FieldmarkApiImportRequest importRequest, User user);
}

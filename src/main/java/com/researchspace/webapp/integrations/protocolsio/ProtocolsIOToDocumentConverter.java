package com.researchspace.webapp.integrations.protocolsio;

import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.protocolsio.Protocol;

public interface ProtocolsIOToDocumentConverter {

  StructuredDocument generateFromProtocol(Protocol toImport, User subject, Long parentFolderId);
}

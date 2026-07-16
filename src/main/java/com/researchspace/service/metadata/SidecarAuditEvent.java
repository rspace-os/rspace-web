package com.researchspace.service.metadata;

import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailProperty;

/** Audit-trail payload for a sidecar generation: the filestore, folder and resulting filename. */
@AuditTrailData(auditDomain = AuditDomain.MEDIA)
public record SidecarAuditEvent(
    @AuditTrailProperty(name = "filestore") String filestore,
    @AuditTrailProperty(name = "path") String path,
    @AuditTrailProperty(name = "filename") String filename) {}

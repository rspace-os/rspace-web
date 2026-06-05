package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;

/** For handling revision history requests around RS Inventory items */
public interface InventoryAuditApiManager {

  ApiInventoryRecordRevisionList getInventoryRecordRevisions(InventoryRecord currentInvRec);

  ApiSample getApiSampleRevision(Long sampleId, Long revisionId);

  ApiSubSample getApiSubSampleRevision(Long subSampleId, Long revisionId);

  ApiSampleTemplate getApiTemplateVersion(Sample latestTemplate, Long version);

  ApiInstrument getApiInstrumentRevision(Long instrumentId, Long revisionId);

  ApiInstrumentTemplate getApiInstrumentTemplateRevision(Long templateId, Long revisionId);

  ApiInstrumentTemplate getApiInstrumentTemplateVersion(
      InstrumentTemplate latestTemplate, Long version);
}

package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;

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

  /**
   * Returns the sample as it was at the given user-facing version. The current version is served
   * from the live entity; older versions resolve to the newest audit revision carrying that
   * version, flagged as historical. Returns null if the version does not exist.
   */
  ApiSample getApiSampleVersion(Sample currentSample, Long version);

  /** As {@link #getApiSampleVersion(Sample, Long)}, for a subsample. */
  ApiSubSample getApiSubSampleVersion(SubSample currentSubSample, Long version);

  /**
   * As {@link #getApiSampleVersion(Sample, Long)}, for a container. Historical container snapshots
   * exclude content: locations are not audited, so a snapshot could only show present-day contents.
   */
  ApiContainer getApiContainerVersion(Container currentContainer, Long version);

  /** As {@link #getApiSampleVersion(Sample, Long)}, for an instrument. */
  ApiInstrument getApiInstrumentVersion(Instrument currentInstrument, Long version);

  /**
   * Returns the container snapshot at the given audit revision, without content (locations are not
   * audited). Returns null if the revision does not exist.
   */
  ApiContainer getApiContainerRevision(Long containerId, Long revisionId);
}

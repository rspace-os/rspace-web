package com.researchspace.service.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.dao.SampleDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Barcode;
import com.researchspace.model.inventory.Sample;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Inventory list collections load lazily in batches, but callers such as {@code
 * SampleTemplatesApiController.setIcon} read the active barcodes, identifiers and attachments after
 * the session has closed. They must be built before the loading transaction commits.
 */
class InventoryListActiveListsAfterCommitIT extends RealTransactionSpringTestBase {

  private static final int RECORD_COUNT = 3;

  private @Autowired SampleDao sampleDao;

  @Test
  void sampleListPageActiveListsReadableAfterCommit() throws Exception {
    User user = createInitAndLoginAnyUser();
    for (int i = 0; i < RECORD_COUNT; i++) {
      ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples("s" + i);
      sample.getBarcodes().add(new ApiBarcode("sample-barcode-" + i));
      sampleApiMgr.createNewApiSample(sample, user);
    }
    PaginationCriteria<Sample> pgCrit = PaginationCriteria.createDefaultForClass(Sample.class);

    List<Sample> samples =
        doInTransaction(
            () ->
                sampleDao
                    .getSamplesForUser(
                        pgCrit,
                        null,
                        user.getUsername(),
                        InventorySearchDeletedOption.EXCLUDE,
                        user)
                    .getResults());

    assertEquals(RECORD_COUNT, samples.size());
    assertThat(samples)
        .allSatisfy(
            sample -> {
              assertThat(sample.getActiveBarcodes())
                  .extracting(Barcode::getBarcodeData)
                  .singleElement()
                  .asString()
                  .startsWith("sample-barcode-");
              assertThat(sample.getActiveIdentifiers()).isEmpty();
              assertThat(sample.getAttachedFiles()).isEmpty();
            });
  }
}

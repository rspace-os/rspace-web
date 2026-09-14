package com.researchspace.model.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SubSampleTest {
  private Sample sample;
  private SubSample subSample;
  private User anyUser;

  @BeforeEach
  void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
    sample = TestFactory.createSampleWithSubSamplesAndEverything(anyUser, 1);

    sample.setId(5L);
    subSample = sample.getActiveSubSamples().get(0);
  }

  @Test
  void assertQuantityCantBeNegative() throws Exception {
    QuantityInfo positiveQuantity = QuantityInfo.of(BigDecimal.valueOf(0), RSUnitDef.GRAM);
    subSample.setQuantity(positiveQuantity);
    QuantityInfo negativeQuantity = QuantityInfo.of(BigDecimal.valueOf(-0.001), RSUnitDef.GRAM);
    IllegalArgumentException iae =
        assertThrows(IllegalArgumentException.class, () -> subSample.setQuantity(negativeQuantity));
    assertEquals("Trying to set negative record quantity: -0.001", iae.getMessage());
  }

  @Test
  void adoptingACommittedQuantityLeavesTheParentTotalAlone() {
    // The whole reason this exists rather than setQuantity. The value was committed by another
    // transaction, which recomputed the parent total from the locked sibling rows before it
    // committed; recomputing here would sum THIS transaction's stale sibling snapshots over the top
    // of that, and would dirty the sample so the stale total got written at all.
    QuantityInfo totalBefore = sample.getTotalQuantity();

    subSample.refreshQuantityFromLockedRow(QuantityInfo.of(BigDecimal.valueOf(3), RSUnitDef.GRAM));

    assertEquals(0, BigDecimal.valueOf(3).compareTo(subSample.getQuantity().getNumericValue()));
    assertEquals(
        totalBefore, sample.getTotalQuantity(), "the parent total must not be recomputed here");
  }

  @Test
  void aRowWithNoStoredQuantityLeavesTheEntityAsItIs() {
    // getQuantityForUpdate returns null for a row whose quantityNumericValue column is NULL. That
    // is the ONLY thing null can mean on this path: the "no such row" case cannot reach here
    // because reconcileWithCommittedRow calls lockSubSampleForEdit first, which 404s on a missing
    // row before any scalar is read.
    //
    // Such a row is not reachable through the application - setQuantityInfo dereferences the
    // numeric value whenever a unit is present, and quantityUnitId is NOT NULL in the schema - so
    // this covers legacy or externally written data only. Adopting the NULL would leave the entity
    // with no quantity at all and NPE the rest of the request, which is worse than declining to
    // reconcile a column the application cannot produce (review 2026-09-14, I3).
    QuantityInfo held = subSample.getQuantity();

    subSample.refreshQuantityFromLockedRow(null);

    assertEquals(held, subSample.getQuantity());
  }

  @Test
  void shallowCopy() throws IllegalArgumentException, IllegalAccessException, IOException {

    SubSample copy = subSample.shallowCopy();
    Set<String> toIgnore =
        TransformerUtils.toSet(
            "id",
            "sample",
            "activeExtraFields",
            "extraFields",
            "activeBarcodes",
            "barcodes",
            "attachedFiles",
            "files",
            "editInfo",
            "notes",
            "thumbnailFileProperty",
            "imageFileProperty");
    ModelTestUtils.assertCopiedFieldsAreEqual(
        copy, subSample, toIgnore, TransformerUtils.toList(SubSample.class, InventoryRecord.class));
    assertNull(copy.getGlobalIdentifier());
    assertNull(copy.getImageFileProperty());
  }

  @Test
  void copy() throws IllegalArgumentException, IllegalAccessException, IOException {
    SubSample copy = subSample.copy(anyUser);

    Set<String> toIgnore =
        TransformerUtils.toSet(
            "id",
            "activeExtraFields",
            "sample",
            "notes",
            "extraFields",
            "activeBarcodes",
            "barcodes",
            "attachedFiles",
            "files",
            "editInfo");
    ModelTestUtils.assertCopiedFieldsAreEqual(
        copy, subSample, toIgnore, TransformerUtils.toList(SubSample.class, InventoryRecord.class));
    assertNull(copy.getGlobalIdentifier());
    assertNotNull(copy.getImageFileProperty());
    assertNotNull(copy.getThumbnailFileProperty());
    assertEquals(subSample.getImageFileProperty(), copy.getImageFileProperty());
    assertEquals(subSample.getThumbnailFileProperty(), copy.getThumbnailFileProperty());
    assertEquals(sample, copy.getSample());
    assertThat(copy.getActiveExtraFields()).hasSize(2);
    assertThat(copy.getNotes()).hasSize(2);
    assertEquals(subSample.getName() + "_COPY", copy.getName());
  }
}

package com.researchspace.service.inventory;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.api.v1.model.ApiSubSampleSearchResult;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.service.inventory.impl.SubSampleDuplicateConfig;
import java.util.List;

/** Handles API actions around Inventory SubSample. */
public interface SubSampleApiManager extends InventoryApiManager {

  /**
   * Get not-deleted subsamples that user can see. Optionally limit to samples belonging to a
   * particular owner.
   */
  ApiSubSampleSearchResult getSubSamplesForUser(
      PaginationCriteria<SubSample> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user);

  /** Checks if subSample with given id exists */
  boolean exists(long id);

  SubSample assertUserCanReadSubSample(Long id, User user);

  SubSample assertUserCanEditSubSample(Long id, User user);

  SubSample assertUserCanDeleteSubSample(Long id, User user);

  /**
   * @return full populated subSample
   */
  ApiSubSample getApiSubSampleById(Long id, User user);

  /**
   * @return updated subSample
   */
  ApiSubSample updateApiSubSample(ApiSubSample incomingSubSample, User user);

  /**
   * @param sampleId id of a parent sample
   * @param subSamplesCount number of subsamples to create
   * @param subSampleQuantity quantity of an individual subsample
   * @return list of newly created subsamples
   */
  List<ApiSubSample> createNewSubSamplesForSample(
      Long sampleId, Integer subSamplesCount, ApiQuantityInfo subSampleQuantity, User user);

  /**
   * @param incomingSubSample details of subsample to create
   * @param sampleId id of a parent sample
   * @return newly created subSample
   */
  ApiSubSample addNewApiSubSampleToSample(ApiSubSample incomingSubSample, Long sampleId, User user);

  ApiSubSample registerApiSubSampleUsage(
      ApiSubSample usedSubSample, QuantityInfo usedQuantity, User user);

  ApiSubSample addSubSampleNote(Long subSampleId, ApiSubSampleNote subSampleNote, User user);

  /**
   * Marks subsample as deleted and removes it from current location.
   *
   * <p>Refeshes active subsamples of a sample, unless called as a part of sample deletion in which
   * case the calling code should refresh them at the end of deletion process.
   *
   * @param id
   * @param user
   * @param partOfSampleDeletion
   * @return deleted subSample
   */
  ApiSubSample markSubSampleAsDeleted(Long id, User user, boolean partOfSampleDeletion);

  /**
   * Un-deletes the subsample and moves it to current user's bench. Also un-deletes the parent
   * sample, if deleted.
   *
   * <p>Refeshes active subsamples of a sample, unless called as a part of sample restore in which
   * case the calling code should refresh them at the end of restore process.
   *
   * @param id
   * @param user
   * @param partOfSampleRestore
   * @return restored subSample
   */
  ApiSubSample restoreDeletedSubSample(Long id, User user, boolean partOfSampleRestore);

  /**
   * Makes full copy of subsample including notes and extra fields.
   *
   * @param subSampleId
   * @param user
   * @return the copied subsample
   */
  ApiSubSample duplicate(Long subSampleId, User user);

  /**
   * Splits the given subsample into n portions, including the original. Quantities are equalised.
   *
   * <p>E.g splitting a subsample of 1 litre into 4 will produce 3 new copies; the volumes of each
   * copy and the orginal will be 250ml each, maintaining conservation of quantity.
   *
   * @param config
   * @param user
   * @return A <code> List<ApiSubSampleFull> </code> of the new copies
   */
  List<ApiSubSample> split(SubSampleDuplicateConfig config, User user);
}

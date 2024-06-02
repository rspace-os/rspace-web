package com.researchspace.service.inventory.impl;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfoWithSampleInfo;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.api.v1.model.ApiSubSampleSearchResult;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.events.InventoryDeleteEvent;
import com.researchspace.model.events.InventoryEditingEvent;
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.events.InventoryRestoreEvent;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.record.IActiveUserStrategy;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.service.inventory.InventoryMoveHelper;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service("subSampleApiManager")
public class SubSampleApiManagerImpl extends InventoryApiManagerImpl
    implements SubSampleApiManager {

  private @Autowired SubSampleDao subSampleDao;
  private @Autowired InventoryMoveHelper moveHelper;

  private @Autowired @Lazy SampleApiManager sampleApiMgr;

  private QuantityUtils qUtils = new QuantityUtils();

  @Override
  public ApiSubSampleSearchResult getSubSamplesForUser(
      PaginationCriteria<SubSample> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user) {

    ISearchResults<SubSample> dbSubSamples =
        subSampleDao.getSubSamplesForUser(pgCrit, ownedBy, deletedOption, user);
    List<ApiSubSampleInfoWithSampleInfo> subSampleInfos = new ArrayList<>();
    for (SubSample subSample : dbSubSamples.getResults()) {
      ApiSubSampleInfoWithSampleInfo apiInvRec = new ApiSubSampleInfoWithSampleInfo(subSample);
      setOtherFieldsForOutgoingApiInventoryRecord(apiInvRec, subSample, user);
      subSampleInfos.add(apiInvRec);
    }

    ApiSubSampleSearchResult apiSearchResult = new ApiSubSampleSearchResult();
    apiSearchResult.setTotalHits(dbSubSamples.getTotalHits());
    apiSearchResult.setPageNumber(dbSubSamples.getPageNumber());
    apiSearchResult.setItems(subSampleInfos);

    return apiSearchResult;
  }

  @Override
  public boolean exists(long id) {
    return subSampleDao.exists(id);
  }

  @Override
  public SubSample assertUserCanReadSubSample(Long id, User user) {
    SubSample subSample = getIfExists(id);
    invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(subSample, user);
    return subSample;
  }

  @Override
  public SubSample assertUserCanEditSubSample(Long id, User user) {
    SubSample subSample = getIfExists(id);
    invPermissions.assertUserCanEditInventoryRecord(subSample, user);
    return subSample;
  }

  @Override
  public SubSample assertUserCanDeleteSubSample(Long id, User user) {
    SubSample subSample = getIfExists(id);
    invPermissions.assertUserCanDeleteInventoryRecord(subSample, user);
    return subSample;
  }

  @Override
  public ApiSubSample getApiSubSampleById(Long id, User user) {
    SubSample subSample = getIfExists(id);
    publisher.publishEvent(new InventoryAccessEvent(subSample, user));
    return getPopulatedApiSubSampleFull(subSample, user);
  }

  private ApiSubSample getPopulatedApiSubSampleFull(SubSample subSample, User user) {
    ApiSubSample result = new ApiSubSample(subSample);
    setOtherFieldsForOutgoingApiInventoryRecord(result, subSample, user);
    return result;
  }

  @SuppressWarnings("unchecked")
  protected SubSample getIfExists(Long id) {
    boolean exists = subSampleDao.exists(id);
    if (!exists) {
      throw new NotFoundException("No SubSample with id: " + id);
    }
    return subSampleDao.get(id);
  }

  @Override
  public ApiSubSample updateApiSubSample(ApiSubSample apiSubSample, User user) {
    SubSample dbSubSample = getIfExists(apiSubSample.getId());
    ApiSubSample original = new ApiSubSample(dbSubSample);
    boolean temporaryLock = lockItemForEdit(dbSubSample, user);

    try {
      dbSubSample = getIfExists(dbSubSample.getId());
      Container orgParent = dbSubSample.getParentContainer();
      boolean contentChanged =
          extraFieldHelper.createDeleteRequestedExtraFieldsInDatabaseSubSample(
              apiSubSample, dbSubSample, user);
      contentChanged |=
          barcodesHelper.createDeleteRequestedBarcodes(
              apiSubSample.getBarcodes(), dbSubSample, user);
      contentChanged |=
          identifiersHelper.createDeleteRequestedIdentifiers(
              apiSubSample.getIdentifiers(), dbSubSample, user);
      contentChanged |= apiSubSample.applyChangesToDatabaseSubSample(dbSubSample, user);
      contentChanged |= saveIncomingSubSampleImage(dbSubSample, apiSubSample, user);
      boolean moveSuccessful =
          moveHelper.moveRecordToTargetParentAndLocation(
              dbSubSample,
              apiSubSample.getParentContainer(),
              apiSubSample.getParentLocation(),
              user);

      if (contentChanged) {
        registerSubSampleModification(user, dbSubSample);
      }
      if (moveSuccessful) {
        publisher.publishEvent(
            new InventoryMoveEvent(dbSubSample, orgParent, dbSubSample.getParentContainer(), user));
      }
      if (contentChanged || moveSuccessful) {
        dbSubSample = subSampleDao.save(dbSubSample);
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbSubSample, user);
      }
    }

    ApiSubSample updated = getPopulatedApiSubSampleFull(dbSubSample, user);
    updateOntologyOnUpdate(original, updated, user);
    return updated;
  }

  @Override
  public ApiSubSample createNewApiSubSample(
      ApiSubSample incomingSubSample, Long sampleId, User user) {

    Sample dbSample = sampleApiMgr.getSampleById(sampleId, user);
    invPermissions.assertUserCanEditInventoryRecord(dbSample, user);

    SubSample newSubSample;
    boolean temporaryLock = lockItemForEdit(dbSample, user);
    try {
      newSubSample = recordFactory.createSubSample(incomingSubSample.getName(), user, dbSample);
      incomingSubSample.applyChangesToDatabaseSubSample(newSubSample, user);

      Container workbench = containerDao.getWorkbenchForUser(user);

      setWorkbenchAsParentForNewInventoryRecord(workbench, newSubSample);
      newSubSample = subSampleDao.save(newSubSample);
      publisher.publishEvent(new InventoryCreationEvent(newSubSample, user));

      dbSample.addSubSample(newSubSample);
      dbSample.recalculateTotalQuantity();
      sampleApiMgr.saveDbSampleUpdate(dbSample, user);

    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbSample, user);
      }
    }

    return getPopulatedApiSubSampleFull(newSubSample, user);
  }

  private boolean saveIncomingSubSampleImage(
      SubSample subSample, ApiSubSample apiSubSample, User user) {
    return saveIncomingImage(
        subSample, apiSubSample, user, SubSample.class, ss -> subSampleDao.save(ss));
  }

  @Override
  public ApiSubSample registerApiSubSampleUsage(
      ApiSubSample apiSubSample, QuantityInfo usedQuantity, User user) {

    SubSample dbSubSample = getIfExists(apiSubSample.getId());
    if (usedQuantity.getNumericValue().equals(BigDecimal.ZERO)) {
      return getPopulatedApiSubSampleFull(dbSubSample, user);
    }

    boolean temporaryLock = lockItemForEdit(dbSubSample, user);
    try {
      dbSubSample = getIfExists(dbSubSample.getId());

      QuantityInfo orgQuantity = dbSubSample.getQuantity();
      QuantityInfo newQuantity = qUtils.sum(Arrays.asList(orgQuantity, usedQuantity.negate()));

      // if usage is larger than remaining quantity set remaining to zero
      if (newQuantity.getNumericValue().compareTo(BigDecimal.ZERO) < 0) {
        newQuantity.setNumericValue(BigDecimal.ZERO);
      }

      dbSubSample.setQuantity(newQuantity);
      registerSubSampleModification(user, dbSubSample);
      dbSubSample = subSampleDao.save(dbSubSample);

    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbSubSample, user);
      }
    }

    return getPopulatedApiSubSampleFull(dbSubSample, user);
  }

  private void registerSubSampleModification(User user, SubSample dbSubSample) {
    dbSubSample.setModificationDate(new Date());
    dbSubSample.setModifiedBy(user.getUsername(), IActiveUserStrategy.CHECK_OPERATE_AS);
    publisher.publishEvent(new InventoryEditingEvent(dbSubSample, user));
  }

  @Override
  public ApiSubSample addSubSampleNote(
      Long subSampleId, ApiSubSampleNote subSampleNote, User user) {
    SubSample subSample = getIfExists(subSampleId);
    subSample.addNote(subSampleNote.getContent(), user);
    subSample = subSampleDao.save(subSample);
    publisher.publishEvent(new InventoryEditingEvent(subSample, user));
    return getPopulatedApiSubSampleFull(subSample, user);
  }

  @Override
  public ApiSubSample duplicate(Long subSampleId, User user) {
    return split(SubSampleDuplicateConfig.simpleDuplicate(subSampleId), user).get(0);
  }

  @Override
  public List<ApiSubSample> split(SubSampleDuplicateConfig cfg, User user) {
    SubSample origSubSample = assertUserCanEditSubSample(cfg.getSubSampleId(), user);
    Container workbench = containerDao.getWorkbenchForUser(user);

    QuantityInfo newQ = origSubSample.getQuantity().copy();
    int numCopiesToMake = cfg.getNumSubSamples() - 1;
    if (cfg.isSplit() && !newQ.getNumericValue().equals(BigDecimal.ZERO)) {
      newQ = qUtils.divide(newQ, BigDecimal.valueOf(cfg.getNumSubSamples()));
      assertSplitResultQuantityNotZero(cfg, origSubSample, newQ);
      origSubSample.setQuantity(newQ.copy());
      registerSubSampleModification(user, origSubSample);
      subSampleDao.save(origSubSample);
    }
    List<ApiSubSample> copied = new ArrayList<>();
    for (int i = 0; i < numCopiesToMake; i++) {
      SubSample copy;
      if (cfg.isSplit()) {
        final int nameSuffix = i + 2; // rsinv-148
        copy = origSubSample.copy(ss -> ss.getName() + "." + nameSuffix, newQ, user);
      } else {
        copy = origSubSample.copy(user); // prt-238
      }
      setWorkbenchAsParentForNewInventoryRecord(workbench, copy);
      setNewCreatorForCopiedInventoryRecord(copy, user);
      copy = subSampleDao.save(copy);
      copied.add(new ApiSubSample(copy));
      publisher.publishEvent(new InventoryCreationEvent(copy, user));
    }
    if (cfg.isSplit()) {
      origSubSample.setName(origSubSample.getName() + ".1");
    }

    return copied;
  }

  private void assertSplitResultQuantityNotZero(
      SubSampleDuplicateConfig cfg, SubSample origSubSample, QuantityInfo newQ) {
    if (newQ.getNumericValue().compareTo(BigDecimal.ZERO) == 0) {
      throw new IllegalArgumentException(
          "Can't split "
              + origSubSample.getQuantity().toPlainString()
              + " into "
              + cfg.getNumSubSamples()
              + " subsamples:"
              + " resulting subsamples would have quantity equal to 0.");
    }
  }

  @Override
  public ApiSubSample markSubSampleAsDeleted(
      Long subSampleId, User user, boolean partOfSampleDeletion) {
    SubSample dbSubSample = assertUserCanDeleteSubSample(subSampleId, user);
    boolean temporaryLock = lockItemForEdit(dbSubSample, user);
    try {
      dbSubSample = getIfExists(dbSubSample.getId());
      if (!dbSubSample.isDeleted()) {
        dbSubSample.removeFromCurrentParent();
        dbSubSample.setRecordDeleted(true);
        dbSubSample.setDeletedOnSampleDeletion(partOfSampleDeletion);
        dbSubSample = subSampleDao.save(dbSubSample);

        if (!partOfSampleDeletion) {
          dbSubSample.getSample().refreshActiveSubSamples();
          dbSubSample.getSample().recalculateTotalQuantity();
        }
        publisher.publishEvent(new InventoryDeleteEvent(dbSubSample, user));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbSubSample, user);
      }
    }
    ApiSubSample deleted = getPopulatedApiSubSampleFull(dbSubSample, user);
    updateOntologyOnRecordChanges(deleted, user);
    return deleted;
  }

  @Override
  public ApiSubSample restoreDeletedSubSample(Long id, User user, boolean partOfSampleRestore) {
    SubSample dbSubSample = assertUserCanDeleteSubSample(id, user);
    boolean temporaryLock = lockItemForEdit(dbSubSample, user);
    try {
      dbSubSample = getIfExists(dbSubSample.getId());
      Sample parentSample = dbSubSample.getSample();
      if (dbSubSample.isDeleted()) {
        if (!parentSample.isTemplate()) {
          Container workbench = containerDao.getWorkbenchForUser(user);
          dbSubSample.moveToNewParent(workbench);
        }
        dbSubSample.setRecordDeleted(false);
        dbSubSample.setDeletedOnSampleDeletion(false);
        dbSubSample = subSampleDao.save(dbSubSample);
        publisher.publishEvent(new InventoryRestoreEvent(dbSubSample, user));

        if (!partOfSampleRestore) {
          // refresh parent sample
          parentSample.refreshActiveSubSamples();
          parentSample.recalculateTotalQuantity();
        }
      }
      if (!partOfSampleRestore && parentSample.isDeleted()) {
        sampleApiMgr.restoreDeletedSample(parentSample.getId(), user, false);
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbSubSample, user);
      }
    }
    ApiSubSample restored = getPopulatedApiSubSampleFull(dbSubSample, user);
    updateOntologyOnRecordChanges(restored, user);
    return restored;
  }
}

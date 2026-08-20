package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("inventoryOperationManager")
public class InventoryOperationManagerImpl implements InventoryOperationManager {

  @Autowired private SampleApiManager sampleApiMgr;
  @Autowired private SubSampleApiManager subSampleApiMgr;

  @Override
  public ApiSampleWithFullSubSamples performOperation(
      ApiInventoryOperationPost request, User user) {
    // Validate-before-mutate: assert edit permission on every origin first, so a permission
    // failure aborts before anything is written (a throw inside this shared transaction marks it
    // rollback-only). See DevDocs/adr/0006.
    for (ApiInventoryOperationOriginUpdate origin : request.getOrigins()) {
      subSampleApiMgr.assertUserCanEditSubSample(origin.getId(), user);
    }

    // Reduce each origin by the amount taken from it BEFORE creating the new sample, so the new
    // subsample is the most-recently-modified record and therefore sorts first in a
    // modification-date-descending listing (registerApiSubSampleUsage stamps each origin's
    // modification date now; the new subsample is stamped later, when created below).
    // registerApiSubSampleUsage subtracts (unit-aware) and clamps at zero, so an operation can only
    // ever decrease the origin, never increase it. Any custom fields the operation adds to the
    // origin
    // itself (Destroy's disposed date) are applied through the ordinary subsample-edit path, each
    // marked newFieldRequest by the frontend. Coordinated inside this manager so it joins the one
    // transaction with the sample creation. See DevDocs/adr/0007, DevDocs/adr/0010,
    // DevDocs/adr/0013.
    // Mutate origins in ascending id order (not request order) so two concurrent multi-origin
    // operations over overlapping origins acquire their row locks in one consistent order and
    // cannot deadlock. The validator guarantees unique, non-null ids by this point.
    List<ApiInventoryOperationOriginUpdate> originsById =
        request.getOrigins().stream()
            .sorted(Comparator.comparing(ApiInventoryOperationOriginUpdate::getId))
            .toList();
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      subSampleApiMgr.registerApiSubSampleUsage(
          origin.getId(), origin.getAmountTaken().toQuantityInfo(), user);
      if (CollectionUtils.isNotEmpty(origin.getExtraFields())) {
        ApiSubSample fieldUpdate = new ApiSubSample();
        fieldUpdate.setId(origin.getId());
        fieldUpdate.setExtraFields(origin.getExtraFields());
        // Sparse update: null tags means "leave tags untouched"; the DTO's default empty list
        // would be applied as "clear all tags" and silently wipe a tagged origin's tags (same
        // idiom as InventoryIdentifierApiManagerImpl's sparse updates).
        fieldUpdate.setTags(null);
        subSampleApiMgr.updateApiSubSample(fieldUpdate, user);
      }
    }

    // A terminal operation (noOutput, e.g. Destroy) sends no new sample: it only acts on its
    // origins,
    // so there is nothing to create and nothing to return. See DevDocs/adr/0013.
    return request.getNewSample() == null
        ? null
        : sampleApiMgr.createNewApiSample(request.getNewSample(), user);
  }
}

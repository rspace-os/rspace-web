package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
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
    // rollback-only). See adr/0001.
    for (ApiInventoryOperationOriginUpdate origin : request.getOrigins()) {
      subSampleApiMgr.assertUserCanEditSubSample(origin.getId(), user);
    }

    ApiSampleWithFullSubSamples created =
        sampleApiMgr.createNewApiSample(request.getNewSample(), user);

    // Reduce each origin by the amount taken from it. registerApiSubSampleUsage subtracts (unit-
    // aware) and clamps at zero, so an operation can only ever decrease the origin, never increase
    // it. Coordinated inside this manager so it joins the one transaction with the sample creation
    // above. See adr/0002.
    for (ApiInventoryOperationOriginUpdate origin : request.getOrigins()) {
      subSampleApiMgr.registerApiSubSampleUsage(
          origin.getId(), origin.getAmountTaken().toQuantityInfo(), user);
    }

    return created;
  }
}

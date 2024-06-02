package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InventoryEditLocksApi;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import org.jsoup.helper.Validate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
public class InventoryEditLocksController extends BaseApiInventoryController
    implements InventoryEditLocksApi {

  @Override
  public ApiInventoryEditLock lockItemForEdit(
      @PathVariable String globalId, @RequestAttribute(name = "user") User user) {

    Validate.isTrue(GlobalIdentifier.isValid(globalId), "not a valid global id: " + globalId);
    assertUserCanEditInventoryRecord(new GlobalIdentifier(globalId), user);

    return tracker.attemptToLockForEdit(globalId, user);
  }

  @Override
  public void unlockItemAfterEdit(
      @PathVariable String globalId, @RequestAttribute(name = "user") User user) {

    Validate.isTrue(GlobalIdentifier.isValid(globalId), "not a valid global id: " + globalId);

    // lock owner can always unlock, for others (or unlocked record) do permission check before the
    // attempt
    if (tracker.getLockOwnerForItem(globalId) == null
        || !tracker.getLockOwnerForItem(globalId).equals(user.getUsername())) {
      assertUserCanEditInventoryRecord(new GlobalIdentifier(globalId), user);
    }
    tracker.attemptToUnlock(globalId, user);
  }
}

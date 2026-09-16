package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiUser;
import lombok.Getter;

/**
 * A conflict the caller can retry once the holder is done, not a bad request: the API boundary maps
 * it to 409.
 */
@Getter
public class InventoryEditLockHeldException extends RuntimeException {

  private final String globalId;
  private final ApiUser owner;

  public InventoryEditLockHeldException(String globalId, ApiUser owner) {
    super(globalId + " is currently being edited by " + owner.getUsername());
    this.globalId = globalId;
    this.owner = owner;
  }

  public String getOwnerDisplayName() {
    String name =
        String.join(
                " ",
                java.util.stream.Stream.of(owner.getFirstName(), owner.getLastName())
                    .filter(part -> part != null && !part.isBlank())
                    .toList())
            .trim();
    return name.isEmpty() ? owner.getUsername() : name;
  }
}

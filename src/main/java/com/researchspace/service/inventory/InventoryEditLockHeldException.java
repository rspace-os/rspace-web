package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiUser;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

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
        Stream.of(owner.getFirstName(), owner.getLastName())
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining(" "));
    return name.isEmpty() ? owner.getUsername() : name;
  }
}

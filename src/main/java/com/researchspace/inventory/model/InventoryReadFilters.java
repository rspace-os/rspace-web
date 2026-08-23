package com.researchspace.inventory.model;

import com.researchspace.model.collection.CollectionDescription.InternalFilter;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import java.util.List;

/**
 * The properties the inventory read rule tests, shared by the rule and by each collection it
 * constrains.
 *
 * <p>These are internal filters rather than fields. A caller must not filter on {@code sharingAcl},
 * because the matching rows would tell them which groups an item is shared with, and a caller has
 * no reason to filter on the sharing mode of records they cannot already see.
 *
 * <p>Every inventory collection whose DAO extends {@code InventoryDaoHibernate} must add {@link
 * #ALL} to its description, or the rule fails to compile against it.
 */
public final class InventoryReadFilters {

  public static final String OWNER_USERNAME = "ownerUsername";
  public static final String SHARING_MODE = "sharingMode";
  public static final String SHARING_ACL = "sharingAcl";

  public static final List<InternalFilter> ALL =
      List.of(
          new InternalFilter(OWNER_USERNAME, "owner.username", CollectionFieldTypes.text()),
          new InternalFilter(
              SHARING_MODE,
              "sharingMode",
              CollectionFieldTypes.enumeration(InventorySharingMode.class)),
          new InternalFilter(SHARING_ACL, "sharingACL.acl", CollectionFieldTypes.text()));

  private InventoryReadFilters() {}
}

package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class InventoryIdentifierApiManagerImplUnitTest {

  /**
   * A {@link com.researchspace.model.inventory.DigitalObjectIdentifier} persisted before the type
   * column was populated loads with a null type. settingTypeFor must not NPE on the {@code switch}:
   * such legacy identifiers predate PDINST and default to IGSN.
   */
  @Test
  void settingTypeForNullTypeDefaultsToIgsn() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    Method settingTypeFor =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "settingTypeFor", IdentifierType.class);
    settingTypeFor.setAccessible(true);

    Object result = settingTypeFor.invoke(mgr, (IdentifierType) null);

    assertEquals(InventorySettingType.IGSN, result);
  }
}

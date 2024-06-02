package com.researchspace.service;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.researchspace.model.User;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

public class SystemPropertyManagerTest extends SpringTransactionalTest {

  private static final String GOOGLE_DRIVE_AVAILABLE = "googledrive.available";

  private @Autowired SystemPropertyManager sysPropMgr;
  private final long parentId = -1L;
  private final long childId = -2L;

  @Test
  public void testSaveByNameHappyCase() {
    User sysadmin = logoutAndLoginAsSysAdmin();
    // unknown property
    assertNull(sysPropMgr.save("unknown.property", "xxx", sysadmin));
    // saves new syspropvalue
    SystemPropertyValue spv = sysPropMgr.save("child.property", "xxx", sysadmin);
    assertNotNull(spv);
    assertEquals("xxx", spv.getValue());
    // updates existing value
    spv.setValue("yyy");
    spv = sysPropMgr.save(spv, sysadmin);
    assertEquals("yyy", spv.getValue());

    spv = sysPropMgr.save(spv.getId(), "zzz", sysadmin);
    assertEquals("zzz", spv.getValue());
  }

  @Test(expected = DataAccessException.class)
  public void saveByIdFailsIfNotExist() {
    User sysadmin = logoutAndLoginAsSysAdmin();
    sysPropMgr.save(-200L, "any", sysadmin);
  }

  @Test(expected = IllegalArgumentException.class)
  public void saveWrongDataTypeFails() {
    User sysadmin = logoutAndLoginAsSysAdmin();
    SystemPropertyValue spv = sysPropMgr.save("numeric.property", "123", sysadmin);
    assertEquals(123 + "", spv.getValue());
    sysPropMgr.save("numeric.property", "not numeric", sysadmin);
  }

  @Test
  public void listAll() {
    List<SystemPropertyValue> vals = sysPropMgr.getAll();
    for (SystemPropertyValue val : vals) {
      log.info(val.toString());
      assertNotNull(val.getProperty().getName());
      assertNotNull(val.getValue());
    }
  }

  @Test
  public void findByName() {
    assertNotNull(sysPropMgr.findByName(GOOGLE_DRIVE_AVAILABLE));
    assertNull(sysPropMgr.findByName("xxx"));
  }

  @Test
  public void cachingBehaviour() {
    User sysadmin = logoutAndLoginAsSysAdmin();
    // calling get twice returns identical(cached) object
    SystemPropertyValue original = sysPropMgr.findByName(GOOGLE_DRIVE_AVAILABLE);
    SystemPropertyValue cached = sysPropMgr.findByName(GOOGLE_DRIVE_AVAILABLE);
    assertThat(original, sameInstance(cached));
    // now save, which will update the cache with a new object instance
    sysPropMgr.save(cached.getProperty().getName(), reverse(original.getValue()), sysadmin);
    SystemPropertyValue reloaded = sysPropMgr.findByName(GOOGLE_DRIVE_AVAILABLE);
    assertThat(reloaded, not(sameInstance(cached)));
    // assert value is updated
    assertEquals(reloaded.getValue(), reverse(original.getValue()));
    // revert to previous value
    sysPropMgr.save(cached.getProperty().getName(), original.getValue(), sysadmin);
  }

  private String reverse(String value) {
    return StringUtils.reverse(value);
  }
}

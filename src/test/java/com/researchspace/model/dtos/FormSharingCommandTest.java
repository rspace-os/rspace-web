package com.researchspace.model.dtos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.model.AccessControl;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.RSForm;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;

public class FormSharingCommandTest {

  RSForm form;

  @Test
  public void testFormSharingCommandTemplate() {
    form = TestFactory.createAnyForm("X");
    form.setId(1L);
    assertNotNull(form.getAccessControl());
    FormSharingCommand tsc = new FormSharingCommand(form);
    assertEquals(1L, tsc.getFormId().longValue());
    assertThat(tsc.getGroupOptions()).element(0).isEqualTo(PermissionType.NONE.toString());
    assertThat(tsc.getWorldOptions()).element(0).isEqualTo(PermissionType.NONE.toString());
  }

  @Test
  public void testToAccessControl() {
    form = TestFactory.createAnyForm("X");
    form.setId(1L);
    assertNotNull(form.getAccessControl());
    FormSharingCommand tsc = new FormSharingCommand(form);
    AccessControl ac = tsc.toAccessControl();
    assertNotNull(ac.getGroupPermissionType());
    assertNotNull(ac.getWorldPermissionType());
    assertNotNull(ac.getOwnerPermissionType());
  }
}

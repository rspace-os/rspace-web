package com.researchspace.api.v1.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.FormTemplatesCommon.FormPost;
import com.researchspace.api.v1.controller.FormTemplatesCommonTest;
import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FormFieldSource;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.FormManager;
import com.researchspace.session.UserSessionTracker;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class FormApiHandlerImplTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock FormManager formMgr;
  @InjectMocks RSFormApiHandlerImpl impl;
  User anyUser;
  RSForm anyForm;
  final Long formInitialId = 1L;
  final Long fieldFormInitialId = -1L;
  UserSessionTracker sessionTracker;
  FormPost toPost;

  @Before
  public void setup() {
    anyUser = TestFactory.createAnyUser("any");
    sessionTracker = new UserSessionTracker();
    // single field
    anyForm = TestFactory.createAnyForm();
    anyForm.setId(formInitialId);
    anyForm.getFieldForms().get(0).setId(fieldFormInitialId);
    // creates a post with 6 fields
    toPost = FormTemplatesCommonTest.createValidFormPost();
  }

  @Test
  public void testEditFormRejectedIfCannotEdit() {
    anyForm.setEditStatus(EditStatus.CAN_NEVER_EDIT);
    mockGetFormForEditing();
    assertThrows(
        IllegalStateException.class,
        () -> impl.editForm(formInitialId, toPost, anyUser, sessionTracker));
  }

  @Test
  public void testEditFormRejectedIfIdDoesntBelongToForm() {
    anyForm.setEditStatus(EditStatus.CAN_NEVER_EDIT);
    toPost.getFields().get(0).setId(fieldFormInitialId + 1000);
    mockGetFormForEditing();
    assertThrows(
        IllegalArgumentException.class,
        () -> impl.editForm(formInitialId, toPost, anyUser, sessionTracker));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testEditFormWithNewFieldsNeverCallsUpdate() {
    anyForm.setEditStatus(EditStatus.EDIT_MODE);
    mockGetFormForEditing();
    when(formMgr.createFieldForm(
            Mockito.any(FormFieldSource.class), Mockito.anyLong(), Mockito.eq(anyUser)))
        .thenReturn(
            createFieldWithId(10),
            createFieldWithId(11),
            createFieldWithId(12),
            createFieldWithId(13),
            createFieldWithId(14),
            createFieldWithId(15));
    when(formMgr.save(anyForm, anyUser)).thenReturn(anyForm);

    when(formMgr.reorderFields(
            Mockito.eq(anyForm.getId()), Mockito.any(List.class), Mockito.eq(anyUser)))
        .thenReturn(anyForm);

    impl.editForm(formInitialId, toPost, anyUser, sessionTracker);
    verify(formMgr, never())
        .updateFieldForm(
            Mockito.any(FormFieldSource.class), Mockito.anyLong(), Mockito.eq(anyUser));
    final int numberOfNewFields = 9;
    verify(formMgr, times(numberOfNewFields))
        .createFieldForm(
            Mockito.any(FormFieldSource.class), Mockito.anyLong(), Mockito.eq(anyUser));
    // the original field is deleted.
    verify(formMgr, times(1))
        .deleteFieldFromForm(Mockito.eq(fieldFormInitialId), Mockito.eq(anyUser));
  }

  RadioFieldForm createFieldWithId(int i) {
    RadioFieldForm rc = new RadioFieldForm();
    rc.setId(Integer.valueOf(i).longValue());
    return rc;
  }

  private void mockGetFormForEditing() {
    when(formMgr.get(formInitialId, anyUser)).thenReturn(anyForm);
    when(formMgr.getForEditing(formInitialId, anyUser, sessionTracker, false)).thenReturn(anyForm);
  }
}

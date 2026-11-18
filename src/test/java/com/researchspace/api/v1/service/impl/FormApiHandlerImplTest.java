package com.researchspace.api.v1.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.FormTemplatesCommon.FormFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.FormPost;
import com.researchspace.api.v1.controller.FormTemplatesCommonTest;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FieldDisplayRuleDTO;
import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.FormDefinitionCommand;
import com.researchspace.service.FormManager;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FormApiHandlerImplTest {
  @Mock FormManager formMgr;
  @InjectMocks RSFormApiHandlerImpl impl;
  User anyUser;
  RSForm anyForm;
  UserSessionTracker sessionTracker;
  FormPost toPost;

  @BeforeEach
  public void setup() {
    anyUser = TestFactory.createAnyUser("any");
    sessionTracker = new UserSessionTracker();
    anyForm = TestFactory.createAnyForm();
    toPost = FormTemplatesCommonTest.createValidFormPost();
  }

  @Test
  public void editDelegatesTheWholeDefinitionToTheFormManager() {
    toPost.getFields().get(0).setId(5L);
    ArgumentCaptor<FormDefinitionCommand> definition =
        ArgumentCaptor.forClass(FormDefinitionCommand.class);
    when(formMgr.editForm(eq(1L), definition.capture(), eq(anyUser), eq(sessionTracker)))
        .thenReturn(anyForm);

    assertSame(anyForm, impl.editForm(1L, toPost, anyUser, sessionTracker));

    FormDefinitionCommand sent = definition.getValue();
    assertEquals(toPost.getName(), sent.name());
    assertEquals(toPost.getTags(), sent.tags());
    assertThat(sent.fields()).hasSameSizeAs(toPost.getFields());
    for (int i = 0; i < sent.fields().size(); i++) {
      FormFieldPost<?> posted = toPost.getFields().get(i);
      FormFieldSaveItem item = sent.fields().get(i);
      assertEquals(posted.getId(), item.getId());
      assertEquals(posted.getType(), item.getType());
      assertSame(posted, item.getSource());
    }
  }

  @Test
  public void anOmittedRuleKeepsTheStoredRuleAndAnExplicitNullClearsIt() {
    FormFieldPost<?> cleared = toPost.getFields().get(1);
    cleared.setDisplayRule(null);
    FormFieldPost<?> set = toPost.getFields().get(2);
    FieldDisplayRuleDTO rule = new FieldDisplayRuleDTO("k", "HAS_VALUE", List.of());
    set.setDisplayRule(rule);
    set.setFieldKey("6f1c2e0a-0000-4000-8000-000000000000");

    List<FormFieldSaveItem> fields = impl.toDefinition(toPost).fields();

    assertTrue(fields.get(0).isKeepStoredDisplayRule());
    assertFalse(fields.get(1).isKeepStoredDisplayRule());
    assertFalse(fields.get(2).isKeepStoredDisplayRule());
    assertEquals(rule, fields.get(2).getDisplayRule());
    assertEquals(set.getFieldKey(), fields.get(2).getFieldKey());
  }

  @Test
  public void createDelegatesToTheFormManager() {
    when(formMgr.createForm(any(), eq(anyUser))).thenReturn(anyForm);
    assertSame(anyForm, impl.createForm(toPost, anyUser));
  }
}

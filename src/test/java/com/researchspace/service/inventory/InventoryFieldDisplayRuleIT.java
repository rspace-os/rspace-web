package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryEntityField.ApiInventoryFieldDef;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.model.User;
import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Field keys and display rules survive a committed save, a reload and the audited revision. Real
 * transactions, because Envers writes revisions on commit.
 */
public class InventoryFieldDisplayRuleIT extends RealTransactionSpringTestBase {

  private static final String GMO_KEY = "6f1c2e0a-8d5b-4c51-9a3e-2b7f1d0c4e9a";
  private static final FieldDisplayRule RULE =
      new FieldDisplayRule(GMO_KEY, ConditionOperator.IS_ANY_OF, List.of("Yes", "Unknown"));

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void keysAndRulesRoundTripThroughTheDatabaseAndTheAuditedRevision() {
    User user = createInitAndLoginAnyUser();
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("GMO register");
    ApiInventoryEntityField gmo = new ApiInventoryEntityField();
    gmo.setName("Is this a GMO?");
    gmo.setType(ApiFieldType.RADIO);
    gmo.setDefinition(new ApiInventoryFieldDef(List.of("Yes", "No", "Unknown"), false));
    gmo.setFieldKey(GMO_KEY);
    post.getFields().add(gmo);
    ApiInventoryEntityField permit = new ApiInventoryEntityField();
    permit.setName("GMO permit number");
    permit.setType(ApiFieldType.STRING);
    permit.setDisplayRule(RULE);
    post.getFields().add(permit);
    ApiSampleTemplate created = sampleApiMgr.createSampleTemplate(post, user);

    ApiSampleTemplate reloaded = sampleApiMgr.getApiSampleTemplateById(created.getId(), user);
    assertEquals(GMO_KEY, reloaded.getFields().get(0).getFieldKey());
    assertNull(reloaded.getFields().get(0).getDisplayRule());
    assertEquals(RULE, reloaded.getFields().get(1).getDisplayRule());
    String permitKey = reloaded.getFields().get(1).getFieldKey();

    // a second version, so that version 1 is read from the audit tables
    ApiSampleTemplate rename = new ApiSampleTemplate();
    rename.setId(created.getId());
    rename.setName("GMO register v2");
    sampleApiMgr.updateApiSampleTemplate(rename, user);

    ApiSampleTemplate version1 =
        sampleApiMgr.getApiSampleTemplateVersion(created.getId(), 1L, user);
    assertEquals("GMO register", version1.getName());
    assertEquals(GMO_KEY, version1.getFields().get(0).getFieldKey());
    assertNull(version1.getFields().get(0).getDisplayRule());
    assertEquals(permitKey, version1.getFields().get(1).getFieldKey());
    assertEquals(RULE, version1.getFields().get(1).getDisplayRule());
  }
}

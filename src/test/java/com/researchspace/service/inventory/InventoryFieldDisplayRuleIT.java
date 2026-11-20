package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryEntityField.ApiInventoryFieldDef;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Field keys and display rules survive a committed save, a reload and the audited revision; a
 * rejected update leaves the committed record as it was; and an unreadable stored rule loads as no
 * rule. Real transactions, because Envers writes revisions on commit and a rejected update must
 * roll back.
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

  @Test
  public void rejectedRevealLeavesTheSourceSelectionAndLinkAsCommitted() {
    User user = createInitAndLoginAnyUser();
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("GMO register with protocol");
    post.getFields().add(gmoField());
    ApiInventoryEntityField permit = textField("GMO permit number");
    permit.setMandatory(true);
    permit.setDisplayRule(RULE);
    post.getFields().add(permit);
    ApiInventoryEntityField protocol = new ApiInventoryEntityField();
    protocol.setName("Protocol");
    protocol.setType(ApiFieldType.LINK);
    protocol.setMandatory(true);
    protocol.setDisplayRule(RULE);
    post.getFields().add(protocol);
    ApiSampleTemplate template = sampleApiMgr.createSampleTemplate(post, user);
    String target = createBasicSampleForUser(user).getGlobalId();

    ApiSampleWithFullSubSamples request = new ApiSampleWithFullSubSamples();
    request.setName("hidden permit and protocol");
    request.setTemplateId(template.getId());
    request.getFields().add(options("No"));
    request.getFields().add(new ApiInventoryEntityField());
    ApiInventoryEntityField link = new ApiInventoryEntityField();
    link.setLink(apiLink(target));
    request.getFields().add(link);
    ApiSampleWithFullSubSamples sample = sampleApiMgr.createNewApiSample(request, user);

    // reveals the empty mandatory permit
    ApiSample revealPermit = update(sample);
    revealPermit.getFields().add(optionsUpdate(sample.getFields().get(0), "Yes"));
    revealPermit.getFields().add(withId(sample.getFields().get(2), apiLink(target)));
    assertThrows(
        IllegalArgumentException.class, () -> sampleApiMgr.updateApiSample(revealPermit, user));
    assertUnchanged(sample.getId(), target, user);

    // reveals the permit, filled, and the protocol, emptied by a field update without a link
    ApiSample revealEmptyLink = update(sample);
    revealEmptyLink.getFields().add(optionsUpdate(sample.getFields().get(0), "Yes"));
    ApiInventoryEntityField filledPermit = withId(sample.getFields().get(1), null);
    filledPermit.setContent("P-1");
    revealEmptyLink.getFields().add(filledPermit);
    revealEmptyLink.getFields().add(withId(sample.getFields().get(2), null));
    assertThrows(
        IllegalArgumentException.class, () -> sampleApiMgr.updateApiSample(revealEmptyLink, user));
    assertUnchanged(sample.getId(), target, user);
  }

  @Test
  public void unreadableStoredRuleLoadsAsNoRuleAndIsClearedWhenItsFieldIsNextWritten() {
    User user = createInitAndLoginAnyUser();
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("corrupted rule");
    post.getFields().add(gmoField());
    ApiInventoryEntityField permit = textField("GMO permit number");
    permit.setDisplayRule(RULE);
    post.getFields().add(permit);
    ApiSampleTemplate created = sampleApiMgr.createSampleTemplate(post, user);
    Long permitId = created.getFields().get(1).getId();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update("update InventoryEntityField set displayRule = '{not json' where id = ?", permitId);

    ApiSampleTemplate reloaded = sampleApiMgr.getApiSampleTemplateById(created.getId(), user);
    assertNull(reloaded.getFields().get(1).getDisplayRule());

    // a change elsewhere in the template leaves the field's row, and the unreadable text, alone
    ApiSampleTemplate renameTemplate = new ApiSampleTemplate();
    renameTemplate.setId(created.getId());
    renameTemplate.setName("corrupted rule, renamed");
    sampleApiMgr.updateApiSampleTemplate(renameTemplate, user);
    assertEquals("{not json", storedRule(jdbc, permitId));

    // writing the field's row writes its (null) rule too
    ApiSampleTemplate renameField = new ApiSampleTemplate();
    renameField.setId(created.getId());
    ApiInventoryEntityField renamed = withId(reloaded.getFields().get(1), null);
    renamed.setName("Permit no.");
    renameField.getFields().add(renamed);
    sampleApiMgr.updateApiSampleTemplate(renameField, user);
    assertNull(storedRule(jdbc, permitId));
  }

  private void assertUnchanged(Long sampleId, String linkTarget, User user) {
    ApiSample reloaded = sampleApiMgr.getApiSampleById(sampleId, user);
    assertEquals(List.of("No"), reloaded.getFields().get(0).getSelectedOptions());
    assertNotNull(reloaded.getFields().get(2).getLink());
    assertEquals(linkTarget, reloaded.getFields().get(2).getLink().getTargetGlobalId());
  }

  private static String storedRule(JdbcTemplate jdbc, Long fieldId) {
    return jdbc.queryForObject(
        "select displayRule from InventoryEntityField where id = ?", String.class, fieldId);
  }

  private static ApiInventoryEntityField gmoField() {
    ApiInventoryEntityField gmo = new ApiInventoryEntityField();
    gmo.setName("Is this a GMO?");
    gmo.setType(ApiFieldType.RADIO);
    gmo.setDefinition(new ApiInventoryFieldDef(List.of("Yes", "No", "Unknown"), false));
    gmo.setFieldKey(GMO_KEY);
    return gmo;
  }

  private static ApiInventoryEntityField textField(String name) {
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setName(name);
    field.setType(ApiFieldType.STRING);
    return field;
  }

  private static ApiInventoryLink apiLink(String target) {
    ApiInventoryLink link = new ApiInventoryLink();
    link.setTargetGlobalId(target);
    link.setRelationType("References");
    return link;
  }

  private static ApiInventoryEntityField options(String option) {
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setSelectedOptions(List.of(option));
    return field;
  }

  private static ApiInventoryEntityField optionsUpdate(
      ApiInventoryEntityField existing, String option) {
    ApiInventoryEntityField update = withId(existing, null);
    update.setSelectedOptions(List.of(option));
    return update;
  }

  private static ApiInventoryEntityField withId(
      ApiInventoryEntityField existing, ApiInventoryLink link) {
    ApiInventoryEntityField update = new ApiInventoryEntityField();
    update.setId(existing.getId());
    update.setLink(link);
    return update;
  }

  private static ApiSample update(ApiSampleWithFullSubSamples sample) {
    ApiSample update = new ApiSample();
    update.setId(sample.getId());
    return update;
  }
}

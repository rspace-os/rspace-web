package com.researchspace.api.v1.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.model.User;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.inventory.field.InventoryLink;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.util.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pure unit tests for {@link ApiExtraField#applyChangesToDatabaseExtraField} when applied to a
 * pre-existing Link extra-field. The DTO apply loop reconciles relationType only; target and
 * version-pin changes are applied in the service layer (ApiExtraFieldsHelper through
 * InventoryLinkManager#updateLink), which validates the target and recaptures the pinned audit
 * revision. Applying a pin here in the DTO would leave the stored targetRevisionId stale.
 */
public class ApiExtraFieldLinkUpdateTest {

  private User user;

  @Before
  public void setUp() {
    // A prior unit test in the same fork can leave a Subject bound to this thread
    // (Shiro's ThreadContext is shared, thread-local state). Without clearing it,
    // getSubject() below would return that stale subject, tied to the prior test's
    // SecurityManager/realm, and login() would throw AuthenticationException because
    // that realm has no "tester" account. Clear it so getSubject() builds a fresh
    // subject from the in-memory realm we set up next.
    ThreadContext.remove();
    user = new User();
    user.setUsername("tester");
    // setModifiedBy(..., CHECK_OPERATE_AS) consults the Shiro SecurityManager;
    // for these pure-unit tests we wire up a trivial in-memory subject.
    SimpleAccountRealm realm = new SimpleAccountRealm("test");
    realm.addAccount("tester", "pw");
    DefaultSecurityManager sm = new DefaultSecurityManager(realm);
    SecurityUtils.setSecurityManager(sm);
    SecurityUtils.getSubject().login(new UsernamePasswordToken("tester", "pw"));
  }

  @After
  public void tearDown() {
    try {
      SecurityUtils.getSubject().logout();
    } catch (Exception ignored) {
      // pass
    }
    ThreadContext.unbindSubject();
    ThreadContext.unbindSecurityManager();
    SecurityUtils.setSecurityManager(null);
  }

  private ExtraLinkField dbLinkField(Long versionPin) {
    InventoryLink link = new InventoryLink();
    link.setRelationType("References");
    link.setTargetGlobalId("SA42");
    link.setVersionPin(versionPin);
    ExtraLinkField field = new ExtraLinkField();
    field.setName("link to sample");
    field.setLink(link);
    return field;
  }

  private ApiExtraField apiLinkField(String relationType, Long versionPin) {
    ApiExtraField api = new ApiExtraField(ExtraFieldTypeEnum.LINK);
    api.setId(1L);
    api.setName("link to sample");
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setRelationType(relationType);
    apiLink.setTargetGlobalId("SA42");
    apiLink.setVersionPin(versionPin);
    api.setLink(apiLink);
    return api;
  }

  @Test
  public void leavesVersionPinChangeToTheServiceLayer() {
    ExtraLinkField dbField = dbLinkField(null);
    ApiExtraField apiField = apiLinkField("References", 7L);

    boolean changed = apiField.applyChangesToDatabaseExtraField(dbField, user);

    // a pin applied here would not recapture targetRevisionId, so the DTO must
    // leave it to ApiExtraFieldsHelper/InventoryLinkManager.updateLink
    assertEquals(false, changed);
    assertNull(dbField.getLink().getVersionPin());
  }

  @Test
  public void leavesVersionPinClearToTheServiceLayer() {
    ExtraLinkField dbField = dbLinkField(7L);
    ApiExtraField apiField = apiLinkField("References", null);

    boolean changed = apiField.applyChangesToDatabaseExtraField(dbField, user);

    assertEquals(false, changed);
    assertEquals(Long.valueOf(7L), dbField.getLink().getVersionPin());
  }

  @Test
  public void appliesUpdatedRelationTypeOntoExistingLink() {
    ExtraLinkField dbField = dbLinkField(null);
    ApiExtraField apiField = apiLinkField("IsCalibratedBy", null);

    boolean changed = apiField.applyChangesToDatabaseExtraField(dbField, user);

    assertTrue(changed);
    assertEquals("IsCalibratedBy", dbField.getLink().getRelationType());
  }

  @Test
  public void ignoresLinkPayloadWhenDbFieldIsNotALinkField() {
    ExtraTextField dbField = new ExtraTextField();
    dbField.setName("note");
    dbField.setData("hello");

    ApiExtraField apiField = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    apiField.setId(1L);
    apiField.setName("note");
    apiField.setContent("hello"); // no content change so no audit bump
    // a malformed payload carrying a link on a text field should be ignored,
    // not throw
    ApiInventoryLink badLink = new ApiInventoryLink();
    badLink.setRelationType("References");
    badLink.setTargetGlobalId("SA42");
    badLink.setVersionPin(7L);
    apiField.setLink(badLink);

    boolean changed = apiField.applyChangesToDatabaseExtraField(dbField, user);

    assertEquals(false, changed);
    assertEquals("hello", dbField.getData());
  }

  @Test
  public void noNullPointerWhenIncomingLinkIsNullOnLinkField() {
    ExtraLinkField dbField = dbLinkField(7L);

    ApiExtraField apiField = new ApiExtraField(ExtraFieldTypeEnum.LINK);
    apiField.setId(1L);
    apiField.setName("link to sample");
    // explicit: no link payload
    apiField.setLink(null);

    boolean changed = apiField.applyChangesToDatabaseExtraField(dbField, user);

    // unchanged because name and content match and no link payload was given
    assertEquals(false, changed);
    assertEquals(Long.valueOf(7L), dbField.getLink().getVersionPin());
  }

  @Test
  public void noNullPointerWhenDbLinkIsNullOnLinkField() {
    ExtraLinkField dbField = new ExtraLinkField();
    dbField.setName("link to sample");
    // do not call setLink, leaving the underlying InventoryLink null

    ApiExtraField apiField = apiLinkField("References", 7L);

    // this should not throw, even though the dbField has no underlying
    // InventoryLink to apply changes onto. In practice this can happen if a
    // Link extra-field row is being created in the same request and arrives
    // via the update path; defensive handling keeps the API resilient.
    boolean changed = apiField.applyChangesToDatabaseExtraField(dbField, user);

    assertEquals(false, changed);
  }

  @Test
  public void noChangeWhenLinkUnmodified() {
    ExtraLinkField dbField = dbLinkField(3L);
    ApiExtraField apiField = apiLinkField("References", 3L);

    boolean changed = apiField.applyChangesToDatabaseExtraField(dbField, user);

    assertEquals(false, changed);
    assertEquals(Long.valueOf(3L), dbField.getLink().getVersionPin());
    assertEquals("References", dbField.getLink().getRelationType());
  }
}

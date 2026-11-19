package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BackfillInventoryTemplateFieldKeys_RSDEV1706Test extends SpringTransactionalTest {

  @Test
  void keysTemplateFieldsWithVersion7AndLeavesRecordFieldsAndExistingKeys() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleTemplateAndSample(user);
    Long sampleTemplateId = sample.getTemplateId();
    ApiInstrumentTemplate instrumentTemplate = createBasicInstrumentTemplateForUser(user);
    ApiSampleTemplate keyed = createBasicSampleTemplate(user);
    String existingKey = keys("sample_id", keyed.getId()).get(0);
    clearKeys("sample_id", sampleTemplateId);
    clearKeys("sample_id", sample.getId());
    clearKeys("instrumentEntity_id", instrumentTemplate.getId());

    FieldKeyBackfillRunner.run(
        new BackfillInventoryTemplateFieldKeys_RSDEV1706(), applicationContext, sessionFactory);

    assertVersion7(keys("sample_id", sampleTemplateId));
    assertVersion7(keys("instrumentEntity_id", instrumentTemplate.getId()));
    keys("sample_id", sample.getId()).forEach(key -> assertNull(key));
    assertEquals(List.of(existingKey), keys("sample_id", keyed.getId()));
  }

  private static void assertVersion7(List<String> keys) {
    assertEquals(1, keys.size());
    UUID uuid = UUID.fromString(keys.get(0));
    assertEquals(7, uuid.version());
    assertEquals(uuid.toString(), keys.get(0));
  }

  private void clearKeys(String parentColumn, Long parentId) {
    sessionFactory.getCurrentSession().flush();
    sessionFactory
        .getCurrentSession()
        .createNativeMutationQuery(
            "update InventoryEntityField set fieldKey = null where " + parentColumn + " = :id")
        .setParameter("id", parentId)
        .executeUpdate();
  }

  private List<String> keys(String parentColumn, Long parentId) {
    sessionFactory.getCurrentSession().flush();
    return sessionFactory
        .getCurrentSession()
        .createNativeQuery(
            "select fieldKey from InventoryEntityField where " + parentColumn + " = :id",
            String.class)
        .setParameter("id", parentId)
        .list();
  }
}

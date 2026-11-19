package com.researchspace.dao.customliquibaseupdates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackfillInventoryTemplateFieldKeys_RSDEV1706Test extends SpringTransactionalTest {

  @Test
  void keysTemplateFieldsAndLeavesRecordFieldsAndExistingKeys() {
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

    assertThat(keys("sample_id", sampleTemplateId)).hasSize(1).doesNotContainNull();
    assertThat(keys("instrumentEntity_id", instrumentTemplate.getId()))
        .hasSize(1)
        .doesNotContainNull();
    keys("sample_id", sample.getId()).forEach(key -> assertNull(key));
    assertEquals(List.of(existingKey), keys("sample_id", keyed.getId()));
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

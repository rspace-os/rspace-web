package com.researchspace.service.inventory.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.dao.SampleTemplateDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A sample may not be created from a template that is in the trash.
 *
 * <p>RSpace soft-deletes, so a trashed template still exists and is still readable: the existence
 * and permission checks both pass and nothing else looked at the flag. The operations wizard could
 * therefore restore a remembered template that had since been trashed and perform against it, and
 * any other API client could do the same deliberately (RSDEV-1231).
 *
 * <p>The check is here, at the creation call site, rather than in {@code
 * assertUserCanReadSampleTemplate}: that assertion has a dozen callers including the template's own
 * GET, image and thumbnail endpoints and export, for which reading a trashed template is correct.
 */
@ExtendWith(MockitoExtension.class)
class SampleApiManagerImplDeletedTemplateTest {

  @Mock private SampleTemplateDao sampleTemplateDao;
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private MessageSourceUtils messages;
  @InjectMocks private SampleApiManagerImpl sampleApiMgr;

  private final User user = new User("someone");

  private ApiSampleWithFullSubSamples sampleFromTemplate(Long templateId) {
    ApiSampleWithFullSubSamples apiSample = new ApiSampleWithFullSubSamples("New sample");
    apiSample.setTemplateId(templateId);
    return apiSample;
  }

  @Test
  void createFromTrashedTemplateIsRejectedWithLocalisedText() {
    SampleTemplate trashed = new SampleTemplate();
    trashed.setRecordDeleted(true);
    when(sampleTemplateDao.exists(7L)).thenReturn(true);
    when(sampleTemplateDao.get(7L)).thenReturn(trashed);
    when(messages.getMessage("errors.inventory.template.deleted", new Object[] {7L}))
        .thenReturn("Sample template 7 is in the trash and cannot be used.");

    assertThat(
        assertThrows(
                IllegalArgumentException.class,
                () -> sampleApiMgr.createNewApiSample(sampleFromTemplate(7L), user))
            .getMessage(),
        containsString("is in the trash"));
  }
}

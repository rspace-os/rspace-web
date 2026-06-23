package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.dao.SampleDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.testutils.TestFactory;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The outgoing template DTO carries how many of the requesting user's samples were created from an
 * older version of the template (and so could be updated to its latest version). Only that genuine
 * "behind" count drives the UI's "Update Samples" affordance; a sample that merely links to the
 * template via a link field is not created from it, is not counted, and so must not surface the
 * action.
 */
@ExtendWith(MockitoExtension.class)
class SampleApiManagerImplSamplesToUpdateCountTest {

  @Mock private SampleDao sampleDao;
  private SampleApiManagerImpl manager;
  private User user;

  @BeforeEach
  void setUp() {
    manager = new SampleApiManagerImpl();
    ReflectionTestUtils.setField(manager, "sampleDao", sampleDao);
    user = TestFactory.createAnyUser("any");
  }

  private Sample templateWith(Long id, Long version) {
    Sample template = mock(Sample.class);
    lenient().when(template.getId()).thenReturn(id);
    lenient().when(template.getVersion()).thenReturn(version);
    return template;
  }

  @Test
  void countReflectsSamplesCreatedFromAnOlderVersion() {
    Sample template = templateWith(7L, 3L);
    when(sampleDao.getSamplesLinkingOlderTemplateVersionForUser(7L, 3L, user))
        .thenReturn(Arrays.asList(mock(Sample.class), mock(Sample.class)));

    ApiSampleTemplate api = new ApiSampleTemplate();
    manager.setSamplesToUpdateCount(api, template, user);

    assertEquals(2, api.getSamplesToUpdateCount());
  }

  @Test
  void countIsZeroWhenNoSampleIsBehind() {
    Sample template = templateWith(7L, 3L);
    when(sampleDao.getSamplesLinkingOlderTemplateVersionForUser(7L, 3L, user))
        .thenReturn(Collections.emptyList());

    ApiSampleTemplate api = new ApiSampleTemplate();
    manager.setSamplesToUpdateCount(api, template, user);

    assertEquals(0, api.getSamplesToUpdateCount());
  }
}

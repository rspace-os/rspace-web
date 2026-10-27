package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dto.SharingResult;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.LocalizedIllegalAddChildOperation;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SharingHandlerImplTest {

  @Mock private RecordSharingManager sharingManager;

  private SharingHandlerImpl handler;
  private User user;

  @BeforeEach
  void setUp() {
    handler = new SharingHandlerImpl();
    user = TestFactory.createAnyUser("sharer");
    ReflectionTestUtils.setField(handler, "sharingManager", sharingManager);
    ReflectionTestUtils.setField(
        handler, "messages", new MessageSourceUtils(new JsonMessageSource()));
  }

  @Test
  void shareResultResolvesLocalizedChildErrorArguments() {
    when(sharingManager.shareRecord(eq(user), eq(1L), any(ShareConfigElement[].class)))
        .thenThrow(new LocalizedIllegalAddChildOperation("folder.addChild.errors.cycle", "A", "B"));

    SharingResult result = handler.shareRecordsWithResult(command(), user);

    assertEquals(
        "Addition of [A] to  folder[B]  would produce a cycle in the folder tree,"
            + " which currently is not permitted.",
        result.getError().getErrorMessages().get(0));
  }

  @Test
  void shareResultPreservesOrdinaryIllegalAddChildMessage() {
    when(sharingManager.shareRecord(eq(user), eq(1L), any(ShareConfigElement[].class)))
        .thenThrow(new IllegalAddChildOperation("legacy failure"));

    SharingResult result = handler.shareRecordsWithResult(command(), user);

    assertEquals("legacy failure", result.getError().getErrorMessages().get(0));
  }

  private ShareConfigCommand command() {
    return new ShareConfigCommand(new Long[] {1L}, new ShareConfigElement[0]);
  }
}

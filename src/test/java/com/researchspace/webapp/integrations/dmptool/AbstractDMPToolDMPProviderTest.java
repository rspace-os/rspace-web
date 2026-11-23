package com.researchspace.webapp.integrations.dmptool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dmptool.model.DMPToolDMP;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.rda.model.DmpId;
import com.researchspace.rda.model.DmpId.DmpIdType;
import com.researchspace.service.DMPManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractDMPToolDMPProviderTest {

  private static final long PLAN_ID = 69563L;

  private @Mock DMPManager dmpManager;
  private @Mock UserManager userManager;
  private DMPToolDMPProviderImpl provider;
  private User user;
  private DMPToolDMP dmp;

  @BeforeEach
  void setUp() throws Exception {
    URL base = new URL("https://dmptool.example.org");
    provider = new DMPToolDMPProviderImpl(base);
    provider.dmpManager = dmpManager;
    provider.userManager = userManager;
    user = TestFactory.createAnyUser("any");
    dmp = new DMPToolDMP();
    dmp.setDmpId(new DmpId("https://doi.org/10.12345/abc", DmpIdType.DOI));
    dmp.setTitle("A plan");
    dmp.setLinks(Map.of("get", base + "/api/v2/plans/" + PLAN_ID));
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
  }

  @Test
  void reimportOfUnknownPlanAdoptsDmpToolAndStillReportsItExists() throws Exception {
    DMPUser existing = existingPlan(DMPSource.UNKNOWN);
    when(dmpManager.findByDmpId(PLAN_ID + "", user)).thenReturn(Optional.of(existing));

    assertNull(provider.doJsonDownload(dmp, "A plan", "token"));
    assertEquals(DMPSource.DMP_TOOL, existing.getSource());
    verify(dmpManager).save(existing);
  }

  @Test
  void reimportOfPlanWithKnownSourceWritesNothing() throws Exception {
    DMPUser existing = existingPlan(DMPSource.DMP_TOOL);
    when(dmpManager.findByDmpId(PLAN_ID + "", user)).thenReturn(Optional.of(existing));

    assertNull(provider.doJsonDownload(dmp, "A plan", "token"));
    verify(dmpManager, never()).save(any());
  }

  private DMPUser existingPlan(DMPSource source) {
    return new DMPUser(user, new DmpDto(PLAN_ID + "", "A plan", source, null, null));
  }
}

package com.researchspace.webapp.integrations.argos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.argos.model.ArgosDMP;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ArgosDMPProviderTest {

  private static final String PLAN_ID = "argos-plan-1";

  private @Mock DMPManager dmpManager;
  private @Mock MediaManager mediaManager;
  private @Mock UserManager userManager;
  private ArgosDMPProvider provider;
  private User user;

  @BeforeEach
  void setUp() throws Exception {
    provider = spy(new ArgosDMPProvider(new URL("https://argos.example.org")));
    provider.dmpManager = dmpManager;
    provider.mediaManager = mediaManager;
    provider.userManager = userManager;
    user = TestFactory.createAnyUser("any");
    ArgosDMP argosPlan = new ArgosDMP();
    argosPlan.setLabel("An Argos plan");
    ReflectionTestUtils.setField(argosPlan, "dois", List.of());
    doReturn(argosPlan).when(provider).getPlanById(PLAN_ID);
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
    when(mediaManager.saveNewDMP(anyString(), any(), eq(user), any()))
        .thenReturn(new EcatDocumentFile());
  }

  @Test
  void reimportSetsSourceOfUnknownPlan() throws Exception {
    DMPUser existing = existingPlan(DMPSource.UNKNOWN);

    provider.importDmp(PLAN_ID);

    verify(dmpManager).save(existing);
    assertEquals(DMPSource.ARGOS, existing.getSource());
  }

  @Test
  void reimportKeepsKnownSource() throws Exception {
    DMPUser existing = existingPlan(DMPSource.DMP_TOOL);

    provider.importDmp(PLAN_ID);

    verify(dmpManager).save(existing);
    assertEquals(DMPSource.DMP_TOOL, existing.getSource());
  }

  private DMPUser existingPlan(DMPSource source) {
    DMPUser existing = new DMPUser(user, new DmpDto(PLAN_ID, "An Argos plan", source, null, null));
    when(dmpManager.findByDmpId(PLAN_ID, user)).thenReturn(Optional.of(existing));
    return existing;
  }
}

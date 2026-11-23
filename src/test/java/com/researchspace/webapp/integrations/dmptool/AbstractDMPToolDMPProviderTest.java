package com.researchspace.webapp.integrations.dmptool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.dmptool.model.DMPToolDMP;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.rda.model.DmpId;
import com.researchspace.rda.model.DmpId.DmpIdType;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
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
  private @Mock MediaManager mediaManager;
  private AbstractDMPToolDMPProvider provider;
  private User user;
  private DMPToolDMP dmp;

  @BeforeEach
  void setUp() throws Exception {
    URL base = new URL("https://dmptool.example.org");
    provider = new DMPToolDMPProviderImpl(base);
    provider.dmpManager = dmpManager;
    provider.mediaManager = mediaManager;
    user = TestFactory.createAnyUser("any");
    dmp = new DMPToolDMP();
    dmp.setDmpId(new DmpId("https://doi.org/10.12345/abc", DmpIdType.DOI));
    dmp.setTitle("A plan");
    dmp.setLinks(Map.of("get", base + "/api/v2/plans/" + PLAN_ID));
  }

  @Test
  void reimportSetsSourceOfUnknownPlan() throws Exception {
    DMPUser existing = existingPlan(DMPSource.UNKNOWN);

    assertEquals(DMPSource.DMP_TOOL, reimport(existing).getSource());
  }

  @Test
  void reimportKeepsKnownSource() throws Exception {
    DMPUser existing = existingPlan(DMPSource.DMP_ONLINE);

    assertEquals(DMPSource.DMP_ONLINE, reimport(existing).getSource());
  }

  private DMPUser existingPlan(DMPSource source) {
    return new DMPUser(user, new DmpDto(PLAN_ID + "", "A plan", source, null, null));
  }

  private DMPUser reimport(DMPUser existing) throws Exception {
    when(mediaManager.saveNewDMP(anyString(), any(), eq(user), any()))
        .thenReturn(new EcatDocumentFile());
    when(dmpManager.findByDmpId(PLAN_ID + "", user)).thenReturn(Optional.of(existing));
    when(dmpManager.save(existing)).thenReturn(existing);
    return provider.saveJsonDMP(dmp, "A plan", user, "{}");
  }
}

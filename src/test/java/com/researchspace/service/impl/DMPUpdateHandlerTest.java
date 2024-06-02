package com.researchspace.service.impl;

// import org.junit.Before;
// import org.junit.Rule;
// import org.junit.Test;
// import org.mockito.junit.MockitoJUnit;
// import org.mockito.junit.MockitoRule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMP;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.DMPManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.webapp.integrations.dmptool.DMPToolDMPProviderImpl;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class DMPUpdateHandlerTest {

  private @Mock DMPManager dmpManager;
  private @Mock DMPToolDMPProviderImpl dmpClient;
  private @Mock IntegrationsHandler integrationsHandler;
  @InjectMocks DMPUpdateHandler dmpUpdateHandler;

  User anyUser;

  @BeforeEach
  void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
  }

  @Test
  void updateDMPSNotAttemptedIdNoDOI() {
    Supplier<URL> nullURLSupplier = () -> null;
    dmpUpdateHandler.updateDMPS(nullURLSupplier, anyUser, List.of(1L));
    verifyNoAttemptToAddDoi();
  }

  @Test
  void updateDMPSNotAttemptedIfNoPlans() {
    mockIntegrationsHandler(usableInfo());
    when(dmpManager.findDMPsForUser(anyUser)).thenReturn(Collections.emptyList());
    dmpUpdateHandler.updateDMPS(urlSupplier(), anyUser, List.of(1L));
    verifyNoAttemptToAddDoi();
  }

  @Test
  void updateDMPSNotAttemptedIfIntegrationNotEnabled() {
    IntegrationInfo usableInfo = usableInfo();
    usableInfo.setEnabled(false);
    mockIntegrationsHandler(usableInfo);
    dmpUpdateHandler.updateDMPS(urlSupplier(), anyUser, List.of(1L));
    verifyNoAttemptToAddDoi();
  }

  @Test
  void updateDMPSAttempted() {
    final long dmpUserId = -1;
    IntegrationInfo usableInfo = usableInfo();
    DMPUser dmpUser = createMultipleDMPusers(List.of(dmpUserId)).get(0);
    mockIntegrationsHandler(usableInfo);
    mockSuccessfulDMPAPIcall();
    when(dmpManager.findDMPsForUser(anyUser)).thenReturn(List.of(dmpUser));
    dmpUpdateHandler.updateDMPS(urlSupplier(), anyUser, List.of(dmpUserId));
    verifyAttemptToAddDoi();
  }

  @Test
  @DisplayName("update both selected DMPs")
  void updateMultipleDMPS() {
    final long dmpUserId1 = 1;
    final long dmpUserId2 = 2;
    IntegrationInfo usableInfo = usableInfo();
    var dmpUsers = createMultipleDMPusers(List.of(dmpUserId1, dmpUserId2));
    mockIntegrationsHandler(usableInfo);
    mockSuccessfulDMPAPIcall();
    when(dmpManager.findDMPsForUser(anyUser)).thenReturn(dmpUsers);
    dmpUpdateHandler.updateDMPS(urlSupplier(), anyUser, List.of(dmpUserId1, dmpUserId2));
    verify(dmpClient, Mockito.times(2))
        .addDoiIdentifierToDMP(any(String.class), anyString(), eq(anyUser));
  }

  @Test
  @DisplayName("Don't attempt to update non-existent DMPs")
  void filterNonExistentRequestedDMPs() {
    final long dmpUserId1 = 1;
    final long UNKNOWN_DMP_USER = -100;
    IntegrationInfo usableInfo = usableInfo();
    var dmpUser = createMultipleDMPusers(List.of(dmpUserId1)).get(0);
    mockIntegrationsHandler(usableInfo);
    when(dmpManager.findDMPsForUser(anyUser)).thenReturn(List.of(dmpUser));
    dmpUpdateHandler.updateDMPS(urlSupplier(), anyUser, List.of(UNKNOWN_DMP_USER));
    verifyNoAttemptToAddDoi();
  }

  private void mockIntegrationsHandler(IntegrationInfo usableInfo) {
    when(integrationsHandler.getIntegration(anyUser, IntegrationsHandler.DMPTOOL_APP_NAME))
        .thenReturn(usableInfo);
  }

  private void mockSuccessfulDMPAPIcall() {
    when(dmpClient.addDoiIdentifierToDMP(any(String.class), anyString(), eq(anyUser)))
        .thenReturn(successResult());
  }

  List<DMPUser> createMultipleDMPusers(List<Long> ids) {
    List<DMPUser> rc = new ArrayList<>();
    for (Long id : ids) {
      DMPUser dmpUser = new DMPUser(anyUser, new DMP("id" + id, "title" + id));
      ReflectionTestUtils.setField(dmpUser, "id", id);
      rc.add(dmpUser);
    }
    return rc;
  }

  private ServiceOperationResult<String> successResult() {
    return new ServiceOperationResult<String>("OK", true);
  }

  private IntegrationInfo usableInfo() {
    var integrationInfo = new IntegrationInfo();
    integrationInfo.setEnabled(true);
    integrationInfo.setOauthConnected(true);
    integrationInfo.setAvailable(true);
    return integrationInfo;
  }

  private Supplier<URL> urlSupplier() {
    try {
      URL url = new URL("https://doi.org/1234");
      return () -> url;
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
  }

  private void verifyNoAttemptToAddDoi() {
    verify(dmpClient, never()).addDoiIdentifierToDMP(any(String.class), anyString(), eq(anyUser));
  }

  private void verifyAttemptToAddDoi() {
    verify(dmpClient).addDoiIdentifierToDMP(any(String.class), anyString(), eq(anyUser));
  }
}

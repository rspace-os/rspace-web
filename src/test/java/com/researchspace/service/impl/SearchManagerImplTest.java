package com.researchspace.service.impl;

import static com.researchspace.Constants.SYSADMIN_ROLE;
import static com.researchspace.testutils.SearchTestUtils.createSimpleFullTextSearchCfg;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.dao.TextSearchDao;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchManagerImplTest {

  @Mock IPropertyHolder properties;
  @Mock MessageSourceUtils messages;
  @Mock TextSearchDao textSearchDao;
  @InjectMocks SearchManagerImpl searchManager;

  @Test
  void sysadminFloorComesFromTheDeploymentProperties() {
    when(properties.getSysadminMinSearchTermLength()).thenReturn(10);
    User sysadmin = TestFactory.createAnyUserWithRole("sys", SYSADMIN_ROLE);
    WorkspaceListingConfig sevenChars = createSimpleFullTextSearchCfg("abcdefg");

    assertThrows(
        IllegalArgumentException.class,
        () -> searchManager.searchWorkspaceRecords(sevenChars, sysadmin));
    verifyNoInteractions(textSearchDao);
  }

  @Test
  void userFloorComesFromTheDeploymentProperties() {
    when(properties.getUserMinSearchTermLength()).thenReturn(4);
    User user = TestFactory.createAnyUser("any");
    WorkspaceListingConfig threeChars = createSimpleFullTextSearchCfg("abc");

    assertThrows(
        IllegalArgumentException.class,
        () -> searchManager.searchWorkspaceRecords(threeChars, user));
    verifyNoInteractions(textSearchDao);
  }
}

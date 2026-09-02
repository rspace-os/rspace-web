package com.researchspace.export.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.preference.ExportPageSize;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;

@ExtendWith(MockitoExtension.class)
public class PDFExportConfigurerTest {

  @Mock private UserManager userMgr;

  private ExportConfigurerImpl pdfConfigurer;
  private User anyUser;
  private UserPreference notset;

  @BeforeEach
  public void setUp() {
    pdfConfigurer = new ExportConfigurerImpl();
    pdfConfigurer.setUserManager(userMgr);
    anyUser = TestFactory.createAnyUser("any");
    notset = anyUser.getValueForPreference(Preference.UI_PDF_PAGE_SIZE);
  }

  @Test
  public void testGetSystemDefault() {
    // default/empty content/invalid value
    assertEquals(ExportPageSize.UNKNOWN, pdfConfigurer.getSystemDefault());
    pdfConfigurer.setSystemDefault("");
    assertEquals(ExportPageSize.UNKNOWN, pdfConfigurer.getSystemDefault());
    pdfConfigurer.setSystemDefault("RANDOM_STRING");
    assertEquals(ExportPageSize.UNKNOWN, pdfConfigurer.getSystemDefault());

    // happy cases
    pdfConfigurer.setSystemDefault(ExportPageSize.A4.name());
    assertEquals(ExportPageSize.A4, pdfConfigurer.getSystemDefault());

    pdfConfigurer.setSystemDefault(ExportPageSize.LETTER.name());
    assertEquals(ExportPageSize.LETTER, pdfConfigurer.getSystemDefault());
  }

  @Test
  public void testGetUserDefault() {
    anyUser = TestFactory.createAnyUser("any");
    // by default, return unknown
    whenGettingPreference().thenReturn(notset);
    assertEquals(ExportPageSize.UNKNOWN, pdfConfigurer.getUserDefault(anyUser));

    UserPreference up =
        new UserPreference(Preference.UI_PDF_PAGE_SIZE, anyUser, ExportPageSize.A4.toString());
    anyUser.setPreference(up);
    whenGettingPreference().thenReturn(up);
    assertEquals(ExportPageSize.A4, pdfConfigurer.getUserDefault(anyUser));

    up = new UserPreference(Preference.UI_PDF_PAGE_SIZE, anyUser, ExportPageSize.LETTER.toString());
    anyUser.setPreference(up);
    assertEquals(ExportPageSize.LETTER, pdfConfigurer.getUserDefault(anyUser));

    up = new UserPreference(Preference.UI_PDF_PAGE_SIZE, anyUser, null);
    anyUser.setPreference(up);
    assertEquals(ExportPageSize.UNKNOWN, pdfConfigurer.getUserDefault(anyUser));
  }

  OngoingStubbing<UserPreference> whenGettingPreference() {
    return when(userMgr.getPreferenceForUser(anyUser, Preference.UI_PDF_PAGE_SIZE));
  }

  // Decision table 1 in RSPAC-193
  @Test
  public void testcalculateDefault() {
    // no default set
    User anyUser = TestFactory.createAnyUser("any");
    // by default, return unknown

    when(userMgr.getPreferenceForUser(anyUser, Preference.UI_PDF_PAGE_SIZE)).thenReturn(notset);
    assertEquals(ExportPageSize.UNKNOWN, pdfConfigurer.getDefault(anyUser));

    // user default set & system default; user default wins
    pdfConfigurer.setSystemDefault(ExportPageSize.LETTER.name());
    UserPreference up =
        new UserPreference(Preference.UI_PDF_PAGE_SIZE, anyUser, ExportPageSize.A4.toString());
    anyUser.setPreference(up);
    when(userMgr.getPreferenceForUser(anyUser, Preference.UI_PDF_PAGE_SIZE)).thenReturn(up);
    assertEquals(ExportPageSize.A4, pdfConfigurer.getDefault(anyUser));
    // just user default
    pdfConfigurer.setSystemDefault(null);
    assertEquals(ExportPageSize.A4, pdfConfigurer.getDefault(anyUser));

    // just systemdefault
    anyUser = TestFactory.createAnyUser("any"); // clean up user
    when(userMgr.getPreferenceForUser(anyUser, Preference.UI_PDF_PAGE_SIZE)).thenReturn(notset);
    pdfConfigurer.setSystemDefault(ExportPageSize.LETTER.name());
    assertEquals(ExportPageSize.LETTER, pdfConfigurer.getDefault(anyUser));
  }
}

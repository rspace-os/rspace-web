package com.researchspace.netfiles.samba;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class SambaUtilsTest {

  public static final String APATH_TO_AFILE = "apath/to/afile/";
  public static final String A_SAMBA_NAME = "\\apath\\to\\afile";
  private SambaUtils testee;
  @Mock private FileAllInformation smbjFileInfo;

  @Before
  public void setup() {
    testee = new SambaUtils();
    initMocks(this);
    when(smbjFileInfo.getNameInformation()).thenReturn(A_SAMBA_NAME);
  }

  @Test
  public void sambaClientCheckTargetFilePathResolution() {
    assertEquals("smb://test/", SambaUtils.getSmbFilePathForTarget("smb://test", ""));
    assertEquals(
        "smb://test/folder A/", SambaUtils.getSmbFilePathForTarget("smb://test", "folder A"));
    assertEquals("smb://test/", SambaUtils.getSmbFilePathForTarget("smb://test/", ""));
    assertEquals(
        "smb://test/folder A/", SambaUtils.getSmbFilePathForTarget("smb://test/", "folder A"));

    // RSPAC-1323
    assertEquals(
        "smb://ibn2-vsrv.ibn.kfa-juelich.de/ibn2-be-share$/Arbeits Gruppen/",
        SambaUtils.getSmbFilePathForTarget(
            "smb://ibn2-vsrv.ibn.kfa-juelich.de/ibn2-be-share$/", "Arbeits Gruppen"));
    assertEquals(
        "smb://ibn-net.kfa-juelich.de/ICS8-BE/BE/Arbeits Gruppen/",
        SambaUtils.getSmbFilePathForTarget(
            "smb://ibn-net.kfa-juelich.de/ICS8-BE/BE/", "Arbeits Gruppen"));
  }

  @Test
  public void getNameInformationFromSmbjFileInfoPathsAndSambaNameMatch() {
    String result = testee.getNameInformationFromSmbjFileInfo(smbjFileInfo, APATH_TO_AFILE, true);
    assertEquals("\\apath\\to\\afile", result);
  }

  @Test
  public void getNameInformationFromSmbjFileInfoPathsAndSambaNameDoNotMatch() {
    when(smbjFileInfo.getNameInformation()).thenReturn("sambasharename" + A_SAMBA_NAME);
    String result = testee.getNameInformationFromSmbjFileInfo(smbjFileInfo, APATH_TO_AFILE, true);
    assertEquals("apath\\to\\afile\\", result);
  }

  @Test
  public void getNameInformationFromSmbjFileInfoPathsAndSambaNameDoNotMatchFlagIsFalse() {
    when(smbjFileInfo.getNameInformation()).thenReturn("sambasharename" + A_SAMBA_NAME);
    String result = testee.getNameInformationFromSmbjFileInfo(smbjFileInfo, APATH_TO_AFILE, false);
    assertEquals("sambasharename\\apath\\to\\afile", result);
  }
}

package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.chemistry.ChemistryClient;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;

public class ChemistrySearchIndexInitialisorTest extends SpringTransactionalTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();

  @Mock private ChemistryClient mockChemistryClient;

  @Autowired private RSChemElementManager rsChemElementMgr;

  private ChemistrySearchIndexInitialisor chemIndexInitializor;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    chemIndexInitializor = new ChemistrySearchIndexInitialisor();
    chemIndexInitializor.setIndexOnStartup(true);
    chemIndexInitializor.setChemistryClient(mockChemistryClient);
    chemIndexInitializor.setRsChemElementMgr(rsChemElementMgr);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testOnAppStartupReinitNoReindex() throws IOException {
    User anyUser = createInitAndLoginAnyUser();
    EcatChemistryFile file = addChemistryFileToGallery("Aminoglutethimide.mol", "CCC", anyUser);
    List<RSChemElement> chemElems =
        rsChemElementMgr.getRSChemElementsLinkedToFile(file.getId(), anyUser);
    assertEquals(1, chemElems.size());
    RSChemElement createdChemElement = chemElems.get(0);
    assertEquals("CCC", createdChemElement.getSmilesString());

    // confirm that indexOnStartup == false means no reindexing
    chemIndexInitializor.setIndexOnStartup(false);
    chemIndexInitializor.onAppStartup(null);
    verify(mockChemistryClient, Mockito.never()).save(Mockito.any(), Mockito.any());

    // enable indexOnStartup, reindex again
    chemIndexInitializor.setIndexOnStartup(true);
    chemIndexInitializor.onAppStartup(null);
    verify(mockChemistryClient).save("CCC", createdChemElement.getId());
  }
}

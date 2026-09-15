package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.researchspace.dao.EcatChemistryFileDao;
import com.researchspace.dao.RSChemElementDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.testutils.SpringTransactionalTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@ExtendWith(MockitoExtension.class)
public class ChemistryImageUpdateInitialisorTest extends SpringTransactionalTest {

  @Autowired ChemistryService chemistryService;

  @Autowired EcatChemistryFileDao chemistryFileDao;

  @Mock ChemistryProvider chemistryProvider;

  @Autowired RSChemElementDao rsChemElementDao;

  @Mock FileStore fileStore;

  ChemistryImageUpdateInitialisor chemIndexInitializor;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
    chemIndexInitializor = new ChemistryImageUpdateInitialisor();
    chemIndexInitializor.setReGenerateImages(true);
    chemIndexInitializor.setChemistryService(chemistryService);
    chemIndexInitializor.setEcatChemistryFileDao(chemistryFileDao);
    chemIndexInitializor.setChemistryProvider(chemistryProvider);
    chemIndexInitializor.setRsChemElementDao(rsChemElementDao);
    chemIndexInitializor.setCompositeFileStore(fileStore);
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void imagesGetUpdated() throws Exception {
    // add a chemical element
    User anyUser = createInitAndLoginAnyUser();
    EcatChemistryFile file = addChemistryFileToGallery("Aminoglutethimide.mol", "CCC", anyUser);
    List<RSChemElement> chemElems = rsChemElementDao.getChemElementsFromChemFileId(file.getId());
    RSChemElement createdChemElement = chemElems.get(0);

    // check image is set as the default test image from addChemistryFileToGallery
    assertArrayEquals(
        createdChemElement.getDataImage(), getBase64Image().getBytes(StandardCharsets.UTF_8));

    when(chemistryProvider.exportToImage(any(), any(), any())).thenReturn(new byte[] {1, 2, 3});

    chemIndexInitializor.onAppStartup(null);

    // check image has been updated via the (mocked) call to chemistryProvider.exportToImage, as a
    // result of the initialisor running
    List<RSChemElement> chemElemsUpdate =
        rsChemElementDao.getChemElementsFromChemFileId(file.getId());
    RSChemElement updatedChemElement = chemElemsUpdate.get(0);
    assertArrayEquals(new byte[] {1, 2, 3}, updatedChemElement.getDataImage());
  }
}

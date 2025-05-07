package com.researchspace.service.impl;

import com.researchspace.dao.EcatChemistryFileDao;
import com.researchspace.dao.RSChemElementDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalExportType;
import com.researchspace.service.ChemistryService;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.chemistry.ChemistryClientException;
import com.researchspace.service.chemistry.ChemistryProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component("ChemistryImageUpdateInitialisor")
@NoArgsConstructor
@Slf4j
public class ChemistryImageUpdateInitialisor implements IApplicationInitialisor {

  @Value("${chemistry.service.reGenerateOpenSourceImages}")
  @Setter(AccessLevel.PACKAGE)
  private boolean reGenerateImages;

  @Autowired
  @Setter(AccessLevel.PACKAGE)
  ChemistryService chemistryService;

  @Autowired
  @Setter(AccessLevel.PACKAGE)
  EcatChemistryFileDao ecatChemistryFileDao;

  @Autowired
  @Setter(AccessLevel.PACKAGE)
  ChemistryProvider chemistryProvider;

  @Autowired
  @Setter(AccessLevel.PACKAGE)
  RSChemElementDao rsChemElementDao;

  @Autowired
  @Setter(AccessLevel.PACKAGE)
  FileStore compositeFileStore;

  int updatedImages = 0;

  @Override
  public void onInitialAppDeployment() {}

  @Override
  public void onAppVersionUpdate() {}

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    if (!reGenerateImages) {
      return;
    }
    log.info("running chemistry image update initialisor");

    // chemicals added as files are MOL format
    List<RSChemElement> molsToUpdate =
        chemistryService.getAllChemicalsByFormat(ChemElementsFormat.MOL);
    for (RSChemElement chemElement : molsToUpdate) {
      // oldest RSpace databases may have MOL elements without connected ecatChemistryFile
      if (chemElement.getEcatChemFileId() != null) {
        EcatChemistryFile chemistryFile = ecatChemistryFileDao.get(chemElement.getEcatChemFileId());
        updateImage(
            chemElement,
            chemistryFile.getChemString(),
            FileNameUtils.getExtension(Path.of(chemistryFile.getFileName())));
      }
    }

    // chemicals drawn in the chemical editor are in KET format
    List<RSChemElement> ketToUpdate =
        chemistryService.getAllChemicalsByFormat(ChemElementsFormat.KET);
    for (RSChemElement chemElement : ketToUpdate) {
      updateImage(chemElement, chemElement.getChemElements(), "ket");
    }

    log.info(
        "re-generated {} out of {} open-source chemistry images.",
        updatedImages,
        molsToUpdate.size() + ketToUpdate.size());
  }

  private void updateImage(
      RSChemElement chemElement, String chemFileContents, String chemFileFormat) {
    try {
      byte[] chemImage =
          chemistryProvider.exportToImage(
              chemFileContents,
              chemFileFormat,
              ChemicalExportFormat.builder()
                  .exportType(ChemicalExportType.PNG)
                  .width(1000)
                  .height(1000)
                  .build());

      if (ArrayUtils.isNotEmpty(chemImage)) {
        if (chemElement.getImageFileProperty() != null) {
          File tmpChem = File.createTempFile("chem", ".png");
          Files.write(tmpChem.toPath(), chemImage);
          compositeFileStore.save(
              chemElement.getImageFileProperty(), tmpChem, FileDuplicateStrategy.REPLACE);
        }
        chemElement.setDataImage(chemImage);
        rsChemElementDao.save(chemElement);
        updatedImages++;
      }
    } catch (IOException | ChemistryClientException e) {
      log.warn("problem re-generating the image for RSChemElement with id: " + chemElement.getId());
    }
  }
}

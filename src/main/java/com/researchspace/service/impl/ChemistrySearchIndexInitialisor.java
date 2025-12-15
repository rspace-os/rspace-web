package com.researchspace.service.impl;

import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.chemistry.ChemistryClient;
import com.researchspace.service.chemistry.ChemistryClientException;
import java.math.BigInteger;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** Performs optional clear & reindexing of chemistry data, calling chemistry client methods */
@Component("ChemistrySearchIndexInitialisor")
@NoArgsConstructor
public class ChemistrySearchIndexInitialisor implements IApplicationInitialisor {

  private Logger log = LoggerFactory.getLogger(AbstractAppInitializor.class);

  @Value("${chemistry.service.indexOnStartup}")
  @Setter(AccessLevel.PACKAGE)
  private boolean indexOnStartup;

  @Autowired
  @Setter(AccessLevel.PACKAGE)
  private ChemistryClient chemistryClient;

  @Autowired
  @Setter(AccessLevel.PACKAGE)
  private RSChemElementManager rsChemElementMgr;

  @Override
  public void onInitialAppDeployment() {}

  @Override
  public void onAppVersionUpdate() {}

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {

    if (!indexOnStartup) {
      log.info("'chemistry.service.indexOnStartup' set to false, reindexing skipped");
      return;
    }

    log.info("'chemistry.service.indexOnStartup' is set to 'true', starting reindexing");
    try {
      chemistryClient.clearSearchIndexes();
    } catch (ChemistryClientException cce) {
      log.warn("chemistry service couldn't clear search indexes, stopping the reindexing");
      return;
    }

    log.info("Old indexes cleared, moving to indexing of all saved chem elements...");
    reindexAllChemElems();
    log.info("Chemical elements re-indexing complete. Calling fast search re-indexing endpoint...");

    try {
      chemistryClient.callFastSearchIndexing();
    } catch (ChemistryClientException cce) {
      log.warn("chemistry service couldn't start fast search reindexing, continuing without");
    }
    log.info("Chemical re-indexing complete.");
  }

  private void reindexAllChemElems() {
    // a bit risky to query whole table, but maybe will get work done?
    List<Object[]> allChems = rsChemElementMgr.getAllIdAndSmilesStringPairs();
    log.info("found " + allChems.size() + " chem elements to reindex");

    int totalCount = 0;
    int successCount = 0;
    for (Object[] chemIdAndSmiles : allChems) {
      BigInteger chemId = (BigInteger) chemIdAndSmiles[0];
      String smilesString = (String) chemIdAndSmiles[1];
      if (StringUtils.isNotEmpty(smilesString)) {
        try {
          chemistryClient.save(smilesString, chemId.longValue());
          successCount++;
        } catch (ChemistryClientException cce) {
          log.warn("chem element not indexed: " + chemId);
        }
      }

      totalCount++;
      if ((totalCount % 100) == 0) {
        log.info("processed {}/{} chem elements", totalCount, allChems.size());
      }
    }

    log.info(
        "re-indexing done, successfully indexed {} out of {} chem elements",
        successCount,
        totalCount);
  }
}

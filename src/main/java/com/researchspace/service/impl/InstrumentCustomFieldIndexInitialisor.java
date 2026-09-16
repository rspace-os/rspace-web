package com.researchspace.service.impl;

import com.researchspace.search.customfield.RuntimeFieldTextSearch;
import com.researchspace.service.IApplicationInitialisor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the instrument custom-field index, which upgrading does not populate.
 *
 * <p>Values written after this runs are indexed as they are saved, so the index only has to be
 * built for content that already exists: on a first deployment there is none, and on a version
 * update there is all of it. Without this the index can never hold the rows a query would need, so
 * {@code collections.textSearch.enabled} could not honestly be turned on.
 *
 * <p>{@code collections.indexOnStartup} rebuilds it on every restart as well, for a deployment
 * recovering from a lost or corrupt index directory.
 */
@Component("InstrumentCustomFieldIndexInitialisor")
@NoArgsConstructor
@Transactional
public class InstrumentCustomFieldIndexInitialisor implements IApplicationInitialisor {

  private static final Logger log =
      LoggerFactory.getLogger(InstrumentCustomFieldIndexInitialisor.class);

  @Value("${collections.indexOnStartup}")
  @Setter(AccessLevel.PACKAGE)
  private boolean indexOnStartup;

  @Autowired
  @Setter(AccessLevel.PACKAGE)
  private RuntimeFieldTextSearch customFieldSearch;

  @Override
  public void onInitialAppDeployment() {
    reindex("initial deployment");
  }

  @Override
  public void onAppVersionUpdate() {
    reindex("version update");
  }

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    if (indexOnStartup) {
      reindex("'collections.indexOnStartup' is true");
    }
  }

  private void reindex(String reason) {
    log.info("Indexing instrument custom field values ({})", reason);
    try {
      customFieldSearch.reindexAll();
      log.info("Instrument custom field indexing complete");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Instrument custom field indexing was interrupted, continuing without it");
    } catch (RuntimeException e) {
      log.error("Instrument custom field indexing failed, continuing without it", e);
    }
  }
}

package com.researchspace.dao.customliquibaseupdates;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.auth.GlobalInitSysadminAuthenticationToken;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import liquibase.database.Database;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.ThreadContext;
import org.springframework.core.io.ClassPathResource;

/**
 * Creates the locked default (system) instrument template "Instrument (PIDINST 1.0)" owned by the
 * sysadmin, so that every RSpace deployment ships it and every user can read and duplicate it while
 * nobody can edit, delete or transfer it (RSDEV-1219). Modelled on {@link
 * UpdatingOwnerIdColumnOnDigitalObjectIdentifier_RSDEV607}: it runs once per database from the
 * Liquibase customUpdates changelog, after all schema changes have been applied.
 */
@Slf4j
public class CreateDefaultInstrumentTemplate_RSDEV1219 extends AbstractCustomLiquibaseUpdater {

  static final String TEMPLATE_NAME = "Instrument (PIDINST 1.0)";
  private static final String TEMPLATE_JSON =
      "inventory/defaultInstrumentTemplate-PIDINST-1.0.json";

  private InstrumentEntityApiManager instrumentApiMgr;
  private UserDao userDao;
  private InstrumentTemplateDao instrumentTemplateDao;
  private String resultMessage = "Default instrument template already present; no changes made";

  @Override
  protected void addBeans() {
    instrumentApiMgr = context.getBean(InstrumentEntityApiManager.class);
    userDao = context.getBean(UserDao.class);
    instrumentTemplateDao = context.getBean(InstrumentTemplateDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return resultMessage;
  }

  @Override
  protected void doExecute(Database database) {
    GlobalInitSysadminAuthenticationToken sysadminToken =
        new GlobalInitSysadminAuthenticationToken();
    User sysadmin = userDao.getUserByUsername(sysadminToken.getPrincipal().toString());
    if (sysadmin == null) {
      // Fail the migration rather than return: a silent skip would be recorded as applied in
      // DATABASECHANGELOG and never retried, permanently leaving the deployment without the
      // required default template. The sysadmin account is seeded before customUpdates runs, so a
      // null here is a genuine, unexpected error state.
      throw new IllegalStateException(
          "No sysadmin account found; cannot create the default instrument template '"
              + TEMPLATE_NAME
              + "'");
    }
    // Idempotency: custom changes run once per DB via DATABASECHANGELOG, but guard anyway for
    // re-baselined / restored databases. Only a *locked* template counts as already seeded; an
    // editable same-named template (e.g. from a prior run interrupted before the lock step) must be
    // locked rather than treated as done, or the deployment would keep an unlocked default.
    List<InstrumentTemplate> existing =
        instrumentTemplateDao.findInstrumentTemplatesByName(TEMPLATE_NAME, sysadmin);
    if (existing.stream().anyMatch(template -> !template.isEditable())) {
      log.info(
          "Locked default instrument template '{}' already exists; nothing to do", TEMPLATE_NAME);
      return;
    }

    // The create path resolves modifiedBy via IActiveUserStrategy.CHECK_OPERATE_AS, which calls
    // SecurityUtils.getSubject().isRunAs(). During the Liquibase migration phase Shiro's
    // SecurityManager is not bound yet (GlobalInitManagerImpl binds it later, on
    // ContextRefreshedEvent), so bind a minimal one to this thread for the duration of the create
    // and clean it up afterwards. createInstrumentTemplate otherwise resolves owner/createdBy/
    // modifiedBy purely from the passed user, and the audit listener uses the event's user.
    SecurityManager previousSecurityManager = ThreadContext.getSecurityManager();
    if (previousSecurityManager == null) {
      ThreadContext.bind(new DefaultSecurityManager());
    }
    try {
      InstrumentTemplate template;
      if (existing.isEmpty()) {
        ApiInstrumentTemplate created =
            instrumentApiMgr.createInstrumentTemplate(readTemplatePost(), sysadmin);
        // the create path sets isEditable=true by default; the seeder is the only writer of false
        template = instrumentTemplateDao.get(created.getId());
      } else {
        // a prior run created the template but did not lock it; finish the job by locking the
        // existing one rather than creating a duplicate
        template = existing.get(0);
        log.info("Found an unlocked default instrument template '{}'; locking it", TEMPLATE_NAME);
      }
      template.setEditable(false);
      instrumentTemplateDao.save(template);
      instrumentTemplateDao.resetDefaultTemplateOwner();

      resultMessage = "Created locked default instrument template '" + TEMPLATE_NAME + "'";
      log.info(resultMessage);
    } finally {
      if (previousSecurityManager == null) {
        ThreadContext.unbindSecurityManager();
        ThreadContext.unbindSubject();
      }
    }
  }

  private ApiInstrumentTemplatePost readTemplatePost() {
    ObjectMapper objectMapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    try (InputStream in = new ClassPathResource(TEMPLATE_JSON).getInputStream()) {
      return objectMapper.readValue(in, ApiInstrumentTemplatePost.class);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not read default instrument template resource: " + TEMPLATE_JSON, e);
    }
  }
}

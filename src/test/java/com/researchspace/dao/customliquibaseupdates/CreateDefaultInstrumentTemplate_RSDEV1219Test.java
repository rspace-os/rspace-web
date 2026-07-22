package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import java.util.List;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

/**
 * Pure unit test for the default-instrument-template seeder, exercising the edge cases raised in
 * review (RSDEV-1219): a missing sysadmin must fail loudly rather than silently skip, and a
 * pre-existing but unlocked template must be locked rather than treated as already seeded.
 */
class CreateDefaultInstrumentTemplate_RSDEV1219Test {

  private CreateDefaultInstrumentTemplate_RSDEV1219 seeder;
  private InstrumentEntityApiManager instrumentApiMgr;
  private UserDao userDao;
  private InstrumentTemplateDao instrumentTemplateDao;
  private User sysadmin;

  @BeforeEach
  void setUp() {
    ThreadContext.remove();
    instrumentApiMgr = Mockito.mock(InstrumentEntityApiManager.class);
    userDao = Mockito.mock(UserDao.class);
    instrumentTemplateDao = Mockito.mock(InstrumentTemplateDao.class);
    sysadmin = Mockito.mock(User.class);

    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    when(context.getBean(InstrumentEntityApiManager.class)).thenReturn(instrumentApiMgr);
    when(context.getBean(UserDao.class)).thenReturn(userDao);
    when(context.getBean(InstrumentTemplateDao.class)).thenReturn(instrumentTemplateDao);

    seeder = new CreateDefaultInstrumentTemplate_RSDEV1219();
    seeder.context = context; // protected field on AbstractCustomLiquibaseUpdater (same package)
    seeder.addBeans();
  }

  @AfterEach
  void tearDown() {
    ThreadContext.remove();
  }

  @Test
  @DisplayName("A missing sysadmin fails the migration instead of silently skipping the seed")
  void throwsWhenNoSysadmin() {
    when(userDao.getUserByUsername(anyString())).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> seeder.doExecute(null));
    verify(instrumentApiMgr, never()).createInstrumentTemplate(any(), any());
  }

  @Test
  @DisplayName("A locked template with the default name is treated as already seeded")
  void skipsWhenLockedTemplateAlreadyExists() {
    when(userDao.getUserByUsername(anyString())).thenReturn(sysadmin);
    InstrumentTemplate locked = Mockito.mock(InstrumentTemplate.class);
    when(locked.isEditable()).thenReturn(false);
    when(instrumentTemplateDao.findInstrumentTemplatesByName(
            CreateDefaultInstrumentTemplate_RSDEV1219.TEMPLATE_NAME, sysadmin))
        .thenReturn(List.of(locked));

    seeder.doExecute(null);

    verify(instrumentApiMgr, never()).createInstrumentTemplate(any(), any());
    verify(locked, never()).setEditable(anyBoolean());
    verify(instrumentTemplateDao, never()).save(any());
  }

  @Test
  @DisplayName(
      "An unlocked template from an interrupted prior run is locked, not skipped or duplicated")
  void locksExistingUnlockedTemplateInsteadOfSkipping() {
    when(userDao.getUserByUsername(anyString())).thenReturn(sysadmin);
    InstrumentTemplate editable = Mockito.mock(InstrumentTemplate.class);
    when(editable.isEditable()).thenReturn(true);
    when(instrumentTemplateDao.findInstrumentTemplatesByName(
            CreateDefaultInstrumentTemplate_RSDEV1219.TEMPLATE_NAME, sysadmin))
        .thenReturn(List.of(editable));

    seeder.doExecute(null);

    verify(instrumentApiMgr, never()).createInstrumentTemplate(any(), any());
    verify(editable).setEditable(false);
    verify(instrumentTemplateDao).save(editable);
    verify(instrumentTemplateDao).resetDefaultTemplateOwner();
  }

  @Test
  @DisplayName("When nothing exists the template is created and locked")
  void createsAndLocksWhenNoneExists() {
    when(userDao.getUserByUsername(anyString())).thenReturn(sysadmin);
    when(instrumentTemplateDao.findInstrumentTemplatesByName(
            CreateDefaultInstrumentTemplate_RSDEV1219.TEMPLATE_NAME, sysadmin))
        .thenReturn(List.of());
    ApiInstrumentTemplate created = Mockito.mock(ApiInstrumentTemplate.class);
    when(created.getId()).thenReturn(42L);
    when(instrumentApiMgr.createInstrumentTemplate(
            any(ApiInstrumentTemplatePost.class), eq(sysadmin)))
        .thenReturn(created);
    InstrumentTemplate saved = Mockito.mock(InstrumentTemplate.class);
    when(instrumentTemplateDao.get(42L)).thenReturn(saved);

    seeder.doExecute(null);

    verify(instrumentApiMgr)
        .createInstrumentTemplate(any(ApiInstrumentTemplatePost.class), eq(sysadmin));
    verify(saved).setEditable(false);
    verify(instrumentTemplateDao).save(saved);
    verify(instrumentTemplateDao).resetDefaultTemplateOwner();
  }
}

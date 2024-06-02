package com.researchspace.service.impl;

import static com.researchspace.service.impl.CustomFormAppInitialiser.ONTOLOGY_FORM_NAME;
import static com.researchspace.service.impl.CustomFormAppInitialiser.ONTOLOGY_PNG;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.FormDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.FormManager;
import com.researchspace.service.IconImageManager;
import com.researchspace.session.UserSessionTracker;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.commons.io.input.NullInputStream;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SubjectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.orm.ObjectRetrievalFailureException;

@ExtendWith(MockitoExtension.class)
public class CustomFormAppInitialiserTest {
  @Mock private ApplicationContext mockAppContext;
  @Mock private FormDao formDao;
  @Mock private UserDao userdao;
  @Mock private FormManager formManager;
  @Mock private SecurityManager securityManagerMock;
  @Mock private Subject subjectMock;
  @Mock private User mockUser;
  @Mock private RSForm ontologyFormMock;
  @Mock private IconImageManager iconImageManagerMock;
  @InjectMocks private CustomFormAppInitialiser testee;
  @Mock private Resource mockResource;
  @Mock private IconEntity mockIconEntiy;

  @BeforeEach
  public void initEach() throws IOException {
    InputStream is = new NullInputStream();
    SecurityUtils.setSecurityManager(securityManagerMock);
    lenient()
        .when(securityManagerMock.createSubject(any(SubjectContext.class)))
        .thenReturn(subjectMock);
    lenient().when(userdao.getUserByUserName(any(String.class))).thenReturn(mockUser);
    lenient().when(mockUser.getUsername()).thenReturn("sysadmin1");
    // simulate a pre-existing ontologies form
    lenient()
        .when(formDao.findOldestFormByNameForCreator(eq(ONTOLOGY_FORM_NAME), anyString()))
        .thenReturn(ontologyFormMock);
    lenient()
        .when(mockAppContext.getResource(eq("classpath:formIcons/" + ONTOLOGY_PNG)))
        .thenReturn(mockResource);
    lenient().when(mockResource.getInputStream()).thenReturn(is);
    lenient()
        .when(iconImageManagerMock.saveIconEntity(any(IconEntity.class), eq(true)))
        .thenReturn(mockIconEntiy);
  }

  @Test
  public void shouldCreateAFormIfDoesntExist() {
    ArgumentCaptor<RSForm> captor = ArgumentCaptor.forClass(RSForm.class);
    testee.onAppStartup(mockAppContext);
    verify(formDao).save(captor.capture());
    RSForm saved = captor.getValue();
    assertEquals(CustomFormAppInitialiser.EQUIPMENT_FORM_NAME, saved.getName());
    assertEquals(CustomFormAppInitialiser.EQUIPMENT_DESCRIPTION, saved.getDescription());
  }

  @Test
  public void shouldCreateOntologyFormIfDoesntExist() {
    // simulate no pre-existing ontologies form
    lenient()
        .when(formDao.findOldestFormByNameForCreator(eq(ONTOLOGY_FORM_NAME), anyString()))
        .thenReturn(null);
    ArgumentCaptor<RSForm> captor = ArgumentCaptor.forClass(RSForm.class);
    testee.onAppStartup(mockAppContext);
    verify(formDao, times(3)).save(captor.capture());
    List<RSForm> saved = captor.getAllValues();
    int count = 0;
    for (RSForm form : saved) {
      if (form.getName().equals(ONTOLOGY_FORM_NAME)) {
        count++;
      }
    }
    if (count == 2) { // saves form then saves again after setting icon
      return;
    }
    fail("No Ontology form saved to DB on App startup");
  }

  @Test
  public void shouldNotCreateAFormIfAlreadyExistsWithSameVersion() {
    RSForm existingForm =
        testee.createTransientEquipmentForm(
            "Equipment", "A generic equipment description", mockUser);
    when(formDao.findOldestFormByNameForCreator(
            eq(CustomFormAppInitialiser.EQUIPMENT_FORM_NAME), eq("sysadmin1")))
        .thenReturn(existingForm);
    testee.onAppStartup(mockAppContext);
    verify(formDao, never()).save(any(RSForm.class));
  }

  @Test
  public void shouldUpdateAnExistingFormIfVersionIncremented() {
    RSForm existingFormInDB =
        testee.createTransientEquipmentForm(
            "Equipment", "A generic equipment description", mockUser);
    // faking the situation that there is an existing form with a version lower than the one set
    // CustomFormAppInitialiser's code.
    existingFormInDB.setVersion(new Version(-1));
    existingFormInDB.setId(1L);
    when(formManager.getForEditing(any(Long.class), any(User.class), any(UserSessionTracker.class)))
        .thenReturn(existingFormInDB);
    when(formDao.findOldestFormByNameForCreator(
            eq(CustomFormAppInitialiser.EQUIPMENT_FORM_NAME), eq("sysadmin1")))
        .thenReturn(existingFormInDB);
    testee.onAppStartup(mockAppContext);
    verify(formDao).save(any(RSForm.class));
  }

  @Test
  public void shouldNotAttemptToCreateFormIfSysadminUserDoesNotExist() {
    doThrow(new ObjectRetrievalFailureException(User.class, "sysadmin1"))
        .when(userdao)
        .getUserByUserName(eq("sysadmin1"));
    testee.onAppStartup(mockAppContext);
    verify(formDao, never()).save(any(RSForm.class));
  }
}

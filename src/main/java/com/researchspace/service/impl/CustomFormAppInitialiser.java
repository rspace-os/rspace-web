package com.researchspace.service.impl;

import com.researchspace.auth.GlobalInitSysadminAuthenticationToken;
import com.researchspace.dao.FormDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.core.UniquelyIdentifiable;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.FormManager;
import com.researchspace.service.IconImageManager;
import com.researchspace.session.UserSessionTracker;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.orm.ObjectRetrievalFailureException;

/**
 * Currently creates a custom equipment form as sysadmin and publishes for all users. This class by
 * default creates the form ONCE. If we subsequently update the version of the form produced by
 * createTransientEquipmentForm, then the existing form is updated (and its saved version is
 * incremented).
 *
 * <p>Also creates an ontologies form as sysadmin and publishes this
 */
@Slf4j
public class CustomFormAppInitialiser extends AbstractAppInitializor {
  public static final String EQUIPMENT_FORM_NAME = "RSpace_Equipment_Form";
  public static final String EQUIPMENT_DESCRIPTION = "A generic equipment description";
  public static final String ONTOLOGY_DESCRIPTION =
      "A generic form for Ontologies to be used for Tags";
  // INCREMENT this by 1 for a release which will update the existing equipment form
  private static final Version CURRENT_VERSION = new Version(0);
  public static final String ONTOLOGY_FORM_NAME = "RSpace Tags from Ontologies";
  public static final String ONTOLOGY_PNG = "Ontology.png";
  @Autowired private FormDao formDao;
  @Autowired private UserDao userdao;
  @Autowired private FormManager formManager;
  private Subject subject;
  @Autowired private IconImageManager iconMgr;
  @Autowired ApplicationContext appContext;

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    try {
      subject = SecurityUtils.getSubject();
      try {
        User sysadmin1 = userdao.getUserByUserName("sysadmin1");
      } catch (ObjectRetrievalFailureException e) {
        log.info("Sysadmin1 user does not exist so custom form not created");
        return;
      }
      User sysAdmin = loginAsSysAdmin();
      RSForm alreadyCreated =
          formDao.findOldestFormByNameForCreator(EQUIPMENT_FORM_NAME, sysAdmin.getUsername());
      if (alreadyCreated != null) {
        RSForm withUpdatedFields =
            createTransientEquipmentForm(alreadyCreated.getName(), EQUIPMENT_DESCRIPTION, sysAdmin);
        if (formsAreDifferent(alreadyCreated, withUpdatedFields)) {
          RSForm toEdit =
              formManager.getForEditing(alreadyCreated.getId(), sysAdmin, new UserSessionTracker());
          toEdit.setDescription(withUpdatedFields.getDescription());
          toEdit.setName(withUpdatedFields.getName());
          for (FieldForm ff : new ArrayList<>(toEdit.getFieldForms())) {
            formManager.deleteFieldFromForm(ff.getId(), sysAdmin);
            toEdit.removeFieldForm(ff);
          }
          for (FieldForm ff : withUpdatedFields.getFieldForms()) {
            toEdit.addFieldForm(ff);
          }
          formDao.save(toEdit);
          formManager.updateVersion(toEdit.getId(), sysAdmin);
          log.info(
              "Updated "
                  + EQUIPMENT_FORM_NAME
                  + " to version "
                  + CURRENT_VERSION.getVersion()
                  + " for sysadmin1 user and shared globally");
        }
      } else {
        RSForm equipmentForm =
            createTransientEquipmentForm(EQUIPMENT_FORM_NAME, EQUIPMENT_DESCRIPTION, sysAdmin);
        equipmentForm.getAccessControl().setWorldPermissionType(PermissionType.READ);
        formDao.save(equipmentForm);
        log.info("Created " + EQUIPMENT_FORM_NAME + " for syadmin1 user and shared globally");
      }
      RSForm ontologiesAlreadyCreated =
          formDao.findOldestFormByNameForCreator(ONTOLOGY_FORM_NAME, sysAdmin.getUsername());
      if (ontologiesAlreadyCreated == null) {
        RSForm ontologiesForm =
            createOntologiesForm(ONTOLOGY_FORM_NAME, ONTOLOGY_DESCRIPTION, sysAdmin);
        formDao.save(ontologiesForm);
        try {
          createAndSaveIconEntity(ONTOLOGY_PNG, ontologiesForm);
        } catch (IOException e) {
          log.error("error loading default icons", e);
        }
        log.info("Created " + ONTOLOGY_FORM_NAME + " for syadmin1 user and shared globally");
      }
    } finally {
      subject.logout();
    }
  }

  private IconEntity createAndSaveIconEntity(String fileName, RSForm form) throws IOException {
    IconEntity ice = saveIconEntity(fileName, form);
    log.info("saved icon " + ice.getId() + " for form " + form.getName());
    form.setIconId(ice.getId());
    formDao.save(form);
    return ice;
  }

  private IconEntity saveIconEntity(String fileName, UniquelyIdentifiable associatedEntity)
      throws IOException {
    Resource resource = appContext.getResource("classpath:formIcons/" + fileName);
    InputStream is = resource.getInputStream();
    byte[] bytes = IOUtils.toByteArray(is);
    IconEntity ice = new IconEntity();
    ice.setId(null);
    ice.setImgType("png");
    ice.setIconImage(bytes);
    String imageName = fileName;
    ice.setImgName(imageName);
    ice.setParentId(associatedEntity.getId());
    ice = iconMgr.saveIconEntity(ice, associatedEntity instanceof RSForm);
    return ice;
  }

  // compares using < instead of != therefore is a dev should accidentally decrement the
  // CURRENT_VERSION field, will return false
  private boolean formsAreDifferent(RSForm existing, RSForm newForm) {
    return existing.getVersion().getVersion() < newForm.getVersion().getVersion();
  }

  private User loginAsSysAdmin() {
    GlobalInitSysadminAuthenticationToken sysAdminToken =
        new GlobalInitSysadminAuthenticationToken();
    final User sysAdmin = userdao.getUserByUserName(sysAdminToken.getPrincipal().toString());
    subject.login(sysAdminToken);
    return sysAdmin;
  }

  public RSForm createOntologiesForm(String formName, String desc, User creator) {
    RSForm form = new RSForm(formName, desc, creator);
    form.setCurrent(true);
    int colindx = 1;
    for (int i = 0; i < 20; i++) {
      FieldForm fld = new TextFieldForm("Ontologies for tag creation");
      fld.setColumnIndex(1 + i);
      form.addFieldForm(fld);
    }
    form.setPublishingState(FormState.PUBLISHED);
    // in order to update an existing form, change the value of the CURRENT_VERSION field,
    // incrementing by 1
    form.setVersion(CURRENT_VERSION);
    form.getAccessControl().setWorldPermissionType(PermissionType.READ);
    return form;
  }

  protected RSForm createTransientEquipmentForm(String formName, String desc, User creator) {
    RSForm form = new RSForm(formName, desc, creator);
    form.setCurrent(true);
    String[] textFieldNames =
        new String[] {"Equipment Description", "Equipment Operational notes", "Equipment Bookings"};
    int colindx = 1;
    StringFieldForm name = new StringFieldForm("Equipment name");
    name.setColumnIndex(colindx++);
    form.addFieldForm(name);
    StringFieldForm category = new StringFieldForm("Equipment category");
    category.setColumnIndex(colindx++);
    form.addFieldForm(category);
    for (String fName : textFieldNames) {
      FieldForm fld = new TextFieldForm(fName);
      fld.setColumnIndex(colindx++);
      form.addFieldForm(fld);
    }
    form.setPublishingState(FormState.PUBLISHED);
    // in order to update an existing form, change the value of the CURRENT_VERSION field,
    // incrementing by 1
    form.setVersion(CURRENT_VERSION);
    return form;
  }
}

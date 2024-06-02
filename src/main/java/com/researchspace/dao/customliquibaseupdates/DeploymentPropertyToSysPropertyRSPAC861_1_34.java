package com.researchspace.dao.customliquibaseupdates;

import static org.apache.commons.lang.StringUtils.isEmpty;

import com.researchspace.dao.SystemPropertyDao;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import liquibase.database.Database;
import org.hibernate.Session;

public class DeploymentPropertyToSysPropertyRSPAC861_1_34 extends AbstractCustomLiquibaseUpdater {

  private SystemPropertyDao dao;

  private SysPropertyProviderRSPAC861 propertyProvider;

  @Override
  protected void addBeans() {
    dao = context.getBean("systemPropertyDao", SystemPropertyDao.class);
    propertyProvider =
        context.getBean("SysPropertyProviderRSPAC861", SysPropertyProviderRSPAC861.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Properties updated";
  }

  @Override
  protected void doExecute(Database database) {
    transfer("ecat.available", propertyProvider.getEcatEnabled());
    transfer("box.available", propertyProvider.getBoxEnabled());
    transfer("dropbox.available", propertyProvider.getDropboxEnabled());
    transfer("googledrive.available", propertyProvider.getGoogleDriveEnabled());
    transfer("onedrive.available", propertyProvider.getOneDriveEnabled());
    transfer("mendeley.available", propertyProvider.getMendeleyEnabled());
    transfer("chemistry.available", "");
    transfer("ecat.defaultServer", "");
  }

  SystemPropertyValue getProperty(String systemPropertyName) {
    return (SystemPropertyValue)
        sessionFactory
            .getCurrentSession()
            .createQuery("from SystemPropertyValue spv where spv.property.name=:systemPropertyName")
            .setString("systemPropertyName", systemPropertyName)
            .uniqueResult();
  }

  SystemProperty getPropertyFromVal(String systemPropertyName) {
    Session session = sessionFactory.getCurrentSession();
    SystemProperty result =
        (SystemProperty)
            session
                .createQuery("from SystemProperty spv where spv.name=:systemPropertyName")
                .setString("systemPropertyName", systemPropertyName)
                .uniqueResult();
    return result;
  }

  private void transfer(String depName, String valueFromDeploymentPropertyFile) {
    SystemPropertyValue spv = getProperty(depName);
    if (spv == null) {
      infoSetting(depName);
    } else {
      infoAlreadySet(depName, spv);
      return;
    }
    SystemProperty prop = getPropertyFromVal(depName);
    if (prop == null) {
      errorPropertyNotFound(depName);
      return;
    }
    if (isEmpty(valueFromDeploymentPropertyFile) || isNotSet(valueFromDeploymentPropertyFile)) {
      logger.info(
          "There is   file-based deployment property  for {} set ({}), setting default value {}  ",
          depName,
          valueFromDeploymentPropertyFile,
          prop.getDefaultValue());
      valueFromDeploymentPropertyFile = prop.getDefaultValue();
    }
    spv = new SystemPropertyValue(prop, valueFromDeploymentPropertyFile);
    spv = dao.save(spv);
    logger.info("System property {} successfully set to {} in DB", depName, spv.getValue());
  }

  private boolean isNotSet(String valueFromDeploymentPropertyFile) {
    return valueFromDeploymentPropertyFile.startsWith("${");
  }

  private void errorPropertyNotFound(String depName) {
    logger.error("Property {} should be preloaded in DB, but wasn't!! Skipping", depName);
  }

  private void infoAlreadySet(String depName, SystemPropertyValue spv) {
    logger.info("Value set for {}, is {}", depName, spv.getValue());
  }

  private void infoSetting(String depName) {
    logger.info("No value set for {}, setting now..", depName);
  }
}

package com.researchspace.service.impl;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;

public class ApiAvailabilityHandlerImpl implements ApiAvailabilityHandler {

  @Autowired private SystemPropertyPermissionManager systemPropertyManager;
  @Autowired private DataCiteConnector dataCiteConnector;
  @Autowired private B2instConnector b2instConnector;
  @Autowired private MessageSourceUtils messages;

  private static final ServiceOperationResult<String> enabledResult =
      new ServiceOperationResult<>("Enabled", true);
  private static final ServiceOperationResult<String> apiDisabledResult =
      new ServiceOperationResult<>(
          "Access to all API has been disabled by your administrator", false);
  private static final ServiceOperationResult<String> invDisabledResult =
      new ServiceOperationResult<>(
          "Access to Inventory has been disabled by your administrator", false);

  void setSystemPropertyManager(SystemPropertyPermissionManager systemPropertyManager) {
    this.systemPropertyManager = systemPropertyManager;
  }

  void setMessages(MessageSourceUtils messages) {
    this.messages = messages;
  }

  @Override
  public void setDataCiteConnector(DataCiteConnector dataciteConnector) {
    this.dataCiteConnector = dataciteConnector;
  }

  void setB2instConnector(B2instConnector b2instConnector) {
    this.b2instConnector = b2instConnector;
  }

  @Override
  public ServiceOperationResult<String> isAvailable(User user, HttpServletRequest request) {
    if (!isApiAvailableForUser(user)) {
      return apiDisabledResult;
    }
    if (isInventoryRequest(request)) {
      if (isInventoryAvailable(user)) {
        return enabledResult;
      } else {
        return invDisabledResult;
      }
    }
    return enabledResult;
  }

  @Override
  public boolean isApiAvailableForUser(User user) {
    return systemPropertyManager.isPropertyAllowed(user, SystemPropertyName.API_AVAILABLE);
  }

  @Override
  public boolean isOAuthAccessAllowed(User user) {
    return systemPropertyManager.isPropertyAllowed(
        user, SystemPropertyName.API_OAUTH_AUTHENTICATION);
  }

  @Override
  public void assertInventoryAndDataciteEnabled(User user) {
    assertInventoryAndIdentifierTypeEnabled(user, InventorySettingType.IGSN);
  }

  @Override
  public boolean isInventoryAndDataciteEnabled(User user) {
    return isInventoryAndIdentifierTypeEnabled(user, InventorySettingType.IGSN);
  }

  @Override
  public void assertInventoryAndIdentifierTypeEnabled(User user, InventorySettingType settingType) {
    assertInventoryAvailable(user);
    assertIdentifierConnectorEnabled(settingType);
  }

  @Override
  public boolean isInventoryAndIdentifierTypeEnabled(User user, InventorySettingType settingType) {
    return isInventoryAvailable(user) && isIdentifierConnectorEnabled(settingType);
  }

  private boolean isIdentifierConnectorEnabled(InventorySettingType settingType) {
    if (InventorySettingType.PIDINST.equals(settingType)
        && b2instConnector != null
        && b2instConnector.isConfiguredAndEnabled()) {
      return true;
    }
    return dataCiteConnector != null
        && dataCiteConnector.isDataCiteConfiguredAndEnabled(settingType);
  }

  private void assertIdentifierConnectorEnabled(InventorySettingType settingType) {
    if (!isIdentifierConnectorEnabled(settingType)) {
      String integrationName = InventorySettingType.IGSN.equals(settingType) ? "IGSN" : "PIDINST";
      throw new UnsupportedOperationException(
          messages.getMessage(
              "errors.inventory.identifier.integrationNotEnabled", new Object[] {integrationName}));
    }
  }

  private void assertInventoryAvailable(User user) {
    if (!isInventoryAvailable(user)) {
      throw new UnsupportedOperationException(messages.getMessage("errors.inventory.notEnabled"));
    }
  }

  @Override
  public boolean isDataCiteConnectorEnabled() {
    return dataCiteConnector != null && dataCiteConnector.isDataCiteConfiguredAndEnabled();
  }

  @Override
  public boolean isInventoryAvailable(User user) {
    return systemPropertyManager.isPropertyAllowed(user, SystemPropertyName.INVENTORY_AVAILABLE);
  }

  private boolean isInventoryRequest(HttpServletRequest request) {
    return request != null && request.getRequestURL().toString().contains("inventory/v1");
  }
}

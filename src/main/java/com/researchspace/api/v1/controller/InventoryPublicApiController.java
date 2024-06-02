package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InventoryPublicApi;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import javax.ws.rs.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
public class InventoryPublicApiController extends BaseApiInventoryController
    implements InventoryPublicApi {

  @Autowired private InventoryIdentifierApiManager identifierMgr;

  @Override
  public ApiInventoryRecordInfo getPublicViewOfInventoryItem(
      @PathVariable String publicLink,
      @RequestAttribute(name = "user", required = false) User user) {

    ApiInventoryRecordInfo fullRec = identifierMgr.findPublishedItemVersionByPublicLink(publicLink);
    if (fullRec == null) {
      throw new NotFoundException(messages.getMessage("inventory.item.not.public"));
    }

    return getRecordCopyLimitedToPublishedDetails(fullRec);
  }

  protected ApiInventoryRecordInfo getRecordCopyLimitedToPublishedDetails(
      ApiInventoryRecordInfo orgRecord) {

    ApiInventoryRecordInfo copy = orgRecord.getEmptyCopy();
    // identifier details are public in all cases
    copy.setIdentifiers(orgRecord.getIdentifiers());

    // more details available if 'customFieldsOnPublicPage' is true
    boolean withCustomFields = orgRecord.getIdentifiers().get(0).getCustomFieldsOnPublicPage();
    if (withCustomFields) {
      copy.setDescription(orgRecord.getDescription());
      copy.setTags(orgRecord.getTags());
      copy.getExtraFields().addAll(orgRecord.getExtraFields());

      if (orgRecord.getType().equals(ApiInventoryRecordType.SAMPLE)
          || orgRecord.getType().equals(ApiInventoryRecordType.SAMPLE_TEMPLATE)) {
        ApiSample sample = (ApiSample) orgRecord;
        ((ApiSample) copy).setFields(sample.getFields());
      }
    }

    return copy;
  }
}

package com.researchspace.linkedelements;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.RecordInformation;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;

@Slf4j
class LinkedRecordConverter extends AbstractFieldElementConverter implements FieldElementConverter {

  public Optional<RecordInformation> jsoup2LinkableElement(FieldContents contents, Element el) {
    if (!el.hasAttr("id")) {
      log.warn("Linked record element does not have an ID attribute");
      return Optional.empty();
    }
    long id = Long.parseLong(el.attr("id"));
    RecordInformation recordInformation = new RecordInformation();
    recordInformation.setId(id);
    recordInformation.setName(el.attr("data-name"));
    String globalIdFromData = el.attr("data-globalid");
    if (StringUtils.isEmpty(globalIdFromData)) {
      // pre 1.47 links don't have data-globalid
      recordInformation.setOid(new GlobalIdentifier(GlobalIdPrefix.SD.toString() + id));
    } else {
      recordInformation.setOid(new GlobalIdentifier(globalIdFromData));
    }
    contents.addElement(recordInformation, el.attr("href"), RecordInformation.class);
    return Optional.of(recordInformation);
  }
}

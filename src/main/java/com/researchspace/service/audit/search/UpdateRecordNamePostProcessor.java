package com.researchspace.service.audit.search;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.service.RecordManager;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class UpdateRecordNamePostProcessor implements IAuditSearchResultPostProcessor {

  private RecordManager recMAnager;

  @Autowired
  public void setRecordManager(RecordManager recMgr) {
    this.recMAnager = recMgr;
  }

  @Override
  public void process(ISearchResults<AuditTrailSearchResult> searchResult) {

    Map<Long, AuditTrailSearchResult> ids = new TreeMap<Long, AuditTrailSearchResult>();
    // pass over search results to identify ames to change
    for (AuditTrailSearchResult result : searchResult.getResults()) {
      HistoricData data = result.getEvent();

      Object oid = data.getData().getData().get("id");
      if (oid == null
          || StringUtils.isBlank(oid.toString())
          || !GlobalIdentifier.isValid(oid.toString())) {
        continue;
      }
      GlobalIdentifier gidObj = new GlobalIdentifier(oid.toString());
      if (isRecord(gidObj) && data.getAction().equals(AuditAction.CREATE)) {
        ids.put(gidObj.getDbId(), result);
      }
    }

    if (!ids.isEmpty()) {
      List<RSpaceDocView> records = recMAnager.getAllFrom(ids.keySet());
      for (RSpaceDocView br : records) {
        ids.get(br.getId()).getEvent().getData().getData().put("name", br.getName());
      }
    }
  }

  private boolean isRecord(GlobalIdentifier gid) {
    return GlobalIdPrefix.SD.equals(gid.getPrefix())
        || GlobalIdPrefix.FL.equals(gid.getPrefix())
        || GlobalIdPrefix.GF.equals(gid.getPrefix())
        || GlobalIdPrefix.NB.equals(gid.getPrefix());
  }
}

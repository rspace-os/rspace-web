package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;

public interface InventoryApiManager {

  void setPreviewImageForInvRecord(InventoryRecord invRecord, String base64Image, User user)
      throws IOException;

  InventoryFile saveAttachment(
      GlobalIdentifier parentOid, String originalFileName, InputStream inputStream, User user)
      throws IOException;

  void setOtherFieldsForOutgoingApiInventoryRecord(
      ApiInventoryRecordInfo recordInfo, InventoryRecord invRec, User user);

  ApiInventorySearchResult sortRepaginateConvertToApiInventorySearchResult(
      PaginationCriteria<InventoryRecord> pgCrit,
      List<? extends InventoryRecord> dbRecords,
      User user);

  /**
   * Converts list of found db items into inventory search result.
   *
   * @param totalHits
   * @param pageNumber
   * @param dbRecords
   * @param user current user
   * @return
   */
  ApiInventorySearchResult convertToApiInventorySearchResult(
      Long totalHits, Integer pageNumber, List<? extends InventoryRecord> dbRecords, User user);

  /*
   * ==============
   * for testing
   * ==============
   */
  void setPublisher(ApplicationEventPublisher publisher);
}

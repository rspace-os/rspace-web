package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Barcode;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.List;
import java.util.Optional;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

/**
 * To deal with API barcodes conversion when barcodes are sent between RSpace server and API client.
 */
@Component
public class ApiBarcodesHelper {

  public boolean createDeleteRequestedBarcodes(
      List<ApiBarcode> incomingBarcodes, InventoryRecord parentInvRec, User user) {

    boolean changed = false;
    if (!CollectionUtils.isEmpty(incomingBarcodes)) {
      for (ApiBarcode apiBarcode : incomingBarcodes) {
        if (apiBarcode.isNewBarcodeRequest()) {
          addRecordBarcodeForIncomingApiBarcode(apiBarcode, user, parentInvRec);
          changed = true;
        }
        if (apiBarcode.isDeleteBarcodeRequest()) {
          if (apiBarcode.getId() == null) {
            throw new IllegalArgumentException(
                "'id' property not provided " + "for a barcode with 'deleteFieldRequest' flag");
          }
          Optional<Barcode> dbBarcodeOpt =
              parentInvRec.getActiveBarcodes().stream()
                  .filter(barcode -> apiBarcode.getId().equals(barcode.getId()))
                  .findFirst();
          if (!dbBarcodeOpt.isPresent()) {
            throw new IllegalArgumentException(
                "Barcode id: "
                    + apiBarcode.getId()
                    + " doesn't match id of any pre-existing barcode");
          }
          dbBarcodeOpt.get().setDeleted(true);
          changed = true;
        }
      }
    }
    if (changed) {
      parentInvRec.refreshActiveBarcodes();
    }
    return changed;
  }

  private void addRecordBarcodeForIncomingApiBarcode(
      ApiBarcode apiBarcode, User user, InventoryRecord parentInvRec) {
    Barcode newBarcode = new Barcode(apiBarcode.getData(), user.getUsername());
    newBarcode.setFormat(apiBarcode.getFormat());
    newBarcode.setDescription(apiBarcode.getDescription());
    parentInvRec.addBarcode(newBarcode); // update parent's barcode list
  }
}

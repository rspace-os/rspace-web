/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiBarcodeRequest;
import com.researchspace.model.User;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/inventory/v1/barcodes")
public interface BarcodesApi {

  @GetMapping
  void generateBarcode(
      @Valid ApiBarcodeRequest barcodeRequest,
      BindingResult errors,
      User user,
      HttpServletResponse response)
      throws Exception;
}

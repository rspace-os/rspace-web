package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.BarcodesApi;
import com.researchspace.api.v1.model.ApiBarcodeRequest;
import com.researchspace.core.util.imageutils.BarcodeUtils;
import com.researchspace.model.User;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
public class BarcodesApiController extends BaseApiInventoryController implements BarcodesApi {

  @Override
  public void generateBarcode(
      @Valid ApiBarcodeRequest barcodeRequest,
      BindingResult errors,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws Exception {

    throwBindExceptionIfErrors(errors);

    String requestedCodeType = "BARCODE";
    if (barcodeRequest.getBarcodeType() != null) {
      requestedCodeType = barcodeRequest.getBarcodeType();
    }

    BufferedImage codeImage = null;
    if (requestedCodeType.equals("BARCODE")) {
      codeImage =
          BarcodeUtils.generateBarcodeImage(
              barcodeRequest.getContent(),
              barcodeRequest.getImageWidth(),
              barcodeRequest.getImageHeight());
    } else if (requestedCodeType.equals("QR")) {
      codeImage =
          BarcodeUtils.generateQRCodeImage(
              barcodeRequest.getContent(),
              barcodeRequest.getImageWidth(),
              barcodeRequest.getImageHeight());
    } else {
      throw new IllegalArgumentException("unsupported code type: " + requestedCodeType);
    }
    response.setContentType("image/png");
    ImageIO.write(codeImage, "png", response.getOutputStream());
  }
}

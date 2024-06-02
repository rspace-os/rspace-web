/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API representation of a barcode request */
@Data
@NoArgsConstructor
public class ApiBarcodeRequest {

  @NotNull(message = "Content parameter is required")
  private String content;

  @Pattern(
      regexp = "(BARCODE)|(QR)",
      message = "Supported barcodeType values are: 'BARCODE' or 'QR'")
  private String barcodeType;

  @Min(value = 0, message = "Requested width cannot be less than zero")
  private Integer imageWidth;

  @Min(value = 0, message = "Requested height cannot be less then zero")
  private Integer imageHeight;
}

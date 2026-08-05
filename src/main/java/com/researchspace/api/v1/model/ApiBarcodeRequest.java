/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API representation of a barcode request */
@Data
@NoArgsConstructor
public class ApiBarcodeRequest {

  @NotNull(message = "{errors.inventory.barcode.contentRequired}")
  private String content;

  @Pattern(regexp = "(BARCODE)|(QR)", message = "{errors.inventory.barcode.type.invalid}")
  private String barcodeType;

  @Min(value = 0, message = "{errors.inventory.barcode.widthNonNegative}")
  private Integer imageWidth;

  @Min(value = 0, message = "{errors.inventory.barcode.heightNonNegative}")
  private Integer imageHeight;
}

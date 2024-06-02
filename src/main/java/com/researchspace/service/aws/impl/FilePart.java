package com.researchspace.service.aws.impl;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jsoup.helper.Validate;

@Data
@NoArgsConstructor
public class FilePart {
  public FilePart(Long startOffset, Long endOffset, Integer partNo) {
    Validate.isTrue(endOffset > startOffset, "EndOffset must be greated than startOffset");
    Validate.isTrue(endOffset > 0 && startOffset >= 0, "Invalid off set values");
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.partNo = partNo;
  }

  private Long startOffset;
  private Long endOffset;
  private Integer partNo;
  private Supplier<Optional<ByteBuffer>> byteBufferSupplier;
}

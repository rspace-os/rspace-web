package com.researchspace.service.aws.impl;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Splits a file into chunks and returns a series of chunk ranges. <br>
 * getParts() returns a supplier to retrieve the actual bytes
 */
public class FileChunker {

  private File toSplit;
  private Long blockSize;
  private List<FilePart> parts = new ArrayList<>();

  public FileChunker(File toSplit, Long blockSize) {
    this.toSplit = toSplit;
    this.blockSize = blockSize;
  }

  class ByteBufferChunkSupplier implements Supplier<Optional<ByteBuffer>> {
    private FilePart part;

    public ByteBufferChunkSupplier(FilePart part) {
      super();
      this.part = part;
    }

    @Override
    public Optional<ByteBuffer> get() {
      int start = part.getStartOffset().intValue();
      int end = part.getEndOffset().intValue();

      int sizeOfFiles = end - start;
      byte[] buffer = new byte[sizeOfFiles];

      try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(toSplit))) {
        bis.skip(start);
        int tmp = -1;
        tmp = bis.read(buffer);
        byte[] out = new byte[tmp];
        try (ByteArrayOutputStream outBaos = new ByteArrayOutputStream()) {
          outBaos.write(out, 0, tmp); // tmp is chunk size
        }
      } catch (IOException e) {
        return Optional.empty();
      }
      return Optional.of(ByteBuffer.wrap(buffer));
    }
  }

  List<FilePart> getParts() {
    this.parts = createParts();
    List<FilePart> rc = new ArrayList<>();
    for (FilePart part : this.parts) {
      part.setByteBufferSupplier(new ByteBufferChunkSupplier(part));
      rc.add(part);
    }
    return rc;
  }

  int getNumParts() {
    if (this.parts.isEmpty()) {
      return getParts().size();
    }
    return this.parts.size();
  }

  List<FilePart> createParts() {
    List<FilePart> rc = new ArrayList<>();
    if (toSplit.length() < blockSize) {
      rc.add(new FilePart(0L, toSplit.length(), 1));
    } else {
      int partNumber = 1;
      for (int i = 0; i < toSplit.length(); i += blockSize) {
        long end = i + blockSize;
        if (i + blockSize > toSplit.length()) {
          end = toSplit.length();
        }
        rc.add(new FilePart(Long.valueOf(i), end, partNumber));
        partNumber++;
      }
    }
    return rc;
  }
}

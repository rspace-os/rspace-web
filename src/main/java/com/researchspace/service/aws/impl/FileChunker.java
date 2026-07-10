package com.researchspace.service.aws.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Splits a file into chunks and returns a series of chunk ranges. <br>
 * getParts() returns a supplier to retrieve the actual bytes
 */
@Slf4j
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
      long start = part.getStartOffset();
      long end = part.getEndOffset();
      long size = end - start;
      // The whole part is buffered in memory, so its length must fit in a byte[] (int-indexed).
      // Part length is the configured chunk size (MB scale by default), but a single part can span
      // the whole file when the file is smaller than the chunk size, so guard against a chunk size
      // configured above 2GB rather than trusting the invariant.
      if (size > Integer.MAX_VALUE) {
        log.error(
            "Cannot buffer part [{}, {}) of {}: length {} bytes exceeds the maximum in-memory "
                + "buffer size; reduce the configured chunk size.",
            start,
            end,
            toSplit,
            size);
        return Optional.empty();
      }
      byte[] buffer = new byte[(int) size];

      try (RandomAccessFile raf = new RandomAccessFile(toSplit, "r")) {
        raf.seek(start);
        raf.readFully(buffer);
      } catch (IOException e) {
        log.error("Failed to read part [{}, {}) of {}", start, end, toSplit, e);
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
      for (long i = 0; i < toSplit.length(); i += blockSize) {
        long end = i + blockSize;
        if (i + blockSize > toSplit.length()) {
          end = toSplit.length();
        }
        rc.add(new FilePart(i, end, partNumber));
        partNumber++;
      }
    }
    return rc;
  }
}

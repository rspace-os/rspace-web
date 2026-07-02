package com.researchspace.service.aws.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
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
      long start = part.getStartOffset();
      long end = part.getEndOffset();
      // A single part is chunk-sized (MB scale), so its length fits in an int even for
      // multi-GB files; only the absolute offset needs to stay a long.
      int size = Math.toIntExact(end - start);
      byte[] buffer = new byte[size];

      try (RandomAccessFile raf = new RandomAccessFile(toSplit, "r")) {
        raf.seek(start);
        raf.readFully(buffer);
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

package com.researchspace.service.aws.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class FileChunkerTest {
  FileChunker fileChunker = null;

  // size = 136575
  File toSplit = RSpaceTestUtils.getResource("a2.zip");

  @ParameterizedTest
  @ValueSource(longs = {10, 100, 1000, 10000, 100000, 1_000_000})
  public void createParts(Long chunk) throws IOException {
    fileChunker = new FileChunker(toSplit, chunk);
    assertEquals((toSplit.length() / chunk) + 1, fileChunker.getNumParts());
    List<FilePart> parts = fileChunker.createParts();
    assertEquals((toSplit.length() / chunk) + 1, parts.size());
  }

  @Test
  public void createPartsOf1() throws IOException {
    fileChunker = new FileChunker(toSplit, 1L);
    assertEquals(toSplit.length(), fileChunker.getNumParts());
    List<FilePart> parts = fileChunker.createParts();
    assertEquals(toSplit.length(), parts.size());
  }

  @Test
  public void splitFile() throws IOException {
    File assembledFromChunksTmp = File.createTempFile("fromChunks", ".zip");

    byte[] fromFile = FileUtils.readFileToByteArray(toSplit);
    fileChunker = new FileChunker(toSplit, 10L);
    try (FileOutputStream outputFos = new FileOutputStream(assembledFromChunksTmp, true); ) {
      for (FilePart part : fileChunker.getParts()) {
        ByteBuffer bb = part.getByteBufferSupplier().get().get();
        IOUtils.write(bb.array(), outputFos);
      }
    }
    // assert bytes are equal in original and rebuilt-from-chunks
    byte[] fromChunked = FileUtils.readFileToByteArray(assembledFromChunksTmp);
    Assertions.assertArrayEquals(fromFile, fromChunked);
  }
}

package com.researchspace.zipprocessing;

import java.util.zip.ZipEntry;

/**
 * Processes a single {@link ZipEntry}. See {@link ZipEntryFileProcessor} for the variant that
 * receives the entry extracted to a file.
 */
@FunctionalInterface
public interface ZipEntryProcessor {

  void process(ZipEntry entry);
}

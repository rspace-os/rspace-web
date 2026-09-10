package com.researchspace.core.util;

import org.apache.commons.io.FileUtils;

/**
 * Bounds applied while extracting an archive with {@link ZipUtils#extractZip(java.io.File,
 * java.io.File, ZipExtractionLimits)}, so a crafted archive cannot fill the disk or burn CPU
 * inflating itself.
 *
 * <p>All byte counts are measured on bytes actually written, never on sizes the archive claims. A
 * value of zero for {@code maxExpandedBytes}, {@code maxEntries}, {@code minFreeDiskBytes} or
 * {@code maxCompressionRatio} disables that check, matching how the archive export limits treat
 * zero.
 *
 * <p>The ratio is judged for the whole archive only. Single legitimate attachments, such as
 * uncompressed image masks or zero-padded instrument files, can inflate several hundred times, and
 * a per-entry check would not lower the worst case: bytes written are already capped at the ratio
 * times the archive's size.
 *
 * @param maxExpandedBytes total bytes that may be written across all entries
 * @param maxEntries entries (files and directories) the archive may contain
 * @param minFreeDiskBytes usable space the destination partition must keep throughout
 * @param maxCompressionRatio total bytes written divided by the archive file's size
 * @param ratioFloorBytes bytes the extraction must have written before its ratio is judged; small
 *     archives of well-compressing XML legitimately exceed the ratio
 */
public record ZipExtractionLimits(
    long maxExpandedBytes,
    int maxEntries,
    long minFreeDiskBytes,
    int maxCompressionRatio,
    long ratioFloorBytes) {

  /** Mirrors the default archive export limits: 10 000 MB expanded, 1 000 MB kept free. */
  public static final ZipExtractionLimits DEFAULTS =
      new ZipExtractionLimits(
          10_000 * FileUtils.ONE_MB,
          1_000_000,
          1_000 * FileUtils.ONE_MB,
          100,
          10 * FileUtils.ONE_MB);

  /** Copies these limits with the two disk-related bounds replaced. */
  public ZipExtractionLimits withDiskLimits(long maxExpandedBytes, long minFreeDiskBytes) {
    return new ZipExtractionLimits(
        maxExpandedBytes, maxEntries, minFreeDiskBytes, maxCompressionRatio, ratioFloorBytes);
  }
}

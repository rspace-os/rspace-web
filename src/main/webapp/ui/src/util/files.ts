/*
 * General purpose functions for working with files
 */

import prettyBytes from "pretty-bytes";

/**
 * Given the number of bytes, return a human-readable string using SI units
 * (decimal, e.g. "1.02 kB"). Returns "" for null/undefined so callers can pass
 * an optional size straight through.
 */
export function formatFileSize(bytes: number | null | undefined, decimalPlaces: number = 2): string {
  if (bytes === null || typeof bytes === "undefined") return "";
  return prettyBytes(bytes, { maximumFractionDigits: decimalPlaces });
}

/**
 * Given a filename, return all but the extension.
 */
export const filenameExceptExtension = (filename: string): string => /^(.*)(\..+)$/.exec(filename)?.[1] ?? filename;

/**
 * Given a filename, return just the extension.
 */
export const justFilenameExtension = (filename: string): string => /^(.*)\.(.+)$/.exec(filename)?.[2] ?? filename;

/**
 * Encodes the contents of blob as a base 64 string.
 */
export const blobToBase64 = (blob: Blob): Promise<string | null> =>
  new Promise((resolve) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(typeof reader.result === "string" ? reader.result : null);
    reader.readAsDataURL(blob);
  });

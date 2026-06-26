/*
 * General purpose functions for working with files
 */

import prettyBytes from "pretty-bytes";

/**
 * Given the number of bytes, return a human-readable string using SI units.
 * When callers pass decimalPlaces explicitly, preserve the previous fixed
 * precision behavior.
 */
export function formatFileSize(bytes: number | null | undefined, decimalPlaces?: number): string {
  if (bytes === null || typeof bytes === "undefined") return "";
  if (typeof decimalPlaces === "undefined") return prettyBytes(bytes, { locale: false });

  if (bytes === 0) return "0 B";
  const k = 1000;
  const sizes = ["B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB", "RB", "QB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const rounded = parseFloat((bytes / k ** i).toFixed(decimalPlaces));
  return `${rounded} ${sizes[i]}`;
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

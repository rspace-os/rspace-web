import { Buffer } from "node:buffer";

// ZIP central-directory field sizes/signatures
const END_OF_CENTRAL_DIRECTORY_SIGNATURE = Buffer.from([0x50, 0x4b, 0x05, 0x06]);
const END_OF_CENTRAL_DIRECTORY_RECORD_SIZE = 22;
const MAX_ZIP_COMMENT_LENGTH = 0xffff;

const CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50;
const CENTRAL_DIRECTORY_FILE_HEADER_SIZE = 46;

// Reads a zip's central directory to list entry names, without decompressing any entry.
export function listZipEntries(buffer: Buffer): string[] {
  const eocdOffset = findEndOfCentralDirectory(buffer);
  const totalEntries = buffer.readUInt16LE(eocdOffset + 10);
  let offset = buffer.readUInt32LE(eocdOffset + 16);

  const entries: string[] = [];
  for (let i = 0; i < totalEntries; i++) {
    if (buffer.readUInt32LE(offset) !== CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE) {
      throw new Error(`listZipEntries: expected central directory file header at offset ${offset}`);
    }
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const nameStart = offset + CENTRAL_DIRECTORY_FILE_HEADER_SIZE;
    entries.push(buffer.toString("utf-8", nameStart, nameStart + nameLength));
    offset = nameStart + nameLength + extraLength + commentLength;
  }
  return entries;
}

function findEndOfCentralDirectory(buffer: Buffer): number {
  const searchFloor = Math.max(0, buffer.length - END_OF_CENTRAL_DIRECTORY_RECORD_SIZE - MAX_ZIP_COMMENT_LENGTH);
  const offset = buffer.lastIndexOf(
    END_OF_CENTRAL_DIRECTORY_SIGNATURE,
    buffer.length - END_OF_CENTRAL_DIRECTORY_RECORD_SIZE,
  );
  if (offset === -1 || offset < searchFloor) {
    throw new Error("listZipEntries: end of central directory record not found");
  }
  return offset;
}

import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const currentDir = dirname(fileURLToPath(import.meta.url));

export function storageStatePath(username: string): string {
  return resolve(currentDir, "../../../playwright/.auth", `${username}.json`);
}

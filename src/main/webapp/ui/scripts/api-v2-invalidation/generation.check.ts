import { spawnSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { resolve } from "node:path";
import { expect, it } from "vitest";
import { getOperations } from "./index";

const require = createRequire(import.meta.url);
const ui = resolve(import.meta.dirname, "../..");
const scratchRoot = resolve(ui, "../../../../.claude");
function scratch() {
  mkdirSync(scratchRoot, { recursive: true });
  return mkdtempSync(resolve(scratchRoot, "api-v2-generation-"));
}
function generatedFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) return entry.name === "valibot" ? [] : generatedFiles(path);
    return path.endsWith(".ts") ? [path] : [];
  });
}

it("retains operation IDs over a complete scratch generation", () => {
  const directory = scratch();
  try {
    for (const file of [
      "orval.config.ts",
      "scripts/api-v2-invalidation",
      "src/modules/common/api/v2/crossResourceInvalidation.ts",
    ]) {
      const target = resolve(directory, file);
      mkdirSync(resolve(target, ".."), { recursive: true });
      cpSync(resolve(ui, file), target, { recursive: true });
    }
    const spec = process.env.API_V2_GENERATION_SPEC ?? resolve(ui, "src/modules/common/api/v2/openapi.generated.json");
    const api = resolve(directory, "src/modules/common/api/v2");
    cpSync(spec, resolve(api, "openapi.generated.json"));
    writeFileSync(
      resolve(api, "apiV2Fetch.ts"),
      `
      export type ErrorType<E> = Error;
      export type BodyType<B> = B;
      export function apiV2Fetch<T>(_url: string, _init?: RequestInit & { operationId?: string }): Promise<T> { throw new Error("stub"); }
    `,
    );
    writeFileSync(
      resolve(api, "apiV2HeadersFetch.ts"),
      `
      export function apiV2HeadersFetch<T>(_url: string, _init?: RequestInit & { operationId?: string }): Promise<{ body: T; headers: Headers }> { throw new Error("stub"); }
      export function apiV2BlobFetch<T = Blob>(_url: string, _init?: RequestInit & { operationId?: string }): Promise<{ body: Blob; headers: Headers }> { throw new Error("stub"); }
    `,
    );
    const result = spawnSync(process.execPath, [require.resolve("orval/bin/orval"), "--config", "orval.config.ts"], {
      cwd: directory,
      env: { ...process.env, PATH: `${resolve(ui, "../../../../node_modules/.bin")}:${process.env.PATH}` },
      encoding: "utf8",
      timeout: 60_000,
    });
    expect(result.status, `${result.stdout}\n${result.stderr}`).toBe(0);
    expect(existsSync(resolve(api, "generated/valibot/native.ts"))).toBe(true);
    const files = generatedFiles(resolve(api, "generated"));
    expect(files.length).toBeGreaterThan(0);
    let operationCount = 0;
    for (const file of files) {
      const source = readFileSync(file, "utf8");

      operationCount += [...source.matchAll(/operationId:\s*["']/g)].length;
    }
    const operations = getOperations(JSON.parse(readFileSync(spec, "utf8")));
    expect(operationCount).toBe(operations.length);
    for (const operation of operations.filter(({ operationId }) =>
      /^(get|create|createOrReplace|rotate)(User)?BookingCalendarSubscription$/.test(operationId),
    )) {
      const declaration = `export const ${operation.operationId} =`;
      const source = files.map((file) => readFileSync(file, "utf8")).find((source) => source.includes(declaration));
      expect(source, operation.operationId).toBeDefined();
      const body = source?.split(declaration)[1].split("export const")[0];
      expect(body, operation.operationId).toContain("return apiV2HeadersFetch");
    }
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}, 90_000);

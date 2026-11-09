import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

// Orval 8.46 emits requestOptions before caller options. Keep operation selection immutable at runtime.
export function protectOperationId(source: string): string {
  return source
    .replace(
      /(?<!NonNullable<)SecondParameter<typeof (apiV2Fetch|apiV2HeadersFetch|apiV2BlobFetch)>/g,
      'Omit<NonNullable<SecondParameter<typeof $1>>, "operationId">',
    )
    .replace(
      /(?<!NonNullable<)Parameters<typeof (apiV2Fetch|apiV2HeadersFetch|apiV2BlobFetch)>\[1\]/g,
      'Omit<NonNullable<Parameters<typeof $1>[1]>, "operationId">',
    )
    .replace(
      /(operationId:\s*["'][^"']+["'],?)(\s*)\.\.\.options,/g,
      (_match, operation: string, whitespace: string) => `...options,${whitespace}${operation.replace(/,$/, "")},`,
    );
}
export function verifyOperationIdProtection(source: string, path = "generated client") {
  // Tokenize strings and comments before checking structure, so formatting cannot hide an unsafe spread.
  const tokens = [
    ...source.matchAll(
      /\/\*[\s\S]*?\*\/|\/\/[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\.\.\.|[\w$]+|[^\s]/g,
    ),
  ]
    .map((match) => match[0])
    .filter((token) => !token.startsWith("//") && !token.startsWith("/*"));
  const text = tokens.join(" ");
  const protectedOptions =
    /Omit\s*<\s*NonNullable\s*<\s*(?:SecondParameter\s*<\s*typeof\s+apiV2\w+\s*>|Parameters\s*<\s*typeof\s+apiV2\w+\s*>\s*\[\s*1\s*\])\s*>\s*,\s*["']operationId["']\s*>/g;
  if (/(?:SecondParameter|Parameters)\s*<\s*typeof\s+apiV2\w+/.test(text.replace(protectedOptions, ""))) {
    throw new Error(`${path}: Unprotected public API v2 request options`);
  }
  const objects: boolean[] = [];
  tokens.forEach((token, index) => {
    if (token === "{") objects.push(false);
    if (token === "}") objects.pop();
    if (
      ["operationId", '"operationId"', "'operationId'"].includes(token) &&
      tokens[index + 1] === ":" &&
      /^["'`]/.test(tokens[index + 2] ?? "")
    ) {
      objects[objects.length - 1] = true;
    }
    if (token === "..." && tokens[index + 1] === "options" && objects.at(-1)) {
      throw new Error(`${path}: operationId must follow caller options`);
    }
  });
}

export function protectGeneratedOperationIds(...args: unknown[]) {
  try {
    const paths = args[0];
    if (!Array.isArray(paths) || !paths.every((path): path is string => typeof path === "string")) {
      throw new Error("Orval hook must receive generated file paths");
    }
    function files(path: string): string[] {
      return statSync(path).isDirectory()
        ? readdirSync(path).flatMap((name) => files(resolve(path, name)))
        : path.endsWith(".ts")
          ? [path]
          : [];
    }
    for (const path of new Set(paths.flatMap(files))) {
      const source = readFileSync(path, "utf8");
      const protectedSource = protectOperationId(source);
      verifyOperationIdProtection(protectedSource, path);
      if (source !== protectedSource) writeFileSync(path, protectedSource);
    }
  } catch (error) {
    // Orval logs hook exceptions and continues. Preserve a failing generator exit status too.
    process.exitCode = 1;
    throw error;
  }
}

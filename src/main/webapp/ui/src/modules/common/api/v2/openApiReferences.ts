const COMPONENTS = "#/components/";
const SCHEMAS = `${COMPONENTS}schemas/`;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

/**
 * The REST API v2 OpenAPI document with every local component reference replaced by its target.
 * The document publishes repeated parameters, responses, examples, filter catalogues and access
 * policies once under `components`. Schema references stay, because schemas may be recursive.
 */
export function dereferenceApiV2OpenApi(document: unknown): unknown {
  if (!isRecord(document)) return document;
  const target = (ref: string): unknown => {
    let node: unknown = document;
    for (const part of ref.slice(2).split("/")) {
      const key = part.replaceAll("~1", "/").replaceAll("~0", "~");
      if (!isRecord(node) || !Object.hasOwn(node, key)) throw new Error(`Unresolved OpenAPI reference ${ref}`);
      node = node[key];
    }
    return node;
  };
  const resolve = (node: unknown, seen: readonly string[]): unknown => {
    if (Array.isArray(node)) return node.map((item) => resolve(item, seen));
    if (!isRecord(node)) return node;
    const ref = node.$ref;
    if (
      typeof ref === "string" &&
      Object.keys(node).length === 1 &&
      ref.startsWith(COMPONENTS) &&
      !ref.startsWith(SCHEMAS)
    ) {
      if (seen.includes(ref)) throw new Error(`Recursive OpenAPI reference ${ref}`);
      return resolve(target(ref), [...seen, ref]);
    }
    return Object.fromEntries(Object.entries(node).map(([key, value]) => [key, resolve(value, seen)]));
  };
  return resolve(document, []);
}

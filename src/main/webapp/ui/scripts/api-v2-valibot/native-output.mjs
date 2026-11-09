const quote = JSON.stringify;

// Native expressions use balanced JS calls and literals. Preserve their validation
// actions verbatim while locating only the arrays/properties requiring readonly output.
function split(source, delimiter = ",") {
  const result = [];
  let depth = 0,
    start = 0,
    string,
    escaped = false;
  for (let i = 0; i < source.length; i++) {
    const char = source[i];
    if (string) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === string) string = undefined;
    } else if ("\"'`".includes(char)) string = char;
    else if ("([{".includes(char)) depth++;
    else if (")]}".includes(char)) depth--;
    else if (char === delimiter && depth === 0) {
      result.push(source.slice(start, i).trim());
      start = i + 1;
    }
  }
  result.push(source.slice(start).trim());
  return result.filter(Boolean);
}
function call(source) {
  const match = /^v\.(\w+)\(([\s\S]*)\)$/.exec(source.trim());
  return match && { name: match[1], args: split(match[2]) };
}
function keyMatches(source, key) {
  const raw = source.replace(/^\[([\s\S]*)\]$/, "$1").trim();
  return raw === key || raw === quote(key) || raw === `'${key.replaceAll("\\", "\\\\").replaceAll("'", "\\'")}'`;
}

// Match generated code only: example values and descriptions may contain action text.
function rewriteCode(source, rewrite) {
  const literals = /(["'`])(?:\\[\s\S]|(?!\1)[^\\])*\1|\/\/[^\n]*|\/\*[\s\S]*?\*\//g;
  let output = "",
    cursor = 0;
  for (const match of source.matchAll(literals)) {
    output += rewrite(source.slice(cursor, match.index)) + match[0];
    cursor = match.index + match[0].length;
  }
  return output + rewrite(source.slice(cursor));
}

export function adaptNative(document, source, { modelImport = "../models" } = {}) {
  const components = document.components?.schemas ?? {};
  const modelName = (name) => name[0].toUpperCase() + name.slice(1);
  const refs = new Map(Object.keys(components).map((name) => [modelName(name), name]));
  function dereference(schema, seen = new Set()) {
    if (!schema.$ref) return schema;
    const name = schema.$ref.split("/").at(-1);
    if (seen.has(name)) throw new Error(`Recursive array alias ${name}`);
    return {
      ...dereference(components[name], new Set([...seen, name])),
      ...Object.fromEntries(Object.entries(schema).filter(([key]) => key !== "$ref")),
    };
  }
  function properties(schema, seen = new Set()) {
    if (schema.$ref) {
      const name = schema.$ref.split("/").at(-1);
      if (seen.has(name)) return {};
      return properties(components[name], new Set([...seen, name]));
    }
    return Object.assign(
      Object.create(null),
      ...(schema.allOf ?? []).map((part) => properties(part, seen)),
      schema.properties,
    );
  }
  function adjust(expression, schema) {
    if (!schema) return expression;
    const parsed = call(expression);
    const effective = dereference(schema);
    const isArray = effective.type === "array" || (Array.isArray(effective.type) && effective.type.includes("array"));
    if (!parsed) return effective.readOnly && isArray ? `v.pipe(${expression}, v.readonly())` : expression;
    const { name, args } = parsed;
    if (["optional", "nullable", "nullish", "nonNullable", "pipe"].includes(name)) {
      args[0] = adjust(args[0], schema);
    } else if (name === "array") {
      args[0] = adjust(args[0], effective.items);
    } else if (["object", "looseObject", "objectWithRest"].includes(name) && args[0]?.startsWith("{")) {
      const fields = properties(schema);
      args[0] = `{${split(args[0].slice(1, -1))
        .map((entry) => {
          const pieces = split(entry, ":");
          const key = Object.keys(fields).find((candidate) => keyMatches(pieces[0], candidate));
          return key === undefined ? entry : `${pieces[0]}: ${adjust(pieces.slice(1).join(":"), fields[key])}`;
        })
        .join(", ")}}`;
      if (name === "objectWithRest" && typeof effective.additionalProperties === "object")
        args[1] = adjust(args[1], effective.additionalProperties);
    } else if (name === "record" && typeof effective.additionalProperties === "object") {
      args[1] = adjust(args[1], effective.additionalProperties);
    } else if (["union", "intersect"].includes(name) && args[0]?.startsWith("[")) {
      const variants =
        schema.anyOf ??
        schema.oneOf ??
        schema.allOf ??
        (Array.isArray(schema.type) ? schema.type.map((type) => ({ ...schema, type })) : []);
      args[0] = `[${split(args[0].slice(1, -1))
        .map((entry, i) =>
          adjust(entry, variants[i] && { ...variants[i], ...(schema.readOnly ? { readOnly: true } : {}) }),
        )
        .join(", ")}]`;
    }
    const adjusted = `v.${name}(${args.join(", ")})`;
    return (name === "array" || (name === "lazy" && isArray)) && effective.readOnly
      ? `v.pipe(${adjusted}, v.readonly())`
      : adjusted;
  }
  const declarations = new Map();
  for (const [name, schema] of Object.entries(components)) declarations.set(modelName(name), schema);
  // Operation constants can inline component properties, so adjust these as well.
  for (const item of Object.values(document.paths ?? {}))
    for (const op of Object.values(item)) {
      if (!op?.operationId) continue;
      const name = modelName(op.operationId);
      const body = Object.values(op.requestBody?.content ?? {}).find((entry) => entry.schema)?.schema;
      if (body) declarations.set(`${name}Body`, body);
      for (const [status, response] of Object.entries(op.responses ?? {})) {
        if (!status.startsWith("2")) continue;
        const schema = Object.values(response.content ?? {}).find((entry) => entry.schema)?.schema;
        if (schema) {
          declarations.set(`${name}Response`, schema);
          break;
        }
      }
    }
  let typed = false;
  // Only generated lazy callbacks returning a known component acquire model typing.
  source = rewriteCode(source, (code) =>
    code
      .replace(/v\.lazy\(\(\)(?::\s*v\.GenericSchema)?\s*=>\s*(\w+)\)/g, (_whole, name) => {
        if (!refs.has(name)) throw new Error(`Unknown recursive native schema ${name}`);
        typed = true;
        return `v.lazy((): v.GenericSchema<unknown, Models.${name}> => ${name})`;
      })
      .replace(/,\s*v\.url\(\)/g, ""),
  );
  source = source.replace(
    /(export const (\w+)\s*=\s*)([\s\S]*?)(?=\nexport const |\s*$)/g,
    (whole, head, name, expression) => {
      const schema = declarations.get(name);
      if (!schema) return whole;
      return `${head}${adjust(expression.trim().replace(/;$/, ""), schema)};\n`;
    },
  );
  if (typed) source += `\nimport type * as Models from ${quote(modelImport)};\n`;
  return source;
}

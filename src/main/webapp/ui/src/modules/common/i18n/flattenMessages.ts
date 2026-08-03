export function flattenMessages(
  value: unknown,
  prefix = "",
  messages: Record<string, string> = {},
): Record<string, string> {
  if (typeof value === "string") {
    if (prefix) messages[prefix] = value;
    return messages;
  }
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError(`Message at '${prefix}' must be a string or object`);
  }

  for (const [key, child] of Object.entries(value)) {
    flattenMessages(child, prefix ? `${prefix}.${key}` : key, messages);
  }
  return messages;
}

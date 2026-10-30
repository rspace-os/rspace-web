import DOMPurify from "dompurify";

// Inventory rich text (see CONTEXT.md): the read-only view keeps only what the
// editor in TextField.tsx produces, typed or pasted, whatever path the text arrived by.
const ALLOWED_TAGS = [
  "p",
  "h1",
  "h2",
  "h3",
  "h4",
  "h5",
  "h6",
  "pre",
  "br",
  "strong",
  "em",
  "b",
  "i",
  "u",
  "s",
  "del",
  "sub",
  "sup",
  "span",
  "a",
  "ul",
  "ol",
  "li",
];
const ALLOWED_ATTR = ["href", "target", "rel", "title", "style", "start", "reversed", "type", "value"];
// None of these can load a URL or move text over the page, so a naive split on ";" is safe.
const ALLOWED_STYLES = ["background-color", "color", "text-decoration", "text-align", "padding-left"];

const purify = DOMPurify();
purify.addHook("uponSanitizeAttribute", (_node, data) => {
  if (data.attrName !== "style") return;
  data.attrValue = data.attrValue
    .split(";")
    .filter((declaration) => ALLOWED_STYLES.includes(declaration.split(":")[0].trim().toLowerCase()))
    .map((declaration) => `${declaration.trim()};`)
    .join(" ");
  data.keepAttr = data.attrValue !== "";
});

export function sanitizeInventoryRichText(html: string): string {
  return purify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR, ALLOW_DATA_ATTR: false, ALLOW_ARIA_ATTR: false });
}

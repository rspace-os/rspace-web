import DOMPurify from "dompurify";

// Inventory rich text (see CONTEXT.md): the read-only view keeps only what the
// editor toolbar in TextField.tsx produces, whatever path the text arrived by.
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
  "span",
  "a",
  "ul",
  "ol",
  "li",
];
const ALLOWED_ATTR = ["href", "target", "rel", "title", "style"];

export function sanitizeInventoryRichText(html: string): string {
  return DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR, ALLOW_DATA_ATTR: false, ALLOW_ARIA_ATTR: false });
}

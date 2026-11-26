#!/usr/bin/env node
/**
 * Generates styles/dark/legacy-generated.css: dark-mode colour overrides for
 * every stylesheet the JSP pages link, and for the <style> blocks in JSP
 * pages and tags.
 *
 * For each rule in those stylesheets it keeps only the colour declarations,
 * remaps the colours (light backgrounds and borders become dark, dark text
 * becomes light, saturated accents stay), and scopes the rule to `html.dark`.
 * The hand-written styles/dark/legacy-dark.css loads after this file and
 * fine-tunes the result.
 *
 * Usage: node scripts/generate-legacy-dark-css.mjs [--check]
 *   --check  exit non-zero if the committed file is out of date.
 */
import { globSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { transform } from "lightningcss";

const WEBAPP = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "main", "webapp");
const OUTPUT = "styles/dark/legacy-generated.css";

/**
 * Stylesheets linked by decorated JSP pages. Sheets in `GLOBAL` load on every
 * page, so their element-only selectors (such as `th`) are kept; for the
 * others such selectors are skipped, since this output loads on every page.
 */
const GLOBAL = [
  "styles/bootstrap-custom-flat.css",
  "styles/simplicity/theme.css",
  "styles/rs-global.css",
  "scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css",
  "scripts/bower_components/Apprise-v2/apprise-v2.css",
  "styles/jquery.toastmessage.css",
  "scripts/bower_components/bootstrap/dist/css/bootstrap.min.css",
  "styles/simplicity/tools.css",
  "styles/simplicity/layout.css",
  "styles/public.css",
];
const PAGE = [
  "styles/system.css",
  "styles/messages.css",
  "scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.css",
  "scripts/bower_components/jquery-tagit/css/tagit.ui-zendesk.css",
  "scripts/bower_components/jquery-tagit/css/jquery.tagit.css",
  "styles/pages/workspace/workspace-dialogs.css",
  "styles/pages/public/passwordReset.css",
  "styles/simplicity/header.css",
  "styles/sign-in-up-common.css",
  "styles/pages/workspace/workspace.css",
  "styles/pages/workspace/workspace-widgets.css",
  "styles/pages/workspace/workspace-toolbar.css",
  "styles/pages/workspace/workspace-extra-icons.css",
  "styles/authentication/login.css",
  "scripts/jqueryFileTree/jqueryFileTree.css",
  "scripts/bootstrap-namespace/bootstrap-namespace.min.css",
  "styles/tinymce_rs.css",
  "styles/tags/fileTreeBrowser.css",
  "styles/structuredDocument.css",
  "styles/signingProcess.css",
  "styles/pages/searchableRecordPicker.css",
  "styles/pages/groups/viewGroup.css",
  "styles/journal.css",
  "scripts/bower_components/photoswipe/dist/default-skin/default-skin.css",
  "scripts/bower_components/jquery.fancytree/dist/skin-bootstrap/ui.fancytree.min.css",
  "styles/userform.css",
  "styles/tags/createFromTemplateDlg.css",
  "styles/preferences.css",
  "styles/plugins/autocomplete.css",
  "styles/pages/messaging/dashboard.css",
  "styles/ownCloud.css",
  "styles/dropbox.css",
  "styles/audit.css",
  "styles/admin.css",
  "scripts/bower_components/select2/dist/css/select2.min.css",
];

/* Dark palette, matching ui/src/theme.ts and styles/dark/legacy-dark.css. */
const PANEL = { h: 197, s: 0.14, l: 0.1 };
/* Highest luminance at which #e3e3e3 text keeps 4.5:1 (with some margin). */
const ACCENT_MAX_LUMINANCE = 0.1;

// ---------------------------------------------------------------- colours

const NAMED = {
  white: "#ffffff", black: "#000000", silver: "#c0c0c0", gray: "#808080", grey: "#808080",
  lightgray: "#d3d3d3", lightgrey: "#d3d3d3", darkgray: "#a9a9a9", darkgrey: "#a9a9a9",
  gainsboro: "#dcdcdc", whitesmoke: "#f5f5f5", dimgray: "#696969", dimgrey: "#696969",
  red: "#ff0000", green: "#008000", blue: "#0000ff", navy: "#000080", maroon: "#800000",
  orange: "#ffa500", yellow: "#ffff00", lightyellow: "#ffffe0", ivory: "#fffff0",
  aliceblue: "#f0f8ff", ghostwhite: "#f8f8ff", snow: "#fffafa", linen: "#faf0e6",
  beige: "#f5f5dc", lightblue: "#add8e6", lightsteelblue: "#b0c4de", lavender: "#e6e6fa",
  honeydew: "#f0fff0", mintcream: "#f5fffa", azure: "#f0ffff", seashell: "#fff5ee",
  floralwhite: "#fffaf0", oldlace: "#fdf5e6", cornsilk: "#fff8dc", lemonchiffon: "#fffacd",
  darkblue: "#00008b", darkred: "#8b0000", darkgreen: "#006400", teal: "#008080",
  purple: "#800080", olive: "#808000", steelblue: "#4682b4", slategray: "#708090",
  slategrey: "#708090", lightslategray: "#778899", darkslategray: "#2f4f4f",
};
const COLOR_RE = new RegExp(
  `#[0-9a-fA-F]{3,8}\\b|rgba?\\([^)]*\\)|hsla?\\([^)]*\\)|\\b(?:${Object.keys(NAMED).join("|")})\\b`,
  "gi",
);

function parseColor(text) {
  const t = text.toLowerCase();
  if (NAMED[t]) return parseColor(NAMED[t]);
  if (t.startsWith("#")) {
    let h = t.slice(1);
    if (h.length === 3 || h.length === 4) h = [...h].map((c) => c + c).join("");
    const n = (i) => parseInt(h.slice(i, i + 2), 16);
    return { r: n(0), g: n(2), b: n(4), a: h.length === 8 ? n(6) / 255 : 1 };
  }
  const nums = t.match(/[\d.]+%?/g)?.map((v) => (v.endsWith("%") ? parseFloat(v) / 100 : parseFloat(v)));
  if (!nums || nums.length < 3) return null;
  if (t.startsWith("rgb")) {
    const c = (v, raw) => (raw.endsWith("%") ? v * 255 : v);
    const raw = t.match(/[\d.]+%?/g);
    return { r: c(nums[0], raw[0]), g: c(nums[1], raw[1]), b: c(nums[2], raw[2]), a: nums[3] ?? 1 };
  }
  return { ...hslToRgb(nums[0], nums[1], nums[2]), a: nums[3] ?? 1 };
}

function rgbToHsl({ r, g, b }) {
  const [R, G, B] = [r / 255, g / 255, b / 255];
  const max = Math.max(R, G, B);
  const min = Math.min(R, G, B);
  const l = (max + min) / 2;
  if (max === min) return { h: 0, s: 0, l };
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  const h = max === R ? (G - B) / d + (G < B ? 6 : 0) : max === G ? (B - R) / d + 2 : (R - G) / d + 4;
  return { h: h * 60, s, l };
}

function hslToRgb(h, s, l) {
  const k = (n) => (n + h / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const f = (n) => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  return { r: f(0) * 255, g: f(8) * 255, b: f(4) * 255 };
}

function luminance({ r, g, b }) {
  const f = (v) => {
    const c = v / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}

function format({ r, g, b, a }) {
  const hex = (v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, "0");
  if (a < 1) return `rgba(${Math.round(r)}, ${Math.round(g)}, ${Math.round(b)}, ${+a.toFixed(3)})`;
  return `#${hex(r)}${hex(g)}${hex(b)}`;
}

/**
 * A light surface becomes a dark one. A saturated mid-tone surface (the
 * brand blues) is darkened until light text on it reaches 4.5:1. Dark and
 * muted surfaces stay.
 */
function darkSurface(c) {
  const y = luminance(c);
  const { h, s, l } = rgbToHsl(c);
  if (y >= 0.45) {
    const neutral = s < 0.15;
    const nl = PANEL.l + (1 - l) * 0.6;
    return { ...hslToRgb(neutral ? PANEL.h : h, neutral ? PANEL.s : Math.min(s, 0.45), nl), a: c.a };
  }
  if (y > ACCENT_MAX_LUMINANCE && s > 0.25) {
    let nl = l;
    let rgb = hslToRgb(h, s, nl);
    while (luminance(rgb) > ACCENT_MAX_LUMINANCE && nl > 0.05) {
      nl -= 0.01;
      rgb = hslToRgb(h, s, nl);
    }
    return { ...rgb, a: c.a };
  }
  return c;
}

/** Dark text becomes light; light and mid-tone text stays. */
function lightText(c) {
  if (luminance(c) >= 0.2) return c;
  const { h, s, l } = rgbToHsl(c);
  const nl = s < 0.15 ? 0.89 - l * 0.35 : Math.max(0.68, 1 - l * 0.55);
  return { ...hslToRgb(h, s < 0.15 ? 0 : Math.min(s, 0.85), nl), a: c.a };
}

/** Light borders become dark grey; dark borders become mid grey. */
function darkBorder(c) {
  const y = luminance(c);
  const { h, s, l } = rgbToHsl(c);
  if (y >= 0.45) return { ...hslToRgb(h, Math.min(s, 0.3), 0.19 + (1 - l) * 0.4), a: c.a };
  if (y < 0.03 && s < 0.15) return { ...hslToRgb(0, 0, 0.35), a: c.a };
  return c;
}

function mapColors(value, fn) {
  let changed = false;
  const out = value.replace(COLOR_RE, (m) => {
    const c = parseColor(m);
    if (!c) return m;
    const mapped = format(fn(c));
    if (mapped !== format(c)) changed = true;
    return mapped;
  });
  return changed ? out : null;
}

// ---------------------------------------------------------------- parsing

function stripComments(css) {
  return css.replace(/\/\*[\s\S]*?\*\//g, "");
}

/** Splits CSS into top-level blocks: { prelude, body } or nested at-rules. */
function parseBlocks(css) {
  const blocks = [];
  let i = 0;
  while (i < css.length) {
    const open = css.indexOf("{", i);
    if (open === -1) break;
    const semi = css.indexOf(";", i);
    if (semi !== -1 && semi < open && css.slice(i, semi).trim().startsWith("@")) {
      i = semi + 1; // @import / @charset
      continue;
    }
    const prelude = css.slice(i, open).trim();
    let depth = 1;
    let j = open + 1;
    let quote = null;
    for (; j < css.length && depth > 0; j++) {
      const ch = css[j];
      if (quote) {
        if (ch === quote && css[j - 1] !== "\\") quote = null;
      } else if (ch === '"' || ch === "'") quote = ch;
      else if (ch === "{") depth++;
      else if (ch === "}") depth--;
    }
    blocks.push({ prelude, body: css.slice(open + 1, j - 1) });
    i = j;
  }
  return blocks;
}

function splitTopLevel(text, sep) {
  const parts = [];
  let depth = 0;
  let quote = null;
  let start = 0;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quote) {
      if (ch === quote && text[i - 1] !== "\\") quote = null;
    } else if (ch === '"' || ch === "'") quote = ch;
    else if (ch === "(" || ch === "[") depth++;
    else if (ch === ")" || ch === "]") depth--;
    else if (ch === sep && depth === 0) {
      parts.push(text.slice(start, i));
      start = i + 1;
    }
  }
  parts.push(text.slice(start));
  return parts.map((p) => p.trim()).filter(Boolean);
}

const BORDER_PROPS = /^border(-(top|right|bottom|left|block|inline)(-(start|end))?)?(-color)?$|^outline(-color)?$|^column-rule(-color)?$/;

/** Returns the dark declarations for one original declaration, or []. */
function darkDeclarations(prop, value, important) {
  const imp = important ? " !important" : "";
  if (/\b(inherit|initial|unset|currentcolor)\b/i.test(value) && !COLOR_RE.test(value)) return [];
  COLOR_RE.lastIndex = 0;
  if (prop === "color" || prop === "fill" || prop === "stroke" || prop === "caret-color" || prop === "-webkit-text-fill-color") {
    const v = mapColors(value, lightText);
    return v ? [`${prop}: ${v}${imp}`] : [];
  }
  if (prop === "background-color") {
    const v = mapColors(value, darkSurface);
    return v ? [`background-color: ${v}${imp}`] : [];
  }
  if (prop === "background" || prop === "background-image") {
    const out = [];
    const gradients = value.match(/(?:-webkit-|-moz-|-o-|-ms-)?(?:repeating-)?(?:linear|radial)-gradient\((?:[^()]|\([^()]*\))*\)/gi);
    if (gradients) {
      const standard = gradients.find((g) => !/^-/.test(g));
      if (standard) {
        const v = mapColors(standard, darkSurface);
        if (v) out.push(`background-image: ${v}${imp}`);
      }
    }
    if (prop === "background") {
      const rest = value.replace(/(?:-webkit-|-moz-|-o-|-ms-)?(?:repeating-)?(?:linear|radial)-gradient\((?:[^()]|\([^()]*\))*\)|url\([^)]*\)/gi, " ");
      const colour = rest.match(COLOR_RE)?.[0];
      COLOR_RE.lastIndex = 0;
      if (colour) {
        const v = mapColors(colour, darkSurface);
        if (v) out.push(`background-color: ${v}${imp}`);
      }
    }
    return out;
  }
  if (BORDER_PROPS.test(prop)) {
    const colourProp = prop.endsWith("-color") ? prop : `${prop}-color`;
    const colours = value.match(COLOR_RE);
    COLOR_RE.lastIndex = 0;
    if (!colours) return [];
    const mapped = colours.map((c) => format(darkBorder(parseColor(c) ?? { r: 0, g: 0, b: 0, a: 1 })));
    if (mapped.every((m, i) => m === format(parseColor(colours[i]) ?? { r: 0, g: 0, b: 0, a: 1 }))) return [];
    return [`${colourProp}: ${prop.endsWith("-color") || prop === "border-color" ? mapped.join(" ") : mapped[0]}${imp}`];
  }
  return [];
}

/**
 * Containers of user-authored rich text. They stay light in dark mode, like
 * paper, because their content carries its own colours. Must match
 * USER_CONTENT in styles/dark/legacy-dark.css.
 */
const USER_CONTENT = [".textFieldViewModeDiv", ".journalPageContent"];
/* Wrapped in :where() so the exclusion adds no specificity. */
const NOT_USER_CONTENT = `:not(:where(${USER_CONTENT.join(", ")}, :is(${USER_CONTENT.join(", ")}) *))`;

/** Index where the selector's last compound starts. */
function subjectStart(sel) {
  let depth = 0;
  let start = 0;
  for (let i = 0; i < sel.length; i++) {
    const ch = sel[i];
    if (ch === "(" || ch === "[") depth++;
    else if (ch === ")" || ch === "]") depth--;
    else if (depth === 0 && /[\s>+~]/.test(ch)) start = i + 1;
  }
  return start;
}

/** Adds the user-content exclusion to the selector's subject. */
function excludeUserContent(sel) {
  const start = subjectStart(sel);
  const subject = sel.slice(start);
  const pseudo = subject.search(/::|:(before|after|first-line|first-letter|placeholder|selection)\b/i);
  const at = pseudo === -1 ? sel.length : start + pseudo;
  return sel.slice(0, at) + NOT_USER_CONTENT + sel.slice(at);
}

function scopeSelector(sel) {
  const scoped = excludeUserContent(sel);
  if (/^:root\b/.test(sel)) return scoped.replace(/^:root/, "html.dark");
  if (/^html\b/.test(sel)) return scoped.replace(/^html/, "html.dark");
  return `html.dark ${scoped}`;
}

const ELEMENT_ONLY = /^[a-z0-9*\s>+~:()-]+$/i;

function convertRules(css, isGlobal, stats) {
  const out = [];
  for (const { prelude, body } of parseBlocks(css)) {
    if (prelude.startsWith("@")) {
      if (/^@media\b/i.test(prelude) && !/\bprint\b/i.test(prelude)) {
        const inner = convertRules(body, isGlobal, stats);
        if (inner.length) out.push(`${prelude} {\n${inner.map((l) => `  ${l.replace(/\n/g, "\n  ")}`).join("\n")}\n}`);
      } else if (/^@supports\b/i.test(prelude)) {
        const inner = convertRules(body, isGlobal, stats);
        if (inner.length) out.push(`${prelude} {\n${inner.map((l) => `  ${l.replace(/\n/g, "\n  ")}`).join("\n")}\n}`);
      }
      continue;
    }
    const decls = splitTopLevel(body, ";")
      .map((d) => {
        const colon = d.indexOf(":");
        if (colon === -1) return null;
        let value = d.slice(colon + 1).trim();
        const important = /!\s*important\s*$/i.test(value);
        value = value.replace(/!\s*important\s*$/i, "").trim();
        return { prop: d.slice(0, colon).trim().toLowerCase(), value, important };
      })
      .filter(Boolean);
    const dark = decls.flatMap(({ prop, value, important }) => darkDeclarations(prop, value, important));
    if (!dark.length) continue;
    const selectors = splitTopLevel(prelude, ",").filter((s) => {
      if (/::?-(moz|ms|webkit)-/.test(s)) return false; // vendor pseudos invalidate whole rule lists
      if (!isGlobal && ELEMENT_ONLY.test(s)) {
        stats.skipped.add(s);
        return false;
      }
      return true;
    });
    if (!selectors.length) continue;
    stats.rules++;
    out.push(`${selectors.map(scopeSelector).join(",\n")} {\n${[...new Set(dark)].map((d) => `  ${d};`).join("\n")}\n}`);
  }
  return out;
}

// ---------------------------------------------------------------- main

/** The <style> blocks of a JSP page or tag, with JSP comments and EL removed. */
function inlineStyles(file) {
  const source = readFileSync(join(WEBAPP, file), "utf8").replace(/<%--[\s\S]*?--%>/g, "");
  return [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/gi)]
    .map((m) => m[1])
    .filter((css) => !/\$\{|<%/.test(css))
    .join("\n");
}

const JSP_FILES = globSync("{WEB-INF,common}/**/*.{jsp,tag,jspf}", { cwd: WEBAPP }).sort();

const stats = { rules: 0, skipped: new Set() };
const sections = [];
const sources = [
  ...[...GLOBAL, ...PAGE].map((file) => [file, readFileSync(join(WEBAPP, file), "utf8")]),
  ...JSP_FILES.map((file) => [file, inlineStyles(file)]),
];
for (const [file, source] of sources) {
  const rules = convertRules(stripComments(source), GLOBAL.includes(file), stats);
  if (rules.length) sections.push(`/* ${file} */\n\n${rules.join("\n\n")}`);
}
const output = `/*
 * GENERATED by scripts/generate-legacy-dark-css.mjs. Do not edit; change the
 * script or override in legacy-dark.css, then run \`pnpm run css:legacy-dark\`.
 *
 * Dark-mode colour overrides for the legacy stylesheets, scoped to html.dark.
 */

${sections.join("\n\n")}
`;

// Fail loudly if the output is not valid CSS.
transform({ filename: OUTPUT, code: Buffer.from(output), errorRecovery: false });

const target = join(WEBAPP, OUTPUT);
if (process.argv.includes("--check")) {
  const current = (() => {
    try {
      return readFileSync(target, "utf8");
    } catch {
      return "";
    }
  })();
  if (current !== output) {
    console.error(`${OUTPUT} is out of date; run \`pnpm run css:legacy-dark\`.`);
    process.exit(1);
  }
  console.log(`${OUTPUT} is up to date.`);
} else {
  writeFileSync(target, output);
  console.log(`Wrote ${OUTPUT}: ${stats.rules} rules; skipped ${stats.skipped.size} element-only selectors from page sheets.`);
}

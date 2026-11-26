#!/usr/bin/env node
/**
 * Generates styles/dark/legacy-generated.css: dark-mode colour overrides for
 * every stylesheet a JSP page or tag links, and for the <style> blocks in
 * JSP pages and tags.
 *
 * For each rule it keeps only the colour declarations and remaps the colours:
 * light surfaces and borders become dark, dark text becomes light, and
 * saturated mid-tone surfaces (the brand blues) are darkened until light text
 * on them reaches 4.5:1. Every colour declaration is emitted, changed or not,
 * so the generated rules keep the cascade of the originals: a hover rule
 * whose colour needs no change still beats its generated base rule.
 *
 * Each stylesheet's rules are wrapped in
 *   @scope (html.dark[data-stylesheets*="<sheet> "]) to (<user content>)
 * so they apply only in dark mode, only on pages that link that sheet, and
 * never inside user-authored rich text, which stays light.
 * common/legacyDarkMode.jsp lists the page's stylesheets in that attribute. The hand-written
 * styles/dark/legacy-dark.css loads after this file and fine-tunes it.
 *
 * Usage: node scripts/generate-legacy-dark-css.mjs [--check]
 *   --check  exit non-zero if the committed file is out of date.
 */
import { existsSync, globSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, posix } from "node:path";
import { fileURLToPath } from "node:url";
import { transform } from "lightningcss";

const WEBAPP = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "main", "webapp");
const OUTPUT = "styles/dark/legacy-generated.css";

/** Linked stylesheets that must not be converted. */
const IGNORED = [
  "styles/dark/legacy-generated.css",
  "styles/dark/legacy-dark.css",
  "styles/simplicity/print.css", // linked with media="print"
];

/**
 * JSP pages, tags and HTML templates that link stylesheets or hold <style>
 * blocks. The decorators come first: their stylesheets load before a page's
 * own, and the generated blocks keep that order so ties resolve as they do
 * in light mode.
 */
const DECORATORS = ["WEB-INF/decorators/default.jsp", "WEB-INF/decorators/externalPages.jsp"];
const TEMPLATES = [
  ...DECORATORS,
  ...globSync("**/*.{jsp,tag,jspf,html}", {
    cwd: WEBAPP,
    exclude: (path) => /^(ui|scripts\/(bower_components|tinymce)|node_modules)(\/|$)/.test(path),
  })
    .filter((path) => !DECORATORS.includes(path))
    .sort(),
];

/** Every stylesheet the templates link, as webapp-relative paths, in first-linked order. */
function linkedStylesheets() {
  const sheets = new Set();
  for (const file of TEMPLATES) {
    const source = readFileSync(join(WEBAPP, file), "utf8");
    for (const m of source.matchAll(/(?:assetUrl value=|href=|url value=)['"]\/([^'"?]+\.css)['"]/g)) {
      if (!IGNORED.includes(m[1])) sheets.add(m[1]);
    }
  }
  const missing = [...sheets].filter((sheet) => !existsSync(join(WEBAPP, sheet)));
  if (missing.length) throw new Error(`Linked stylesheets not found: ${missing.join(", ")}`);
  return [...sheets];
}

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
    if (neutral) return { ...hslToRgb(PANEL.h, PANEL.s, PANEL.l + (1 - l) * 0.6), a: c.a };
    return { ...darkenTo(h, Math.min(s, 0.45), PANEL.l + (1 - l) * 0.6), a: c.a };
  }
  if (y > ACCENT_MAX_LUMINANCE && s > 0.25) {
    // Map 0.1..0.45 onto 0.04..0.1 rather than clamping, so a hover shade
    // stays distinct from its base colour.
    const target = 0.04 + (ACCENT_MAX_LUMINANCE - 0.04) * ((y - ACCENT_MAX_LUMINANCE) / (0.45 - ACCENT_MAX_LUMINANCE));
    return { ...darkenTo(h, s, l, target), a: c.a };
  }
  return c;
}

/** Lowers the lightness until the luminance is at most `target`. */
function darkenTo(h, s, l, target = ACCENT_MAX_LUMINANCE) {
  let nl = l;
  let rgb = hslToRgb(h, s, nl);
  while (luminance(rgb) > target && nl > 0.03) {
    nl -= 0.005;
    rgb = hslToRgb(h, s, nl);
  }
  return rgb;
}

/** Light shadows (highlights) become dark; dark and coloured ones stay. */
function darkShadow(c) {
  return luminance(c) >= 0.45 ? darkSurface(c) : c;
}

/**
 * The legacy pages show keyboard focus as a dark grey glow, which vanishes on
 * a dark surface, so in focus rules dark greys become light.
 */
function focusShadow(c) {
  return luminance(c) < 0.2 && rgbToHsl(c).s < 0.15 ? lightText(c) : darkShadow(c);
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
  return value.replace(COLOR_RE, (m) => {
    const c = parseColor(m);
    return c ? format(fn(c)) : m;
  });
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
const TEXT_PROPS = new Set(["color", "fill", "stroke", "caret-color", "-webkit-text-fill-color"]);
/* Gradient arguments nest up to two levels, as in from(rgba(...)). */
const ARGS = "\\((?:[^()]|\\((?:[^()]|\\([^()]*\\))*\\))*\\)";
const IMAGE_RE = new RegExp(
  `(?:-webkit-|-moz-|-o-|-ms-)?(?:repeating-)?(?:linear|radial)-gradient${ARGS}|-webkit-gradient${ARGS}|url\\(\\s*(['"]?)[^)]*?\\1\\s*\\)`,
  "gi",
);

/**
 * Rewrites a relative url() in `file` so it resolves from the generated file.
 * The result stays relative, so it keeps any context path the app runs under.
 */
function relocateUrl(token, file) {
  return token.replace(/url\(\s*(['"]?)([^)'"]*)\1\s*\)/i, (whole, quote, path) => {
    if (/^(data:|https?:|\/|#)/i.test(path)) return whole;
    // A <style> block's relative urls resolve against the page URL, which
    // the generator cannot know.
    if (!file.endsWith(".css")) throw new Error(`${file}: relative url() in a <style> block: ${path}`);
    const target = posix.join(posix.dirname(file), path);
    return `url(${quote}${posix.relative(posix.dirname(OUTPUT), target)}${quote})`;
  });
}

/**
 * Gradients with a prefix no current browser renders. `-webkit-` gradients
 * render in Chromium, Firefox and Safari, and Firefox may still render
 * `-moz-` ones, so both are remapped like any other.
 */
const VENDOR_PREFIXED = /^-(o|ms)-/i;

/** Whether the value's only images are gradients with an ignored prefix. */
function onlyPrefixedImages(value) {
  const images = value.match(IMAGE_RE) ?? [];
  return images.length > 0 && images.every((token) => VENDOR_PREFIXED.test(token));
}

/** The background-image layers of a `background` shorthand, mapped. */
function backgroundImages(value, file) {
  const images = (value.match(IMAGE_RE) ?? [])
    .filter((token) => !VENDOR_PREFIXED.test(token))
    .map((token) => (/^url\(/i.test(token) ? relocateUrl(token, file) : mapColors(token, darkSurface)));
  return images.length ? images.join(", ") : "none";
}

/**
 * Returns the dark declarations for one original declaration: every colour
 * it sets, remapped or unchanged, so the generated rules keep the originals'
 * cascade. `focus` marks a rule whose selectors all match focused elements.
 */
function darkDeclarations(prop, value, important, file, focus) {
  const imp = important ? " !important" : "";
  const decl = (p, v) => [`${p}: ${v}${imp}`];
  // Old IE hacks (`#000\9`) are invalid in current browsers, so ignored there.
  if (/\\9\s*$/.test(value)) return [];
  if (TEXT_PROPS.has(prop)) return decl(prop, mapColors(value, lightText));
  if (prop === "background-color") return decl(prop, mapColors(value, darkSurface));
  if (prop === "background-image") {
    // Only images the browser ignores: the declaration has no effect.
    if (onlyPrefixedImages(value)) return [];
    return decl(prop, /^\s*none\s*$/i.test(value) ? "none" : backgroundImages(value, file));
  }
  if (prop === "background") {
    if (onlyPrefixedImages(value) && !value.replace(IMAGE_RE, " ").match(COLOR_RE)) return [];
    if (/^\s*(inherit|initial|unset)\s*$/i.test(value)) return [...decl("background-color", value), ...decl("background-image", value)];
    const rest = value.replace(IMAGE_RE, " ");
    const colour = rest.match(COLOR_RE)?.[0] ?? (/\btransparent\b/i.test(rest) ? "transparent" : null);
    return [
      ...decl("background-color", colour ? mapColors(colour, darkSurface) : "transparent"),
      ...decl("background-image", backgroundImages(value, file)),
    ];
  }
  if (prop === "text-shadow" || prop === "box-shadow") return decl(prop, mapColors(value, focus ? focusShadow : darkShadow));
  if (BORDER_PROPS.test(prop)) {
    const colourProp = prop.endsWith("-color") ? prop : `${prop}-color`;
    if (/^\s*(none|0|hidden)\s*$/i.test(value)) return [];
    if (prop.endsWith("-color")) return decl(colourProp, mapColors(value, darkBorder));
    const colours = value.match(COLOR_RE);
    const keyword = value.match(/\b(transparent|currentcolor|inherit|initial|unset)\b/i)?.[0];
    if (!colours) return decl(colourProp, keyword ?? "currentcolor");
    const mapped = colours.map((c) => mapColors(c, darkBorder));
    return decl(colourProp, prop === "border-color" ? mapped.join(" ") : mapped[0]);
  }
  return [];
}

/**
 * Containers of user-authored rich text. They stay light in dark mode, like
 * paper, because their content carries its own colours; every @scope block
 * stops at them. Must match the boundary in styles/dark/legacy-dark.css.
 */
const USER_CONTENT = [".textFieldViewModeDiv", ".journalPageContent"];

/** Anchors a selector to the scope root, <html class="dark">. */
function scopeSelector(sel) {
  if (/^:root\b/.test(sel)) return sel.replace(/^:root/, ":scope");
  if (/^html\b/.test(sel)) return sel.replace(/^html/, ":scope");
  return `:scope ${sel}`;
}

/** Drops repeated declarations, keeping the last one, which is the one that applies. */
function keepLast(declarations) {
  return declarations.filter((d, i) => declarations.lastIndexOf(d) === i);
}

const ELEMENT_ONLY = /^[a-z0-9*\s>+~:()-]+$/i;

function convertRules(css, file, skipElementOnly, stats) {
  const out = [];
  for (const { prelude, body } of parseBlocks(css)) {
    if (prelude.startsWith("@")) {
      if (/^@media\b/i.test(prelude) && !/\bprint\b/i.test(prelude)) {
        const inner = convertRules(body, file, skipElementOnly, stats);
        if (inner.length) out.push(`${prelude} {\n${inner.map((l) => `  ${l.replace(/\n/g, "\n  ")}`).join("\n")}\n}`);
      } else if (/^@supports\b/i.test(prelude)) {
        const inner = convertRules(body, file, skipElementOnly, stats);
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
    const focus = splitTopLevel(prelude, ",").every((s) => /:focus/.test(s));
    const dark = decls.flatMap(({ prop, value, important }) => darkDeclarations(prop, value, important, file, focus));
    if (!dark.length) continue;
    const selectors = splitTopLevel(prelude, ",").filter((s) => {
      if (/::?-(moz|ms|webkit)-/.test(s)) return false; // vendor pseudos invalidate whole rule lists
      if (skipElementOnly && ELEMENT_ONLY.test(s)) {
        stats.skipped.add(s);
        return false;
      }
      return true;
    });
    if (!selectors.length) continue;
    stats.rules++;
    out.push(`${selectors.map(scopeSelector).join(",\n")} {\n${keepLast(dark).map((d) => `  ${d};`).join("\n")}\n}`);
  }
  return out;
}

// ---------------------------------------------------------------- main

/** The <style> blocks of a template, skipping any that use JSP expressions. */
function inlineStyles(file) {
  const source = readFileSync(join(WEBAPP, file), "utf8").replace(/<%--[\s\S]*?--%>/g, "");
  return [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/gi)]
    .map((m) => m[1])
    .filter((css) => !/\$\{|<%/.test(css))
    .join("\n");
}

const indent = (text) => text.replace(/^(?=.)/gm, "  ");
const limit = `to (${USER_CONTENT.join(", ")})`;
const stats = { rules: 0, skipped: new Set() };
const sections = [];

/**
 * A stylesheet's rules, with those of its local @imports first, as they load.
 * `ancestors` guards against import cycles; a sheet imported twice is emitted
 * twice, as the browser applies it twice.
 */
function sheetRules(file, ancestors = []) {
  if (ancestors.includes(file)) throw new Error(`Import cycle: ${[...ancestors, file].join(" -> ")}`);
  const css = stripComments(readFileSync(join(WEBAPP, file), "utf8"));
  const imported = [...css.matchAll(/@import\s+(?:url\(\s*)?['"]?([^'")\s;]+)['"]?\s*\)?([^;]*);/gi)]
    .filter((m) => !/^(https?:|\/\/)/i.test(m[1]))
    .flatMap((m) => {
      if (m[2].trim()) throw new Error(`${file}: conditional @import is not supported: ${m[0]}`);
      const path = m[1].split("?")[0];
      const target = path.startsWith("/") ? path.slice(1) : posix.join(posix.dirname(file), path);
      if (!existsSync(join(WEBAPP, target))) throw new Error(`${file} imports a missing stylesheet: ${path}`);
      return sheetRules(target, [...ancestors, file]);
    });
  return [...imported, ...convertRules(css, file, false, stats)];
}

const sheets = linkedStylesheets();
for (const file of sheets) {
  const rules = sheetRules(file);
  if (rules.length) {
    sections.push(`/* ${file} */\n@scope (html.dark[data-stylesheets*="/${file} "]) ${limit} {\n${indent(rules.join("\n\n"))}\n}`);
  }
}
// <style> blocks cannot be tied to the pages that render them, so they apply
// on every page; their element-only selectors (such as \`th\`) are skipped.
const inline = TEMPLATES.flatMap((file) => {
  const rules = convertRules(stripComments(inlineStyles(file)), file, true, stats);
  return rules.length ? [`/* ${file} */\n\n${rules.join("\n\n")}`] : [];
});
if (inline.length) sections.push(`/* <style> blocks in templates */\n@scope (html.dark) ${limit} {\n${indent(inline.join("\n\n"))}\n}`);

const output = `/*
 * GENERATED by scripts/generate-legacy-dark-css.mjs. Do not edit; change the
 * script or override in legacy-dark.css, then run \`pnpm run css:legacy-dark\`.
 *
 * Dark-mode colour overrides for the legacy stylesheets. Each block applies
 * in dark mode on pages that link its source stylesheet, and stops at
 * user-authored rich text, which stays light.
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
  console.log(`Wrote ${OUTPUT}: ${stats.rules} rules from ${sheets.length} stylesheets and ${inline.length} templates; skipped ${stats.skipped.size} element-only selectors in <style> blocks.`);
}

# Frontend Translation Key Guidelines

How to name and organise i18next translation keys in `src/main/webapp/ui`.
Keep it boring and consistent so keys are easy to find, reuse, and lint.

## The setup in one paragraph

The shared i18next singleton lives in `src/modules/common/i18n/index.ts`.
English text is authored directly in the JSON catalogs under
`src/modules/common/i18n/locales/en-US/<namespace>.json`; **code carries keys
only, never English strings**. Config: `defaultNS: "common"`, `keySeparator:
"."`, `nsSeparator: ":"`. Types are generated from the catalogs, so every key
you reference is checked at compile time.

## 1. One namespace per module

Each catalog file is a namespace, and namespaces mirror the app's modules:

| Namespace   | Use for text in ...                                  |
| ----------- | ---------------------------------------------------- |
| `common`    | Shared/cross-cutting UI (buttons, dialogs, app bar, errors, anything reused across modules) |
| `workspace` | ELN workspace                                        |
| `inventory` | Inventory module                                     |
| `gallery`   | Gallery                                              |
| `apps`      | Apps / integrations pages                            |
| `groups`    | Groups                                               |
| `system`    | System / sysadmin                                    |
| `dashboard`, `admin`, `about`, `public` | their matching areas    |

Rule of thumb: **put a key in the module namespace where it is shown. Promote
it to `common` only when a second module needs the same text.** Do not
pre-emptively drop everything into `common`.

## 2. Key naming

Nest by feature/component, then describe what the text *is* at the leaf:

```
<feature>.<subFeature>.<leaf>
```

- Segments are `camelCase` (`emptyTablePlaceholder`, `insertTooltip`).
- The leaf names the string's role, not its English words: prefer
  `validation.nameRequired` over `pleaseEnterAName`. The wording can change; the
  key should not.
- Group by the component or feature that owns the text, e.g. from `common`:

  ```json
  "stoichiometry": {
    "addReagent": {
      "title": "Add New Chemical",
      "validation": { "nameRequired": "Name is required" }
    },
    "dialog": { "reactionTable": "Reaction table" }
  }
  ```

- Reuse generic leaves that already exist (`actions.save`, `actions.cancel`,
  `alerts.*`) instead of minting `saveButton` / `cancelBtn` variants. Check the
  `common` groups (`actions`, `alerts`, `inputs`, `confirmationDialog`, ...)
  before adding a new one.

## 3. Referencing keys in code

Inside a React component, use the hook and let the default namespace resolve
bare keys:

```tsx
const { t } = useTranslation();          // defaultNS = common
t("actions.save");                        // common key
t("inventory:sample.namePlaceholder");    // explicit namespace
```

Preload a non-default namespace once at the component root:

```tsx
const { t } = useTranslation("inventory");
```

Outside React (services, TinyMCE plugins, plain modules) there is no hook, so
call the singleton and **prefix the namespace**:

```ts
import i18n from "@/modules/common/i18n";
i18n.t("common:stoichiometry.plugin.insertTooltip");
```

Prefer the `common:` prefix over `getFixedT(null, "common")`; it reads the same
and keeps full key type-checking.

## 4. Do not construct keys dynamically

Prefer whole, literal keys. Do not build a key from string parts or select one
inside the `t()` call with a ternary, because dynamic keys defeat static
extraction (`i18n:check` cannot see them), type-checking, and "find usages".

```ts
// avoid: key assembled/branched at the call site
t(`errors.${code}`);
t(isPi ? "role.pi" : "role.user");

// prefer: branch between whole t() calls
isPi ? t("role.pi") : t("role.user");
```

When a key genuinely must be chosen at runtime (e.g. an enum -> message map),
map to literal keys with `as const` so every key stays statically visible and
the union stays type-checked:

```ts
const keys = {
  missingLink: "common:stoichiometry.inventoryUpdate.linkRequired",
  insufficientStock: "common:stoichiometry.inventoryLink.insufficientStock",
} as const;
return i18n.t(keys[reason]);
```

## 5. Interpolation, never concatenation

Do not build sentences by joining translated fragments (word order differs
between languages). Use placeholders / ICU:

```json
{ "itemsSelected": "{count, plural, one {# item} other {# items}} selected" }
```

For text with inline markup (links, `<strong>`), use `TransRichText` rather
than splitting the string.

## 6. Workflow when you add or change keys

Run from `src/main/webapp/ui` (the root `i18n:*` scripts `cd` here for you):

```bash
pnpm run i18n:check   # extract: writes new keys (empty) into the catalogs
# fill in the English text in locales/en-US/<namespace>.json
pnpm run i18n:types   # regenerate i18next.d.ts / resources.d.ts
pnpm run i18n:lint    # flag missing / malformed keys
pnpm run tsc          # confirm key references type-check
```

`i18n:check` never overwrites existing English values and never deletes unused
keys, so it is safe to run often. Keys in `__tests__`, `*.test.*`, `*.spec.*`,
and `*.story.*` are ignored by extraction and lint.

## 7. Translator workflow

Translation catalogs live under
`src/modules/common/i18n/locales/<language>/<namespace>.json`. `en-US` is the
source language. Other languages must keep the same file names, object shape,
and keys as `en-US`; only values are translated.

### Adding a new language

Use a BCP 47 language tag such as `fr-FR` or `de-DE`.

1. Add the language tag to `locales` in `src/main/webapp/ui/i18next.config.ts`.
2. Add the same tag to `supportedLngs` in
   `src/main/webapp/ui/src/modules/common/i18n/index.ts`.
3. Create `src/modules/common/i18n/locales/<language>/`.
4. Copy every JSON namespace from `locales/en-US/` into the new language
   folder and translate the values.
5. Run:

   ```bash
   pnpm run i18n:lint
   pnpm run i18n:status
   pnpm run tsc
   ```

Keep `fallbackLng: "en-US"` unless product behaviour has been explicitly
changed to use a different fallback.

### Translating new feature strings

For a feature added after a language already exists:

1. Run `pnpm run i18n:check` so new keys are present in the catalogs.
2. Run `pnpm run i18n:status` to see untranslated or missing values.
3. Translate the new values in every non-`en-US` locale.
4. Run `pnpm run i18n:lint` before handing the translation back.

Do not rename keys to match the translated wording. If English changes but the
meaning stays the same, keep the key and update the values.

### What translators must preserve

- Preserve JSON keys, nesting, punctuation required by JSON, and namespace file
  names.
- Preserve interpolation placeholders exactly: `{name}`, `{count}`, `{serverAlias}`.
- Preserve ICU syntax and option names exactly, e.g.
  `{count, plural, one {...} other {...}}` and `{enabled, select, yes {...} other {...}}`.
- Preserve rich-text tag names and required attributes. Translate only the
  human-readable text between tags unless the URL or documentation target
  genuinely differs for the target language.
- Do not introduce arbitrary HTML. Supported rich-text tags are provided by
  `TransRichText`, including `<strong>`, `<br/>`, `<code>`, `<kbd>`, lists,
  `<internalLink to="...">`, `<externalLink href="...">`, and
  `<helpDocs docLink="...">`.
- Keep product names, integration names, API names, and legal terms unchanged
  unless there is an approved localized name.

### Links and documentation targets

`common.help` is a central map of HelpDocs article targets. Values are HelpDocs
path segments, optionally with an anchor, e.g.
`"c8sxesdqpy-create-a-template#update_all_of_your_samples_to_latest_template_version"`.

Treat each `common.help` value as one translatable documentation target:

- If the target language uses the same HelpDocs article and anchor, copy the
  `en-US` value unchanged.
- If the target language has localized HelpDocs pages, replace the whole value
  with the localized `slug#anchor`.
- Keep the slug and anchor together in `common.help`; do not put raw HelpDocs
  slugs in feature strings.
- In prose, link to documentation with `<helpDocs docLink="commonHelpKey">...`
  rather than a raw URL or `<a>` tag.

Internal app links use `<internalLink to="/path">...`; external web links use
`<externalLink href="https://...">...`. The renderer controls router
transitions and safe external-link attributes, so translators should not add
`target`, `rel`, or raw `<a>` tags to catalog values.

## 8. The `noJsxLiterals` lint rule is the safety net

Biome's `style/noJsxLiterals` rule is enabled as an **error** (see
`src/main/webapp/ui/biome.jsonc`) specifically to catch untranslated text: a raw
string literal sitting in JSX means someone rendered English directly instead of
going through a translation key. Treat a `noJsxLiterals` failure as "this text
needs a key", not as noise to silence.

```tsx
// fails noJsxLiterals — hard-coded English
<Button>Save</Button>

// passes — resolved from a key
<Button>{t("actions.save")}</Button>
```

Do not suppress it with a disable comment to sneak literal text through. The
rare legitimate exception (a symbol or non-word glyph) is handled with the
rule's `allowedStrings` option in a scoped `overrides` entry, as done for
`IdentifierPublicPage.tsx` (`"˚"`). If you find yourself wanting to disable it
for actual words, add a translation key instead.

## Checklist for a new string

1. Which module shows it? Use that namespace (or `common` if genuinely shared).
2. Does an equivalent key already exist? Reuse it.
3. Name it `feature.subFeature.roleOfText` in `camelCase`.
4. Reference via `t()` (component) or `i18n.t("ns:key")` (outside React).
5. Interpolate variables; do not concatenate.
6. Run `i18n:check` → fill English → `i18n:types` → `i18n:lint` → `tsc`.

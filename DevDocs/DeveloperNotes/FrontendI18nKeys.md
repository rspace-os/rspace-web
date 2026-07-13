# Frontend Translation Key Guidelines

How to name and organise i18next translation keys in `src/main/webapp/ui`.
Keep keys plain and consistent so they are easy to find, reuse, and lint.

## The setup in one paragraph

The shared i18next singleton lives in `src/modules/common/i18n/index.ts`.
English text lives in JSON catalogs under
`src/modules/common/i18n/locales/en-US/<namespace>.json`; **code uses keys, not
English strings**. Config: `defaultNS: "common"`, `keySeparator: "."`,
`nsSeparator: ":"`. Types are generated from the catalogs, so key references
are checked at compile time.

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

Rule of thumb: **put a key in the namespace for the module that shows it. Move
it to `common` only when another module needs the same text.** Do not put keys
in `common` just in case.

## 2. Key naming

Nest by feature or component, then name what the text is *for*:

```
<feature>.<subFeature>.<leaf>
```

- Segments are `camelCase` (`emptyTablePlaceholder`, `insertTooltip`).
- The leaf names the string's role, not its English wording. Prefer
  `validation.nameRequired` over `pleaseEnterAName`. Wording can change; keys
  should not.
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

- Reuse generic keys that already exist (`actions.save`, `actions.cancel`,
  `alerts.*`) instead of adding `saveButton` or `cancelBtn` variants. Check the
  `common` groups (`actions`, `alerts`, `inputs`, `confirmationDialog`, ...)
  before adding a new key.

## 3. Referencing keys in code

Inside a React component, use the hook. Bare keys use the default namespace:

```tsx
const { t } = useTranslation();          // defaultNS = common
t("actions.save");                        // common key
t("inventory:sample.namePlaceholder");    // explicit namespace
```

Preload a non-default namespace once at the component root:

```tsx
const { t } = useTranslation("inventory");
```

Outside React (services, TinyMCE plugins, plain modules), call the singleton
and **prefix the namespace**:

```ts
import i18n from "@/modules/common/i18n";
i18n.t("common:stoichiometry.plugin.insertTooltip");
```

Prefer the `common:` prefix over `getFixedT(null, "common")`. It is clearer
and keeps full key type-checking.

## 4. Do not construct keys dynamically

Use full, literal keys. Do not build keys from string parts or choose a key
inside `t()` with a ternary. Dynamic keys are hard to extract, type-check, and
search for.

```ts
// avoid: key assembled/branched at the call site
t(`errors.${code}`);
t(isPi ? "role.pi" : "role.user");

// prefer: branch between whole t() calls
isPi ? t("role.pi") : t("role.user");
```

When a key must be chosen at runtime, such as from an enum, map to literal keys
with `as const`. This keeps every key visible and type-checked:

```ts
const keys = {
  missingLink: "common:stoichiometry.inventoryUpdate.linkRequired",
  insufficientStock: "common:stoichiometry.inventoryLink.insufficientStock",
} as const;
return i18n.t(keys[reason]);
```

## 5. Interpolation, never concatenation

Do not build sentences by joining translated fragments. Word order differs
between languages. Use placeholders / ICU:

```json
{ "itemsSelected": "{count, plural, one {# item} other {# items}} selected" }
```

For text with inline markup (links, `<strong>`), use `TransRichText` rather
than splitting the string.

### Placeholder names must match exactly, on both sides

This project uses ICU interpolation, so placeholders use **single braces**
(`{types}`), not react-i18next double braces (`{{types}}`).

The placeholder name in the JSON must exactly match the object key passed to
`t()`. If they do not match, there is no error. The user sees the placeholder
text instead:

```json
"canStoreOnly": "This container can only store {types}."
```

```ts
// wrong: key does not match the placeholder name; renders
// "This container can only store {types}." verbatim
t("moveToTarget.messages.canStoreOnly", { canStoreLabel });

// right
t("moveToTarget.messages.canStoreOnly", { types: canStoreLabel });
```

The same thing happens with unused variables. If you remove a placeholder from
the English text, but the call still passes a value such as `count`, `placed`,
or `total`, that value is ignored with no error or lint warning.

`i18n:lint` checks key structure. It does not check placeholder names against
the arguments passed to `t()`. **After adding or editing an interpolated
message, click through the feature in a browser.**

## 6. Workflow when you add or change keys

Run from `src/main/webapp/ui` (the root `i18n:*` scripts `cd` here for you):

```bash
pnpm run i18n:check   # extract: writes new keys (empty) into the catalogs
# fill in the English text in locales/en-US/<namespace>.json
pnpm run i18n:types   # regenerate i18next.d.ts / resources.d.ts
pnpm run i18n:lint    # flag missing / malformed keys
pnpm run tsc          # confirm key references type-check
```

`i18n:check` does not overwrite existing English values or delete unused keys,
so it is safe to run often. Extraction and lint ignore keys in `__tests__`,
`*.test.*`, `*.spec.*`, and `*.story.*`.

## 7. Translator workflow

Translation catalogs live under
`src/modules/common/i18n/locales/<language>/<namespace>.json`. `en-US` is the
source language. Other languages must keep the same file names, object shape,
and keys as `en-US`. Only values are translated.

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

Do not rename keys to match translated wording. If the English wording changes
but the meaning stays the same, keep the key and update the values.

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

Treat each `common.help` value as one documentation target:

- If the target language uses the same HelpDocs article and anchor, copy the
  `en-US` value unchanged.
- If the target language has localized HelpDocs pages, replace the whole value
  with the localized `slug#anchor`.
- Keep the slug and anchor together in `common.help`; do not put raw HelpDocs
  slugs in feature strings.
- In prose, link to documentation with `<helpDocs docLink="commonHelpKey">...`
  rather than a raw URL or `<a>` tag.

Internal app links use `<internalLink to="/path">...`; external web links use
`<externalLink href="https://...">...`. The renderer handles routing and safe
external-link attributes, so translators should not add `target`, `rel`, or raw
`<a>` tags to catalog values.

## 8. The `noJsxLiterals` lint rule is the safety net

Biome's `style/noJsxLiterals` rule is an **error** (see
`src/main/webapp/ui/biome.jsonc`) so untranslated JSX text gets caught. A raw
string in JSX means English was rendered directly instead of through a
translation key. Treat a `noJsxLiterals` failure as "this text needs a key",
not as noise to silence.

```tsx
// fails noJsxLiterals — hard-coded English
<Button>Save</Button>

// passes — resolved from a key
<Button>{t("actions.save")}</Button>
```

Do not suppress this rule to allow literal text. Rare exceptions, such as a
symbol or non-word glyph, belong in the rule's `allowedStrings` option in a
scoped `overrides` entry. `IdentifierPublicPage.tsx` does this for `"˚"`. If
you want to disable the rule for real words, add a translation key instead.

## 9. Common pitfalls

- **Placeholder name mismatch** between the JSON template and the object passed
  to `t()`. The user sees the literal `{placeholder}` instead of an error. See
  section 5.
- **Unused variables** after simplifying English text. If the ICU string no
  longer uses `count`, `placed`, or `total`, that value disappears from the
  message.
- **Forgetting `i18n:types` after editing JSON.** `tsc` can then pass against
  stale key names or fail on a key that exists.
- **Hand-typing a new key into JSON first.** Write the `t()` call, then run
  `i18n:check` or `i18n:extract`. This avoids typos, wrong nesting, and keys
  that do not match the code.

## Checklist for a new string

1. Which module shows it? Use that namespace (or `common` if genuinely shared).
2. Does an equivalent key already exist? Reuse it.
3. Name it `feature.subFeature.roleOfText` in `camelCase`.
4. Reference via `t()` (component) or `i18n.t("ns:key")` (outside React).
5. Interpolate variables; do not concatenate.
6. Run `i18n:check` → fill English → `i18n:types` → `i18n:lint` → `tsc`.

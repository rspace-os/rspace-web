import { Editor } from "@tinymce/tinymce-react";
import type React from "react";
import type { RawEditorOptions } from "tinymce";

/*
 * The Inventory rich-text editor uses a self-hosted TinyMCE 8 build that is
 * loaded lazily at runtime rather than bundled. `tinymceScriptSrc` points at
 * the TinyMCE assets served from the build output under `/ui/dist/tinymce/`
 * (see the `rspace:tinymce-assets` plugin in vite.config.ts, which serves them
 * in dev and copies them into `dist` for production). The wrapper injects
 * tinymce.min.js from there; TinyMCE then derives its base URL from that
 * script's location and lazy-loads its model, theme, icons, skin and plugins
 * from the same directory at editor-init time.
 *
 * This replaces an earlier approach that imported the model/theme/plugins as
 * bundler side effects. Under the bundler those side effects did not reliably
 * register the `dom` model, so TinyMCE fell back to fetching
 * `models/dom/model.js` from a wrong base URL (a 404). Loading the build as
 * served static files also keeps TinyMCE's minified skin CSS away from
 * lightningcss, which rejects its `:nth-child(2of...)` selector.
 *
 * The legacy document/notebook editor uses a separate global TinyMCE 5 and is
 * unaffected: TinyMCE 5 and 8 are never present on the same page.
 *
 * Cache-busting: the assets are served from a stable path, so a `?v=<version>`
 * query (keyed on the bundled TinyMCE version, injected by Vite) is appended to
 * invalidate browser/proxy caches on upgrade, matching RSpace's `?v=<token>`
 * asset convention. The script src carries it for the main script, and
 * `cache_suffix` makes TinyMCE append the same query to every resource it
 * lazy-loads (model, theme, icons, skin, plugins).
 */
const TINYMCE_CACHE_SUFFIX = `?v=${__TINYMCE_VERSION__}`;
// `__TINYMCE_BASE__` is injected by the bundler and is the full directory URL
// the self-hosted TinyMCE assets are served from (always ending in a slash):
// "/ui/dist/tinymce/" for the app build, and "/" for the Playwright CT build
// (where the assets are served at the server root via Vite's publicDir).
const TINYMCE_SCRIPT_SRC = `${__TINYMCE_BASE__}tinymce.min.js${TINYMCE_CACHE_SUFFIX}`;

const customStyles =
  `.mce-content-body {
    font-family: "Roboto", "Helvetica", sans-serif;
  }` +
  `.mce-content-body:not(.mce-content-readonly) {
  border-radius: 4px;
  padding: 0px 14px;
  word-break: break-word;
}` +
  `.tox-editor-header { position: static !important; }`;

export default function StyledTinyMceEditor({
  init,
  ...props
}: {
  init: RawEditorOptions;
} & React.ComponentProps<typeof Editor>): React.ReactNode {
  return (
    <Editor
      tinymceScriptSrc={TINYMCE_SCRIPT_SRC}
      /*
       * TinyMCE 8 requires a license key for self-hosted deployments. RSpace
       * self-hosts under the GPL with only open-source plugins, so the key is
       * the literal "gpl". The @tinymce/tinymce-react integration owns
       * `init.license_key`, so it is supplied via this prop.
       * https://www.tiny.cloud/docs/tinymce/latest/license-key/
       */
      licenseKey="gpl"
      init={{
        ...init,

        // Append the same cache-busting query to every resource TinyMCE
        // lazy-loads (model, theme, icons, skin, plugins).
        cache_suffix: TINYMCE_CACHE_SUFFIX,

        // see https://www.tiny.cloud/docs/tinymce/latest/autoresize/
        autoresize: true,
        min_height: 100,
        max_height: 500,

        /*
         * This uses the default tinymce blue, which is a bit darker than our
         * primary blue. Unfortunately, the only way to change this seems to be
         * by defining a custom skin, which doesn't seem worth it for such a
         * small thing.
         * https://www.tiny.cloud/docs/tinymce/latest/content-appearance/#using-highlight_on_focus-with-custom-skins
         */
        highlight_on_focus: true,

        branding: false,
        content_css: false,
        content_style: [init.content_style || "", customStyles].join("\n"),
      }}
      {...props}
    />
  );
}

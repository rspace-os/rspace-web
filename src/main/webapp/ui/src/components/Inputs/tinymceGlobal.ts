import type { TinyMCE } from "tinymce";

/*
 * The Inventory editor's TinyMCE 8 build is now loaded lazily at runtime (see
 * StyledTinyMceEditor.tsx), and @tinymce/tinymce-react assigns the loaded
 * instance to `window.tinymce`. This module no longer sets that global itself;
 * it only augments the global type so that code reading `window.tinymce`
 * (notably the legacy document-editor TinyMCE integrations under src/tinyMCE)
 * type-checks. It emits no runtime code.
 */
declare global {
  interface Window {
    tinymce: TinyMCE;
  }

  /**
   * The bundled TinyMCE package version, injected at build time by Vite's
   * `define` (see vite.config.ts). Used as the `?v=` cache-busting token for
   * the lazily-loaded TinyMCE assets in StyledTinyMceEditor.tsx.
   */
  const __TINYMCE_VERSION__: string;

  /**
   * The full directory URL the self-hosted TinyMCE assets are served from,
   * injected at build time (the app build uses "/ui/dist/tinymce/", the
   * Playwright component-test build uses "/"). Always ends in a slash.
   */
  const __TINYMCE_BASE__: string;
}

export {};

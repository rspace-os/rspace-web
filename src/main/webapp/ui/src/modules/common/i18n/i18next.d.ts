import "i18next";
import type Resources from "./resources";

/**
 * Wires the generated `resources.d.ts` into i18next for per-key type-checking.
 * Re-run `pnpm run i18n:types` whenever catalogs change; never hand-edit
 * `resources.d.ts`.
 */
declare module "i18next" {
  interface CustomTypeOptions {
    defaultNS: "common";
    returnNull: false;
    resources: Resources;
  }
}

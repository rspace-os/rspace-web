import { defineConfig } from "i18next-cli";

/**
 * i18next-cli config. Run from `src/main/webapp/ui` (the root `i18n:*` scripts
 * cd here first). `en-US` is the primary/base language; its English text is
 * authored directly in the catalogs (code carries keys only).
 */
export default defineConfig({
  locales: ["en-US"],
  extract: {
    input: ["src/**/*.{ts,tsx}"],
    ignore: [
      "**/__tests__/**",
      "**/*.test.*",
      "**/*.spec.*",
      "**/*.story.*",
      "src/test-stubs/**",
      "src/__tests__/**",
      "src/modules/common/i18n/locales/**",
      "src/modules/common/i18n/i18next.d.ts",
      "src/modules/common/i18n/resources.d.ts",
    ],
    output: "src/modules/common/i18n/locales/{{language}}/{{namespace}}.json",
    primaryLanguage: "en-US",
    defaultNS: "common",
    keySeparator: ".",
    nsSeparator: ":",
    removeUnusedKeys: true,
    extractFromComments: false,
    // extract adds new keys empty and preserves existing primary values
    // (it never syncs from code unless run with --sync-primary).
  },
  // `pnpm run i18n:types` regenerates these after any key change.
  types: {
    input: ["src/modules/common/i18n/locales/en-US/*.json"],
    output: "src/modules/common/i18n/i18next.d.ts",
    resourcesFile: "src/modules/common/i18n/resources.d.ts",
  },
  lint: {
    ignore: ["**/__tests__/**", "**/*.test.*", "**/*.spec.*", "**/*.story.*", "src/test-stubs/**"],
  },
});

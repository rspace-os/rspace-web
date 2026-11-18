import i18next from "i18next";
import ICU from "i18next-icu";
import { initReactI18next } from "react-i18next";
import { localeBackend, sharedInitOptions } from "./i18nOptions";

/**
 * Shared i18next singleton. Every entry bundle must import this module for its
 * side effect so the instance is initialised before any `useTranslation` runs.
 * Only `common` loads at init; other namespaces load on demand (Vite
 * code-splits one chunk per namespace).
 */
void i18next
  .use(ICU)
  .use(localeBackend)
  .use(initReactI18next)
  .init({
    ...sharedInitOptions,
    react: { useSuspense: true },
  });

export default i18next;

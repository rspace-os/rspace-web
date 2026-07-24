import i18next from "i18next";
import ICU from "i18next-icu";
import resourcesToBackend from "i18next-resources-to-backend";
import { initReactI18next } from "react-i18next";

/**
 * Shared i18next singleton. Every entry bundle must import this module for its
 * side effect so the instance is initialised before any `useTranslation` runs.
 * Only `common` loads at init; other namespaces load on demand (Vite
 * code-splits one chunk per namespace).
 */
void i18next
  .use(ICU)
  .use(resourcesToBackend((language: string, namespace: string) => import(`./locales/${language}/${namespace}.json`)))
  .use(initReactI18next)
  .init({
    lng: document.documentElement.lang || "en-US",
    fallbackLng: "en-US",
    load: "currentOnly",
    defaultNS: "common",
    ns: ["common"],
    keySeparator: ".",
    nsSeparator: ":",
    returnNull: false,
    returnEmptyString: false,
    interpolation: { escapeValue: false },
    react: { useSuspense: true },
  });

export default i18next;

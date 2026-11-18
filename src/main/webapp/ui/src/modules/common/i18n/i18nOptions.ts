import type { InitOptions } from "i18next";
import resourcesToBackend from "i18next-resources-to-backend";

/** Loads one namespace of one language; Vite code-splits one chunk per namespace. */
export const localeBackend = resourcesToBackend(
  (language: string, namespace: string) => import(`./locales/${language}/${namespace}.json`),
);

/** Options shared by the React singleton and the plain instance for legacy pages. */
export const sharedInitOptions = {
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
} as const satisfies InitOptions;

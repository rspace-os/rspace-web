import { createInstance, type i18n as I18nInstance } from "i18next";
import ICU from "i18next-icu";
import type React from "react";
import { I18nextProvider } from "react-i18next";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import inventory from "@/modules/common/i18n/locales/en-US/inventory.json";

/**
 * An i18next instance carrying the real en-US catalogs, for the few tests that must see the
 * assembled English sentence rather than its key.
 *
 * The global test setup puts the shared singleton in cimode, which returns keys and drops every
 * interpolated parameter, so a test there cannot tell "{origin}: {amount}" from a message that
 * silently lost its arguments. This instance is deliberately separate: it is passed through
 * I18nextProvider rather than initReactI18next, so it never becomes react-i18next's default and
 * cannot change what any other test sees.
 */
export function createEnglishI18n(): I18nInstance {
  const instance = createInstance();
  void instance.use(ICU).init({
    lng: "en-US",
    fallbackLng: "en-US",
    defaultNS: "common",
    ns: ["common", "inventory"],
    resources: { "en-US": { common, inventory } },
    keySeparator: ".",
    nsSeparator: ":",
    returnNull: false,
    returnEmptyString: false,
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
  });
  return instance;
}

/** Wraps `children` in a provider serving the real en-US catalogs. */
export function InEnglish({ children }: { children: React.ReactNode }): React.ReactElement {
  return <I18nextProvider i18n={createEnglishI18n()}>{children}</I18nextProvider>;
}

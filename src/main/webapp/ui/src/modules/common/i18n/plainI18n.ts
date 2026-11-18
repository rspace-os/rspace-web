import i18next from "i18next";
import ICU from "i18next-icu";
import { localeBackend, sharedInitOptions } from "./i18nOptions";

/**
 * An i18next instance without React, for small entries that legacy JSP pages
 * load beside other bundles. It reads the same catalogs as the React singleton.
 */
export function createPlainI18n(): typeof i18next {
  const instance = i18next.createInstance();
  void instance.use(ICU).use(localeBackend).init(sharedInitOptions);
  return instance;
}

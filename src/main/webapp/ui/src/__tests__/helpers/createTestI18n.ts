import { createInstance, type i18n as I18nInstance, type ResourceLanguage } from "i18next";
import ICU from "i18next-icu";

export type TestI18nResources = Record<string, ResourceLanguage>;

export async function createTestI18n(resources: TestI18nResources, defaultNS: string): Promise<I18nInstance> {
  const i18n = createInstance();
  await i18n.use(ICU).init({
    lng: "en-US",
    fallbackLng: "en-US",
    supportedLngs: ["en-US"],
    load: "currentOnly",
    defaultNS,
    ns: Object.keys(resources),
    keySeparator: ".",
    nsSeparator: ":",
    returnNull: false,
    returnEmptyString: false,
    resources: {
      "en-US": resources,
    },
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
  });
  return i18n;
}

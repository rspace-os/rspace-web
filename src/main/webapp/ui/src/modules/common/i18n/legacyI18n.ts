import type IntlMessageFormatType from "intl-messageformat";
import type { PrimitiveType } from "intl-messageformat";

declare global {
  var RSpaceIntlMessageFormat: typeof IntlMessageFormatType;
}

const legacyWindow = window as Window & {
  RS?: {
    i18n?: Record<string, string>;
    formatIcuMessage?: (pattern: string, args: PrimitiveType[]) => string;
  };
};

legacyWindow.RS = legacyWindow.RS || {};
// Vite replaces this placeholder with the flattened server.legacyJs.json catalogues.
const catalogues = "__LEGACY_I18N_CATALOGUES__" as unknown as Record<string, Record<string, string>>;
const locale = document.documentElement.lang || "en-US";
legacyWindow.RS.i18n = selectLegacyCatalogue(catalogues, locale);

const formatterCache = new Map<string, IntlMessageFormatType>();

export function selectLegacyCatalogue(
  availableCatalogues: Record<string, Record<string, string>>,
  languageTag: string,
): Record<string, string> {
  return (
    availableCatalogues[languageTag] ??
    availableCatalogues[languageTag.split("-")[0]] ??
    availableCatalogues["en-US"] ??
    {}
  );
}

export function formatIcuMessage(pattern: string, args: PrimitiveType[]): string {
  const locale = document.documentElement.lang || navigator.language || "en";
  const cacheKey = `${locale}\0${pattern}`;
  let formatter = formatterCache.get(cacheKey);
  if (!formatter) {
    formatter = new globalThis.RSpaceIntlMessageFormat(pattern, locale, undefined, { ignoreTag: true });
    formatterCache.set(cacheKey, formatter);
  }
  const values = Object.fromEntries(args.map((value, index) => [index, value]));
  return formatter.format(values) as string;
}

legacyWindow.RS.formatIcuMessage = formatIcuMessage;

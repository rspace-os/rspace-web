import type IntlMessageFormatType from "intl-messageformat";
import type { PrimitiveType } from "intl-messageformat";
import { formatList as formatLocalizedList } from "./listFormat";

declare global {
  var RSpaceIntlMessageFormat: typeof IntlMessageFormatType;
}

// The ICU formatting half of `RS.msg`; `RS.i18n` comes from a separate script rendered alongside.

const legacyWindow = window as Window & {
  RS?: {
    i18n?: Record<string, string>;
    formatIcuMessage?: (pattern: string, args: PrimitiveType[]) => string;
    formatList?: (items: Array<string | number>) => string;
  };
};

legacyWindow.RS = legacyWindow.RS || {};

const locale = document.documentElement.lang || "en-US";
const formatterCache = new Map<string, IntlMessageFormatType>();

function formatIcuMessage(pattern: string, args: PrimitiveType[]): string {
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
legacyWindow.RS.formatList = (items) => formatLocalizedList(items, locale);

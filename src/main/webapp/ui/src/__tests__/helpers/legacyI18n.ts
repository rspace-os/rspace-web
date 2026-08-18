import { flattenMessages } from "@/modules/common/i18n/flattenMessages";
// Imported for its side effects: the entry exports nothing and installs the formatter on `window.RS`.
import "@/modules/common/i18n/legacyI18n";
import catalogue from "@/modules/common/i18n/locales/en-US/server.legacyJs.json";

type LegacyArg = string | number | boolean | null | undefined;

// Read once, here, rather than per call: tests routinely reassign `window.RS` wholesale to stub
// `RS.msg`, which would otherwise take the formatter with it.
const formatIcuMessage = (window as Window & { RS?: { formatIcuMessage?: (p: string, a: LegacyArg[]) => string } }).RS
  ?.formatIcuMessage;

if (!formatIcuMessage) {
  throw new Error("legacyI18n did not install RS.formatIcuMessage");
}

// Stands in for the catalogue script the server renders.
const messages = flattenMessages(catalogue);

export const legacyMsg = (key: string, ...args: unknown[]): string =>
  formatIcuMessage(messages[key] ?? key, args as LegacyArg[]);

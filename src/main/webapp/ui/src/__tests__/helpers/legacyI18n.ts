import { flattenMessages } from "@/modules/common/i18n/flattenMessages";
import { formatIcuMessage } from "@/modules/common/i18n/legacyI18n";
import catalogue from "@/modules/common/i18n/locales/en-US/server.legacyJs.json";

const messages = flattenMessages(catalogue);

export const legacyMsg = (key: string, ...args: unknown[]): string =>
  formatIcuMessage(messages[key] ?? key, args as Array<string | number | boolean | null | undefined>);

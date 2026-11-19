import type { i18n } from "i18next";
import * as v from "valibot";
import type Resources from "./resources";

const NAMESPACE = "valibot";

type MessageKey = keyof Resources["valibot"];

/**
 * The Valibot actions that the `valibot` catalogue has a message for, by
 * catalogue key. Every other key in the catalogue is `schema`, the message
 * for all schema issues. The catalogue mirrors `@valibot/i18n`, so
 * `scripts/import-valibot-translations.ts` can fill it in for each language.
 */
const actions = {
  base64: v.base64,
  bic: v.bic,
  bytes: v.bytes,
  check: v.check,
  checkAsync: v.checkAsync,
  checkItems: v.checkItems,
  checkItemsAsync: v.checkItemsAsync,
  codePoints: v.codePoints,
  creditCard: v.creditCard,
  cuid2: v.cuid2,
  decimal: v.decimal,
  digits: v.digits,
  domain: v.domain,
  email: v.email,
  emoji: v.emoji,
  empty: v.empty,
  endsWith: v.endsWith,
  entries: v.entries,
  everyItem: v.everyItem,
  excludes: v.excludes,
  finite: v.finite,
  graphemes: v.graphemes,
  gtValue: v.gtValue,
  guard: v.guard,
  hash: v.hash,
  hexColor: v.hexColor,
  hexadecimal: v.hexadecimal,
  imei: v.imei,
  includes: v.includes,
  integer: v.integer,
  ip: v.ip,
  ipv4: v.ipv4,
  ipv6: v.ipv6,
  isbn: v.isbn,
  isoDate: v.isoDate,
  isoDateTime: v.isoDateTime,
  isoDateTimeSecond: v.isoDateTimeSecond,
  isoTime: v.isoTime,
  isoTimeSecond: v.isoTimeSecond,
  isoTimestamp: v.isoTimestamp,
  isoWeek: v.isoWeek,
  isrc: v.isrc,
  jwsCompact: v.jwsCompact,
  ksuid: v.ksuid,
  length: v.length,
  ltValue: v.ltValue,
  mac: v.mac,
  mac48: v.mac48,
  mac64: v.mac64,
  maxBytes: v.maxBytes,
  maxCodePoints: v.maxCodePoints,
  maxEntries: v.maxEntries,
  maxGraphemes: v.maxGraphemes,
  maxLength: v.maxLength,
  maxSize: v.maxSize,
  maxValue: v.maxValue,
  maxWords: v.maxWords,
  mimeType: v.mimeType,
  minBytes: v.minBytes,
  minCodePoints: v.minCodePoints,
  minEntries: v.minEntries,
  minGraphemes: v.minGraphemes,
  minLength: v.minLength,
  minSize: v.minSize,
  minValue: v.minValue,
  minWords: v.minWords,
  multipleOf: v.multipleOf,
  nanoid: v.nanoid,
  nonEmpty: v.nonEmpty,
  notBytes: v.notBytes,
  notCodePoints: v.notCodePoints,
  notEntries: v.notEntries,
  notGraphemes: v.notGraphemes,
  notLength: v.notLength,
  notSize: v.notSize,
  notValue: v.notValue,
  notValues: v.notValues,
  notWords: v.notWords,
  octal: v.octal,
  parseBoolean: v.parseBoolean,
  parseJson: v.parseJson,
  partialCheck: v.partialCheck,
  rawCheck: v.rawCheck,
  rawTransform: v.rawTransform,
  regex: v.regex,
  rfcEmail: v.rfcEmail,
  safeInteger: v.safeInteger,
  size: v.size,
  slug: v.slug,
  someItem: v.someItem,
  startsWith: v.startsWith,
  stringifyJson: v.stringifyJson,
  toBigint: v.toBigint,
  toDate: v.toDate,
  toNumber: v.toNumber,
  toString: v.toString,
  ulid: v.ulid,
  url: v.url,
  uuid: v.uuid,
  value: v.value,
  values: v.values,
  words: v.words,
} satisfies Record<Exclude<MessageKey, "schema">, (...args: never[]) => unknown>;

function registerMessages(i18next: i18n, lng: string): void {
  const t = i18next.getFixedT(lng, NAMESPACE);
  const message = (key: MessageKey) => (issue: v.BaseIssue<unknown>) =>
    t(key, { expected: issue.expected ?? "", received: issue.received });
  v.setSchemaMessage(message("schema"), lng);
  for (const key of Object.keys(actions) as Array<keyof typeof actions>) {
    v.setSpecificMessage(actions[key], message(key), lng);
  }
}

/**
 * Makes Valibot's issue messages come from the `valibot` catalogue in the
 * language of the given i18next instance. Register it before `init`, so that
 * the initial language is applied too. Until the catalogue loads, and in
 * `cimode`, Valibot uses its built-in English messages.
 */
export function localiseValibot(i18next: i18n): void {
  i18next.on("languageChanged", (lng: string) => {
    if (lng === "cimode") {
      v.setGlobalConfig({ lang: undefined });
      return;
    }
    i18next
      .loadNamespaces(NAMESPACE)
      .then(() => {
        if (i18next.language !== lng) return;
        registerMessages(i18next, lng);
        v.setGlobalConfig({ lang: lng });
      })
      .catch((error: unknown) => {
        console.warn(`Could not load the Valibot messages for ${lng}`, error);
      });
  });
}

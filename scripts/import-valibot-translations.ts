#!/usr/bin/env node
// Writes locales/<tag>/valibot.json from the @valibot/i18n translations, for
// the given language tags or, without arguments, for every non-primary locale
// in i18next.config.ts. A tag uses the whole @valibot/i18n language when one
// exists (zh-TW), otherwise its primary language subtag (de-DE uses de).
//
//   pnpm run i18n:valibot            # every configured locale
//   pnpm run i18n:valibot de-DE      # one locale, e.g. before adding it
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { parseArgs } from "node:util";
import { type MessageFormatElement, TYPE } from "@formatjs/icu-messageformat-parser";
import { printAST } from "@formatjs/icu-messageformat-parser/printer.js";
import { IntlMessageFormat } from "intl-messageformat";
import * as v from "valibot";

type Message = (issue: { expected: string | null; received: string }) => string;

const PRIMARY = "en-US";
const ui = path.resolve(import.meta.dirname, "../src/main/webapp/ui");
const localesDir = path.join(ui, "src/modules/common/i18n/locales");

// Private-use characters stand in for the issue values, so that the formatted
// message can be split into its literal text and its two arguments.
const ARGUMENTS = { "\u{E000}": "expected", "\u{E001}": "received" } as const;
const SAMPLE = { expected: "EXPECTED", received: "RECEIVED" };

function valibotLanguage(tag: string): string {
  for (const candidate of new Set([tag, new Intl.Locale(tag).language])) {
    try {
      import.meta.resolve(`@valibot/i18n/${candidate}`);
      return candidate;
    } catch {
      // Not a published language; try the next candidate.
    }
  }
  throw new Error(`@valibot/i18n has no translation for ${tag}`);
}

/**
 * Loads one @valibot/i18n message module and returns the message it
 * registered with Valibot: the schema message for `schema`, otherwise the
 * message of the action with that name.
 */
async function registeredMessage(language: string, key: string): Promise<Message> {
  await import(`@valibot/i18n/${language}/${key}`);
  const action: unknown = v[key as keyof typeof v];
  const message =
    key === "schema"
      ? v.getSchemaMessage(language)
      : typeof action === "function"
        ? v.getSpecificMessage(action as Parameters<typeof v.getSpecificMessage>[0], language)
        : undefined;
  if (typeof message !== "function") throw new Error(`@valibot/i18n/${language}/${key} registered no message`);
  return message as Message;
}

/** Converts a message to ICU, and checks that both format the same text. */
function toIcu(message: Message, where: string): string {
  const [expected, received] = Object.keys(ARGUMENTS);
  const ast: MessageFormatElement[] = message({ expected, received })
    .split(/([\u{E000}\u{E001}])/u)
    .filter((part) => part !== "")
    .map((part) =>
      part in ARGUMENTS
        ? { type: TYPE.argument, value: ARGUMENTS[part as keyof typeof ARGUMENTS] }
        : { type: TYPE.literal, value: part },
    );
  const icu = printAST(ast);
  const formatted = new IntlMessageFormat(icu, "en").format(SAMPLE);
  const original = message(SAMPLE);
  if (formatted !== original) throw new Error(`${where}: ICU text formats as "${formatted}", not "${original}"`);
  return icu;
}

let { positionals: tags } = parseArgs({ allowPositionals: true });
if (tags.length === 0) {
  const { default: config } = await import(pathToFileURL(path.join(ui, "i18next.config.ts")).href);
  tags = config.locales.filter((tag: string) => tag !== PRIMARY);
}
if (tags.length === 0) {
  console.log(`No locales besides ${PRIMARY} are configured; nothing to import.`);
} else {
  const keys = Object.keys(JSON.parse(await readFile(path.join(localesDir, PRIMARY, "valibot.json"), "utf8")));
  for (const tag of tags) {
    const language = valibotLanguage(tag);
    const messages: Record<string, string> = {};
    for (const key of keys) {
      messages[key] = toIcu(await registeredMessage(language, key), `@valibot/i18n/${language}/${key}`);
    }
    const out = path.join(localesDir, tag, "valibot.json");
    await mkdir(path.dirname(out), { recursive: true });
    await writeFile(out, `${JSON.stringify(messages, null, 2)}\n`);
    console.log(`Wrote ${path.relative(process.cwd(), out)} from @valibot/i18n/${language}`);
  }
}

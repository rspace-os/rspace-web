import i18next from "i18next";
import ICU from "i18next-icu";
import * as v from "valibot";
import { afterEach, describe, expect, test, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import enUS from "../locales/en-US/valibot.json";
import { localiseValibot } from "../valibot";

const schemas = [
  v.string(),
  v.pipe(v.string(), v.minLength(3)),
  v.pipe(v.string(), v.email()),
  v.pipe(
    v.number(),
    v.check((n) => n > 0),
  ),
];
const inputs = [1, "ab", "not an email", -1];
const messages = () => schemas.map((schema, i) => v.safeParse(schema, inputs[i]).issues?.[0].message);

async function instanceIn(lng: string) {
  const instance = i18next.createInstance().use(ICU);
  localiseValibot(instance);
  await instance.init({
    lng,
    fallbackLng: "en-US",
    resources: {
      "en-US": { valibot: enUS },
      "de-DE": { valibot: { ...enUS, schema: "Ungültiger Typ: {expected} erwartet aber {received} erhalten" } },
    },
  });
  return instance;
}

describe("localiseValibot", () => {
  afterEach(() => {
    v.deleteGlobalConfig();
  });

  test("the en-US catalogue reproduces Valibot's built-in messages", async () => {
    const builtIn = messages();
    await instanceIn("en-US");
    await vi.waitFor(() => expect(v.getGlobalConfig().lang).toBe("en-US"));
    expect(messages()).toEqual(builtIn);
  });

  test("follows the i18next language, and cimode restores the built-in messages", async () => {
    const instance = await instanceIn("de-DE");
    await vi.waitFor(() => expect(messages()[0]).toBe("Ungültiger Typ: string erwartet aber 1 erhalten"));

    await instance.changeLanguage("cimode");
    expect(messages()[0]).toBe("Invalid type: Expected string but received 1");
  });

  test("keeps the built-in messages when the catalogue fails to load", async () => {
    const builtIn = messages();
    const restoreConsole = silenceConsole(["warn"], ["Could not load the Valibot messages"]);
    const { warn } = console;
    try {
      const instance = i18next.createInstance().use(ICU);
      instance.use({
        type: "backend",
        init() {},
        read(_language: string, _namespace: string, callback: (error: Error, data: false) => void) {
          callback(new Error("chunk failed to load"), false);
        },
      });
      localiseValibot(instance);
      await instance.init({ lng: "en-US", fallbackLng: "en-US", ns: [] });

      await vi.waitFor(() =>
        expect(vi.mocked(warn)).toHaveBeenCalledWith("Could not load the Valibot messages for en-US"),
      );
      expect(v.getGlobalConfig().lang).toBeUndefined();
      expect(messages()).toEqual(builtIn);
    } finally {
      restoreConsole();
    }
  });
});

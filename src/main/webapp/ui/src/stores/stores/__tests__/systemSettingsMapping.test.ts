import { test, describe, expect } from "vitest";
import {
  systemSettingsFromApiResponse,
  dataciteSettingsToIgsnPayload,
  type ApiInventorySystemSettings,
} from "../systemSettingsMapping";

describe("systemSettingsFromApiResponse", () => {
  test("maps the IGSN entry of the new response to the datacite settings shape", () => {
    const response: ApiInventorySystemSettings = {
      identifiersSettings: {
        IGSN: {
          provider: "IGSN_DATACITE",
          serverUrl: "https://api.test.datacite.org",
          username: "AJFO.JIJEMD",
          password: "secret",
          repositoryPrefix: "10.82316",
          enabled: "true",
        },
        PDINST: {
          provider: "PDINST_DATACITE",
          serverUrl: "https://api.test.datacite.org",
          username: "ignored",
          password: "ignored",
          repositoryPrefix: "99.99999",
          enabled: "true",
        },
      },
    };

    const settings = systemSettingsFromApiResponse(response);

    // only the IGSN part is used, and the provider field is dropped from the dialog model
    expect(settings).toEqual({
      datacite: {
        serverUrl: "https://api.test.datacite.org",
        username: "AJFO.JIJEMD",
        password: "secret",
        repositoryPrefix: "10.82316",
        enabled: "true",
      },
    });
  });

  test("falls back to safe defaults when the IGSN entry is missing", () => {
    const settings = systemSettingsFromApiResponse({ identifiersSettings: {} });

    expect(settings.datacite).toEqual({
      serverUrl: "https://api.datacite.org",
      username: "",
      password: "",
      repositoryPrefix: "",
      enabled: "false",
    });
  });
});

describe("dataciteSettingsToIgsnPayload", () => {
  test("wraps the datacite settings into a single IGSN_DATACITE identifier settings object", () => {
    const payload = dataciteSettingsToIgsnPayload({
      serverUrl: "https://api.test.datacite.org",
      username: "AJFO.JIJEMD",
      password: "secret",
      repositoryPrefix: "10.82316",
      enabled: "true",
    });

    expect(payload).toEqual({
      provider: "IGSN_DATACITE",
      serverUrl: "https://api.test.datacite.org",
      username: "AJFO.JIJEMD",
      password: "secret",
      repositoryPrefix: "10.82316",
      enabled: "true",
    });
  });
});

import { describe, expect, test } from "vitest";
import {
  type ApiInventorySystemSettings,
  dataciteSettingsToIgsnPayload,
  pidinstB2InstSettingsToPayload,
  pidinstDataciteSettingsToPayload,
  systemSettingsFromApiResponse,
} from "../systemSettingsMapping";

describe("systemSettingsFromApiResponse", () => {
  test("maps the IGSN and PIDINST entries from the response to the system settings model", () => {
    const response: ApiInventorySystemSettings = {
      identifiersSettings: {
        IGSN: [
          {
            provider: "IGSN_DATACITE",
            serverUrl: "https://api.test.datacite.org",
            username: "AJFO.JIJEMD",
            password: "secret",
            repositoryPrefix: "10.82316",
            enabled: "true",
          },
        ],
        PIDINST: [
          {
            provider: "PIDINST_DATACITE",
            serverUrl: "https://api.test.datacite.org",
            username: "pidinst-user",
            password: "pidinst-secret",
            repositoryPrefix: "99.99999",
            enabled: "true",
          },
          {
            provider: "PIDINST_B2INST",
            serverUrl: "https://b2inst-test.gwdg.de",
            username: "my-community",
            password: "my-token",
            repositoryPrefix: "",
            enabled: "false",
          },
        ],
      },
    };

    const settings = systemSettingsFromApiResponse(response);

    expect(settings.igsnDatacite).toEqual({
      serverUrl: "https://api.test.datacite.org",
      username: "AJFO.JIJEMD",
      password: "secret",
      repositoryPrefix: "10.82316",
      enabled: "true",
    });

    expect(settings.pidinstDatacite).toEqual({
      serverUrl: "https://api.test.datacite.org",
      username: "pidinst-user",
      password: "pidinst-secret",
      repositoryPrefix: "99.99999",
      enabled: "true",
    });

    expect(settings.pidinstB2Inst).toEqual({
      serverUrl: "https://b2inst-test.gwdg.de",
      username: "my-community",
      password: "my-token",
      repositoryPrefix: "",
      enabled: "false",
    });
  });

  test("falls back to safe defaults when entries are missing", () => {
    const settings = systemSettingsFromApiResponse({ identifiersSettings: {} });

    expect(settings.igsnDatacite).toEqual({
      serverUrl: "https://api.datacite.org",
      username: "",
      password: "",
      repositoryPrefix: "",
      enabled: "false",
    });

    expect(settings.pidinstDatacite).toEqual({
      serverUrl: "https://api.datacite.org",
      username: "",
      password: "",
      repositoryPrefix: "",
      enabled: "false",
    });

    expect(settings.pidinstB2Inst).toEqual({
      serverUrl: "",
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

describe("pidinstDataciteSettingsToPayload", () => {
  test("wraps settings into a PIDINST_DATACITE identifier settings object", () => {
    const payload = pidinstDataciteSettingsToPayload({
      serverUrl: "https://api.datacite.org",
      username: "repo-account",
      password: "repo-pass",
      repositoryPrefix: "10.12345",
      enabled: "true",
    });

    expect(payload).toEqual({
      provider: "PIDINST_DATACITE",
      serverUrl: "https://api.datacite.org",
      username: "repo-account",
      password: "repo-pass",
      repositoryPrefix: "10.12345",
      enabled: "true",
    });
  });
});

describe("pidinstB2InstSettingsToPayload", () => {
  test("wraps B2INST settings into a PIDINST_B2INST identifier settings object with empty repositoryPrefix", () => {
    const payload = pidinstB2InstSettingsToPayload({
      serverUrl: "https://b2inst.example.com",
      username: "community-id",
      password: "api-token",
      repositoryPrefix: "should-be-ignored",
      enabled: "true",
    });

    expect(payload).toEqual({
      provider: "PIDINST_B2INST",
      serverUrl: "https://b2inst.example.com",
      username: "community-id",
      password: "api-token",
      repositoryPrefix: "",
      enabled: "true",
    });
  });
});

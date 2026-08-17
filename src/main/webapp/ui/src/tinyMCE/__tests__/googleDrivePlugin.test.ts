import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";
import { describe, expect, it, vi } from "vitest";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCRIPTS_ROOT = resolve(__dirname, "../../../../scripts");
const CLIENT_ID = "123456789-client.apps.googleusercontent.com";
const APP_ID = "987654321";
const DEVELOPER_KEY = "developer-key";
const PICKER_URL = "https://docs.google.com/picker?picker-uri";
const DEPLOYMENT_PROPERTIES = {
  "googledrive.app.id": APP_ID,
  "googledrive.client.id": CLIENT_ID,
  "googledrive.developer.key": DEVELOPER_KEY,
};

function evaluateScript(relativePath: string, sandbox: Record<string, unknown>) {
  runInNewContext(readFileSync(resolve(SCRIPTS_ROOT, relativePath), "utf8"), sandbox);
}

type TinyMceSetup = {
  external_plugins: Record<string, string>;
  [key: string]: unknown;
};

type ConfigureGoogleDrive = (setup: TinyMceSetup, properties: Record<string, string>, enabled: boolean) => boolean;

function loadGoogleDriveConfigurator() {
  const scriptRequest = { fail: vi.fn() };
  scriptRequest.fail.mockReturnValue(scriptRequest);
  const getScript = vi.fn(() => scriptRequest);
  const sandbox: Record<string, unknown> = {
    $: {
      getScript,
      when: vi.fn(() => scriptRequest),
    },
    console: { error: vi.fn(), log: vi.fn(), warn: vi.fn() },
    document: { querySelector: () => null },
    RS: { msg: (key: string) => key },
    tinymce: { PluginManager: { add: () => {} } },
    window: {},
  };

  evaluateScript("pages/workspace/editor/tinymce5_configuration.js", sandbox);
  return {
    configure: sandbox.configureGoogleDrive as ConfigureGoogleDrive,
    getScript,
    loadScripts: sandbox.loadGoogleDriveScripts as () => unknown,
  };
}

type OAuthConfiguration = {
  callback: (response: { access_token?: string; error?: string }) => void;
  client_id: string;
  error_callback: (response: { type?: string }) => void;
  scope: string;
};

function loadGoogleDriveInserter() {
  let oauthConfiguration: OAuthConfiguration | undefined;
  let finishPickerLoad: (() => void) | undefined;
  let pickerCallback: ((response: { action: string; error?: number }) => void) | undefined;
  const apprise = vi.fn();
  const initTokenClient = vi.fn((configuration: OAuthConfiguration) => {
    oauthConfiguration = configuration;
    return { requestAccessToken };
  });
  const requestAccessToken = vi.fn();
  const picker = { setVisible: vi.fn() };
  const pickerBuilder = {
    addView: vi.fn(() => pickerBuilder),
    build: vi.fn(() => picker),
    setAppId: vi.fn(() => pickerBuilder),
    setCallback: vi.fn((callback: (response: { action: string; error?: number }) => void) => {
      pickerCallback = callback;
      return pickerBuilder;
    }),
    setDeveloperKey: vi.fn(() => pickerBuilder),
    setOAuthToken: vi.fn(() => pickerBuilder),
    setOrigin: vi.fn(() => pickerBuilder),
    toUri: vi.fn(() => PICKER_URL),
  };
  function PickerBuilder() {
    return pickerBuilder;
  }
  const sandbox: Record<string, unknown> = {
    RS: { msg: (key: string) => key },
    apprise,
    gapi: {
      load(_libraries: string, callback: () => void) {
        finishPickerLoad = callback;
      },
    },
    google: {
      accounts: {
        oauth2: {
          initTokenClient,
        },
      },
      picker: {
        Action: { ERROR: "error", PICKED: "picked" },
        Document: {},
        PickerBuilder,
        Response: {},
        ViewId: { DOCS: "docs" },
      },
    },
    tinymce: { PluginManager: { add: () => {} } },
    window: {
      location: { host: "rspace.example.com", protocol: "https:" },
    },
  };

  evaluateScript("externalTinymcePlugins/googledrive/plugin.min.js", sandbox);

  return {
    apprise,
    finishPickerLoad: () => finishPickerLoad?.(),
    getOauthConfiguration: () => oauthConfiguration,
    getPickerCallback: () => pickerCallback,
    initTokenClient,
    insert: sandbox.insertFromGoogleDrive as (editor: { settings: Record<string, string> }) => Promise<void>,
    pickerBuilder,
    requestAccessToken,
    picker,
  };
}

function createEditor() {
  return {
    settings: {
      googledrive_app_id: APP_ID,
      googledrive_client_id: CLIENT_ID,
      googledrive_developer_key: DEVELOPER_KEY,
      googledrive_scope: "drive-scope",
    },
    windowManager: { openUrl: vi.fn() },
  };
}

async function startInsertion(harness: ReturnType<typeof loadGoogleDriveInserter>, editor = createEditor()) {
  const insertion = harness.insert(editor);
  harness.finishPickerLoad();
  await insertion;
  const oauthConfiguration = harness.getOauthConfiguration();
  if (!oauthConfiguration) throw new Error("Google OAuth was not configured");
  return oauthConfiguration;
}

describe("Google Drive TinyMCE integration", () => {
  it("passes deployment properties to the plugin through TinyMCE settings", () => {
    const { configure } = loadGoogleDriveConfigurator();
    const setup = { external_plugins: {} };

    expect(configure(setup, DEPLOYMENT_PROPERTIES, true)).toBe(true);
    expect(setup).toMatchObject({
      external_plugins: {
        googledrive: "/scripts/externalTinymcePlugins/googledrive/plugin.min.js",
      },
      googledrive_app_id: APP_ID,
      googledrive_client_id: CLIENT_ID,
      googledrive_developer_key: DEVELOPER_KEY,
      googledrive_scope: "https://www.googleapis.com/auth/drive.file",
    });
  });

  it("does not load the plugin when required deployment properties are missing", () => {
    const { configure } = loadGoogleDriveConfigurator();
    const setup = {
      external_plugins: {
        googledrive: "/scripts/externalTinymcePlugins/googledrive/plugin.min.js",
      },
      googledrive_client_id: "old-client-id",
      googledrive_app_id: "old-app-id",
      googledrive_developer_key: "old-developer-key",
      googledrive_scope: "old-scope",
    };

    expect(configure(setup, { ...DEPLOYMENT_PROPERTIES, "googledrive.app.id": "" }, true)).toBe(false);
    expect(setup).toEqual({ external_plugins: {} });
  });

  it("does not load the plugin when the integration is disabled", () => {
    const { configure } = loadGoogleDriveConfigurator();
    const setup = { external_plugins: {} };

    expect(configure(setup, DEPLOYMENT_PROPERTIES, false)).toBe(false);
    expect(setup.external_plugins).not.toHaveProperty("googledrive");
  });

  it("loads the Google client scripts once", () => {
    const { getScript, loadScripts } = loadGoogleDriveConfigurator();

    const firstRequest = loadScripts();
    const secondRequest = loadScripts();

    expect(secondRequest).toBe(firstRequest);
    expect(getScript.mock.calls).toEqual([
      ["https://accounts.google.com/gsi/client"],
      ["https://apis.google.com/js/api.js"],
    ]);
  });

  it("uses TinyMCE settings for OAuth and Google Picker", async () => {
    const {
      finishPickerLoad,
      getOauthConfiguration,
      initTokenClient,
      insert,
      requestAccessToken,
      pickerBuilder,
      picker,
    } = loadGoogleDriveInserter();
    const editor = createEditor();

    const insertion = insert(editor);
    await Promise.resolve();
    expect(initTokenClient).not.toHaveBeenCalled();

    finishPickerLoad();
    await insertion;
    const oauthConfiguration = getOauthConfiguration();
    expect(oauthConfiguration).toMatchObject({
      client_id: CLIENT_ID,
      scope: "drive-scope",
    });
    expect(requestAccessToken).toHaveBeenCalledWith({ prompt: "consent" });

    oauthConfiguration?.callback({ access_token: "access-token" });
    expect(pickerBuilder.setDeveloperKey).toHaveBeenCalledWith(DEVELOPER_KEY);
    expect(pickerBuilder.setAppId).toHaveBeenCalledWith(APP_ID);
    expect(pickerBuilder.setOrigin).toHaveBeenCalledWith("https://rspace.example.com");
    expect(picker.setVisible).toHaveBeenCalledWith(true);
  });

  it("shows the Picker response in a TinyMCE iframe when Picker reports an error", async () => {
    const harness = loadGoogleDriveInserter();
    const editor = createEditor();
    const oauthConfiguration = await startInsertion(harness, editor);
    oauthConfiguration.callback({ access_token: "access-token" });

    harness.getPickerCallback()?.({ action: "error" });

    expect(harness.picker.setVisible).toHaveBeenLastCalledWith(false);
    expect(editor.windowManager.openUrl).toHaveBeenCalledWith({
      title: "legacyjs.tinymce.googleDrive.pickerTitle",
      url: PICKER_URL,
      width: 1051,
      height: 650,
    });
  });

  it.each([
    {
      expectedMessage: "legacyjs.tinymce.googleDrive.popupBlocked",
      response: { type: "popup_failed_to_open" },
      responseHandler: "error_callback" as const,
    },
    {
      expectedMessage: "legacyjs.tinymce.googleDrive.authorizationIncomplete",
      response: { error: "access_denied" },
      responseHandler: "callback" as const,
    },
  ])(
    "explains how to recover from $responseHandler failures",
    async ({ expectedMessage, response, responseHandler }) => {
      const harness = loadGoogleDriveInserter();
      const oauthConfiguration = await startInsertion(harness);
      oauthConfiguration[responseHandler](response);

      expect(harness.pickerBuilder.setDeveloperKey).not.toHaveBeenCalled();
      expect(harness.apprise).toHaveBeenCalledWith(expectedMessage);
    },
  );
});

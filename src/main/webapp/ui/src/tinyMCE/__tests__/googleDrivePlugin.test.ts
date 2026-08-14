import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";
import { describe, expect, it, vi } from "vitest";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCRIPTS_ROOT = resolve(__dirname, "../../../../scripts");

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
    RS: { msg: (key: string) => key },
    tinymce: { PluginManager: { add: () => {} } },
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
  const setDeveloperKey = vi.fn();
  const setAppId = vi.fn();
  const setOrigin = vi.fn();
  const picker = { setVisible: vi.fn() };
  class PickerBuilder {
    addView() {
      return this;
    }

    build() {
      return picker;
    }

    setAppId(appId: string) {
      setAppId(appId);
      return this;
    }

    setCallback(callback: (response: { action: string; error?: number }) => void) {
      pickerCallback = callback;
      return this;
    }

    setDeveloperKey(developerKey: string) {
      setDeveloperKey(developerKey);
      return this;
    }

    setOAuthToken() {
      return this;
    }

    setOrigin(origin: string) {
      setOrigin(origin);
      return this;
    }

    toUri() {
      return "https://docs.google.com/picker?picker-uri";
    }
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
    requestAccessToken,
    setAppId,
    setDeveloperKey,
    setOrigin,
    picker,
  };
}

const editor = {
  settings: {
    googledrive_app_id: "987654321",
    googledrive_client_id: "123456789-client.apps.googleusercontent.com",
    googledrive_developer_key: "developer-key",
    googledrive_scope: "drive-scope",
  },
  windowManager: { openUrl: vi.fn() },
};

describe("Google Drive TinyMCE integration", () => {
  it("passes deployment properties to the plugin through TinyMCE settings", () => {
    const { configure } = loadGoogleDriveConfigurator();
    const setup = { external_plugins: {} };

    expect(
      configure(
        setup,
        {
          "googledrive.app.id": "987654321",
          "googledrive.client.id": "123456789-client.apps.googleusercontent.com",
          "googledrive.developer.key": "developer-key",
        },
        true,
      ),
    ).toBe(true);
    expect(setup).toMatchObject({
      external_plugins: {
        googledrive: "/scripts/externalTinymcePlugins/googledrive/plugin.min.js",
      },
      googledrive_app_id: "987654321",
      googledrive_client_id: "123456789-client.apps.googleusercontent.com",
      googledrive_developer_key: "developer-key",
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

    expect(
      configure(
        setup,
        {
          "googledrive.app.id": "",
          "googledrive.client.id": "123456789-client.apps.googleusercontent.com",
          "googledrive.developer.key": "developer-key",
        },
        true,
      ),
    ).toBe(false);
    expect(setup.external_plugins).not.toHaveProperty("googledrive");
    expect(setup).not.toHaveProperty("googledrive_client_id");
    expect(setup).not.toHaveProperty("googledrive_app_id");
    expect(setup).not.toHaveProperty("googledrive_developer_key");
    expect(setup).not.toHaveProperty("googledrive_scope");
  });

  it("does not load the plugin when the integration is disabled", () => {
    const { configure } = loadGoogleDriveConfigurator();
    const setup = { external_plugins: {} };

    expect(
      configure(
        setup,
        {
          "googledrive.app.id": "987654321",
          "googledrive.client.id": "123456789-client.apps.googleusercontent.com",
          "googledrive.developer.key": "developer-key",
        },
        false,
      ),
    ).toBe(false);
    expect(setup.external_plugins).not.toHaveProperty("googledrive");
  });

  it("loads the Google client scripts once", () => {
    const { getScript, loadScripts } = loadGoogleDriveConfigurator();

    const firstRequest = loadScripts();
    const secondRequest = loadScripts();

    expect(secondRequest).toBe(firstRequest);
    expect(getScript).toHaveBeenCalledTimes(2);
    expect(getScript).toHaveBeenNthCalledWith(1, "https://accounts.google.com/gsi/client");
    expect(getScript).toHaveBeenNthCalledWith(2, "https://apis.google.com/js/api.js");
  });

  it("uses TinyMCE settings for OAuth and Google Picker", async () => {
    const {
      finishPickerLoad,
      getOauthConfiguration,
      initTokenClient,
      insert,
      requestAccessToken,
      setAppId,
      setDeveloperKey,
      setOrigin,
      picker,
    } = loadGoogleDriveInserter();

    const insertion = insert(editor);
    await Promise.resolve();
    expect(initTokenClient).not.toHaveBeenCalled();

    finishPickerLoad();
    await insertion;
    const oauthConfiguration = getOauthConfiguration();
    expect(oauthConfiguration).toMatchObject({
      client_id: "123456789-client.apps.googleusercontent.com",
      scope: "drive-scope",
    });
    expect(requestAccessToken).toHaveBeenCalledWith({ prompt: "consent" });

    oauthConfiguration?.callback({ access_token: "access-token" });
    expect(setDeveloperKey).toHaveBeenCalledWith("developer-key");
    expect(setAppId).toHaveBeenCalledWith("987654321");
    expect(setOrigin).toHaveBeenCalledWith("https://rspace.example.com");
    expect(picker.setVisible).toHaveBeenCalledWith(true);
  });

  it("shows the Picker response in a TinyMCE iframe when Picker reports an error", async () => {
    const { finishPickerLoad, getOauthConfiguration, getPickerCallback, insert, picker } = loadGoogleDriveInserter();
    const insertion = insert(editor);
    finishPickerLoad();
    await insertion;
    getOauthConfiguration()?.callback({ access_token: "access-token" });

    getPickerCallback()?.({ action: "error" });

    expect(picker.setVisible).toHaveBeenLastCalledWith(false);
    expect(editor.windowManager.openUrl).toHaveBeenCalledWith({
      title: "legacyjs.tinymce.googleDrive.pickerTitle",
      url: "https://docs.google.com/picker?picker-uri",
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
      const { apprise, finishPickerLoad, getOauthConfiguration, insert, setDeveloperKey } = loadGoogleDriveInserter();
      const insertion = insert(editor);
      finishPickerLoad();
      await insertion;

      const oauthConfiguration = getOauthConfiguration();
      oauthConfiguration?.[responseHandler](response);

      expect(setDeveloperKey).not.toHaveBeenCalled();
      expect(apprise).toHaveBeenCalledWith(expectedMessage);
    },
  );
});

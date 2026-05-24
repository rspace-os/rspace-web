import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { beforeEach, describe, expect, it, vi } from "vitest";

type TinyDialogConfig = {
  title: string;
  body: {
    type: string;
    items: Array<{ type: string; html?: string }>;
  };
  buttons: Array<{
    type: string;
    name?: string;
    text: string;
    primary?: boolean;
    enabled?: boolean;
  }>;
  onChange: (dialogApi: DialogApiStub) => void;
  onSubmit: (dialogApi: DialogApiStub) => void;
};

type DialogApiStub = {
  close: ReturnType<typeof vi.fn>;
  disable: ReturnType<typeof vi.fn>;
  enable: ReturnType<typeof vi.fn>;
  focus: ReturnType<typeof vi.fn>;
  getData: ReturnType<typeof vi.fn<() => { videoUrl: string }>>;
};

type ButtonConfig = {
  tooltip: string;
  icon: string;
  onAction: () => void;
};

type MenuItemConfig = {
  text: string;
  icon: string;
  onAction: () => void;
};

type EditorStub = {
  addCommand: ReturnType<typeof vi.fn<(name: string, command: () => void) => void>>;
  execCommand: ReturnType<
    typeof vi.fn<(commandName: string, ui?: boolean, value?: string) => void>
  >;
  focus: ReturnType<typeof vi.fn>;
  ui: {
    registry: {
      addButton: ReturnType<typeof vi.fn>;
      addMenuItem: ReturnType<typeof vi.fn>;
    };
  };
  windowManager: {
    open: ReturnType<typeof vi.fn>;
  };
};

function createDialogApi(initialUrl = "") {
  let currentUrl = initialUrl;
  const dialogApi: DialogApiStub = {
    close: vi.fn(),
    disable: vi.fn(),
    enable: vi.fn(),
    focus: vi.fn(),
    getData: vi.fn(() => ({ videoUrl: currentUrl })),
  };

  return {
    dialogApi,
    setUrl: (url: string) => {
      currentUrl = url;
    },
  };
}

function instantiatePlugin() {
  const registeredPlugins = new Map<string, (editor: EditorStub) => void>();

  (
    globalThis as typeof globalThis & {
      tinymce: {
        PluginManager: {
          add: (name: string, plugin: (editor: unknown) => void) => void;
        };
      };
    }
  ).tinymce = {
    PluginManager: {
      add: (name, plugin) => {
        registeredPlugins.set(name, plugin);
      },
    },
  };

  const pluginSource = readFileSync(
    resolve(process.cwd(), "../scripts/externalTinymcePlugins/video/plugin.js"),
    "utf8",
  );
  runInNewContext(pluginSource, globalThis);
  return registeredPlugins;
}

function createEditor(dialogApi: DialogApiStub) {
  const buttons = new Map<string, ButtonConfig>();
  const menuItems = new Map<string, MenuItemConfig>();
  const insertions: Array<{ commandName: string; value?: string }> = [];
  let openedDialog: TinyDialogConfig | null = null;

  const editor: EditorStub = {
    addCommand: vi.fn((name: string, command: () => void) => {
      if (name === "cmdVideoEmbed") {
        editor.execCommand.mockImplementation(
          (commandName: string, _ui?: boolean, value?: string) => {
            if (commandName === "cmdVideoEmbed") {
              command();
              return;
            }
            insertions.push({ commandName, value });
          },
        );
      }
    }),
    execCommand: vi.fn(),
    focus: vi.fn(),
    ui: {
      registry: {
        addButton: vi.fn((name: string, config: ButtonConfig) => {
          buttons.set(name, config);
        }),
        addMenuItem: vi.fn((name: string, config: MenuItemConfig) => {
          menuItems.set(name, config);
        }),
      },
    },
    windowManager: {
      open: vi.fn((config: TinyDialogConfig) => {
        openedDialog = config;
        return dialogApi;
      }),
    },
  };

  return {
    buttons,
    editor,
    getOpenedDialog: () => openedDialog,
    insertions,
    menuItems,
  };
}

function openDialogWithUrl(url: string) {
  const plugins = instantiatePlugin();
  const { dialogApi, setUrl } = createDialogApi();
  const editorContext = createEditor(dialogApi);

  plugins.get("videoembed")?.(editorContext.editor);
  editorContext.editor.execCommand("cmdVideoEmbed");

  const dialogConfig = editorContext.getOpenedDialog();
  expect(dialogConfig).not.toBeNull();
  document.body.innerHTML = dialogConfig?.body.items[2]?.html ?? "";
  setUrl(url);
  dialogConfig?.onChange(dialogApi);

  return {
    dialogApi,
    dialogConfig: dialogConfig as TinyDialogConfig,
    ...editorContext,
  };
}

describe("TinyMCE video embed plugin", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    document.body.innerHTML = "";
    window.insertActions = new Map();
    (globalThis as typeof globalThis & { RS: { trackEvent: ReturnType<typeof vi.fn> } }).RS = {
      trackEvent: vi.fn(),
    };
  });

  it("registers toolbar/menu/slash actions and lists supported providers", () => {
    const plugins = instantiatePlugin();
    const { dialogApi } = createDialogApi();
    const { buttons, editor, getOpenedDialog, menuItems } = createEditor(dialogApi);

    const plugin = plugins.get("videoembed");
    expect(plugin).toBeDefined();
    plugin?.(editor);

    expect(buttons.get("videoembed")).toEqual(
      expect.objectContaining({
        tooltip: "Embed video",
        icon: "embed",
      }),
    );
    expect(menuItems.get("optVideoEmbed")).toEqual(
      expect.objectContaining({
        text: "Video",
        icon: "embed",
      }),
    );
    expect(window.insertActions?.get("optVideoEmbed")).toEqual(
      expect.objectContaining({
        text: "Video",
        icon: "embed",
      }),
    );

    buttons.get("videoembed")?.onAction();

    expect(editor.windowManager.open).toHaveBeenCalledTimes(1);
    const dialogConfig = getOpenedDialog();
    expect(dialogConfig).toEqual(
      expect.objectContaining({
        title: "Embed video",
      }),
    );
    expect(dialogConfig?.body.items[1]?.html).toContain(
      "Supported providers: YouTube, YouTube Privacy-Enhanced Mode, JoVE, TIB AV-Portal.",
    );
    expect(dialogConfig?.buttons).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          type: "submit",
          text: "Insert",
          enabled: false,
        }),
      ]),
    );
    expect(
      (globalThis as typeof globalThis & { RS: { trackEvent: ReturnType<typeof vi.fn> } }).RS
        .trackEvent,
    ).toHaveBeenCalledWith("VideoEmbedUsed");
  });

  it.each([
    {
      url: "https://www.youtube.com/watch?v=bhRExXIGxek",
      feedback: "YouTube video detected.",
      expected:
        '<div class="embedIframeDiv mceNonEditable"><iframe src="https://www.youtube.com/embed/bhRExXIGxek" width="560" height="315" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen="" referrerpolicy="strict-origin-when-cross-origin"></iframe></div><p>&nbsp;</p>',
    },
    {
      url: "https://youtu.be/bhRExXIGxek",
      feedback: "YouTube video detected.",
      expected:
        '<div class="embedIframeDiv mceNonEditable"><iframe src="https://www.youtube.com/embed/bhRExXIGxek" width="560" height="315" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen="" referrerpolicy="strict-origin-when-cross-origin"></iframe></div><p>&nbsp;</p>',
    },
    {
      url: "https://www.youtube.com/embed/bhRExXIGxek",
      feedback: "YouTube video detected.",
      expected:
        '<div class="embedIframeDiv mceNonEditable"><iframe src="https://www.youtube.com/embed/bhRExXIGxek" width="560" height="315" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen="" referrerpolicy="strict-origin-when-cross-origin"></iframe></div><p>&nbsp;</p>',
    },
    {
      url: "https://www.youtube.com/shorts/bhRExXIGxek",
      feedback: "YouTube video detected.",
      expected:
        '<div class="embedIframeDiv mceNonEditable"><iframe src="https://www.youtube.com/embed/bhRExXIGxek" width="560" height="315" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen="" referrerpolicy="strict-origin-when-cross-origin"></iframe></div><p>&nbsp;</p>',
    },
    {
      url: "https://www.youtube-nocookie.com/embed/bhRExXIGxek",
      feedback: "YouTube Privacy-Enhanced Mode video detected.",
      expected:
        '<div class="embedIframeDiv mceNonEditable"><iframe src="https://www.youtube-nocookie.com/embed/bhRExXIGxek" width="560" height="315" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen="" referrerpolicy="strict-origin-when-cross-origin"></iframe></div><p>&nbsp;</p>',
    },
    {
      url: "https://app.jove.com/v/60908/using-an-automated-hirschberg-test-app",
      feedback: "JoVE video detected.",
      expected:
        '<div class="embedIframeDiv mceNonEditable"><iframe src="https://www.jove.com/embed/player?id=60908&t=1&s=1&fpv=1" width="460" height="440" border="0" frameborder="0" marginwidth="0" marginwheight="0" allow="encrypted-media *" allowfullscreen="" allowtransparency="true" scrolling="no"></iframe></div><p>&nbsp;</p>',
    },
    {
      url: "https://www.jove.com/embed/player?id=60908&t=1&s=1&fpv=1",
      feedback: "JoVE video detected.",
      expected:
        '<div class="embedIframeDiv mceNonEditable"><iframe src="https://www.jove.com/embed/player?id=60908&t=1&s=1&fpv=1" width="460" height="440" border="0" frameborder="0" marginwidth="0" marginwheight="0" allow="encrypted-media *" allowfullscreen="" allowtransparency="true" scrolling="no"></iframe></div><p>&nbsp;</p>',
    },
    {
      url: "https://av.tib.eu/player/70488",
      feedback: "TIB AV-Portal video detected.",
      expected:
        '<div class="embedIframeDiv mceNonEditable"><iframe src="https://av.tib.eu/player/70488" width="560" allow="fullscreen"></iframe></div><p>&nbsp;</p>',
    },
    {
      url: "https://av.tib.eu/media/56500",
      feedback: "TIB AV-Portal video detected.",
      expected:
        '<div class="embedIframeDiv mceNonEditable"><iframe src="https://av.tib.eu/player/56500" width="560" allow="fullscreen"></iframe></div><p>&nbsp;</p>',
    },
  ])("inserts canonical embed HTML for $url", ({ expected, feedback, url }) => {
    const { dialogApi, dialogConfig, editor, insertions } = openDialogWithUrl(url);

    expect(dialogApi.enable).toHaveBeenLastCalledWith("submit");
    expect(document.getElementById("rspace-video-url-feedback")).toHaveTextContent(feedback);

    dialogConfig.onSubmit(dialogApi);

    expect(editor.focus).toHaveBeenCalled();
    expect(insertions).toEqual([
      {
        commandName: "mceInsertContent",
        value: expected,
      },
    ]);
    expect(dialogApi.close).toHaveBeenCalled();
  });

  it("keeps insert disabled for unsupported video URLs and shows validation feedback", () => {
    const { dialogApi } = openDialogWithUrl("https://vimeo.com/1234");

    expect(dialogApi.disable).toHaveBeenLastCalledWith("submit");
    expect(document.getElementById("rspace-video-url-feedback")).toHaveTextContent(
      "Enter a URL from a supported video provider.",
    );
  });
});

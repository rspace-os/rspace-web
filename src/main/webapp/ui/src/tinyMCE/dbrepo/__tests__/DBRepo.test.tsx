import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { afterEach, describe, expect, it, vi } from "vitest";
import axios from "@/common/axios";
import DBRepo, { buildDBRepoLinkTemplateData } from "../DBRepo";

const DBREPO_LOGO_PATH = "/images/icons/dbrepo.svg";

const mockAxios = new MockAdapter(axios);

afterEach(() => {
  mockAxios.reset();
  vi.restoreAllMocks();
  delete (window as unknown as { tinymce?: unknown }).tinymce;
  delete (window as unknown as { RS?: unknown }).RS;
});

describe("DBRepo dialog body", () => {
  it("lists databases and inserts a selected DBRepo link template", async () => {
    const editorListeners = new Map<string, () => void>();
    const insertTemplateIntoTinyMCE = vi.fn();
    const close = vi.fn();
    const activeEditor = {
      getBody: () => document.body,
      on: vi.fn((eventName: string, callback: () => void) => editorListeners.set(eventName, callback)),
      off: vi.fn((eventName: string) => editorListeners.delete(eventName)),
      windowManager: { close },
    };
    (window as unknown as { tinymce?: unknown }).tinymce = {
      activeEditor,
    };
    (window as unknown as { RS?: unknown }).RS = {
      insertTemplateIntoTinyMCE,
    };
    mockAxios.onGet("/apps/dbrepo/databases").reply(200, [
      {
        id: "db-1",
        name: "Research data",
        description: "Primary project database",
        url: "https://dbrepo.example/database/db-1",
      },
    ]);

    render(<DBRepo />);

    expect(await screen.findByText("Research data")).toBeVisible();
    await waitFor(() => expect(editorListeners.has("dbrepo-insert")).toBe(true));
    editorListeners.get("dbrepo-insert")?.();

    expect(insertTemplateIntoTinyMCE).toHaveBeenCalledWith(
      "dbrepoLink",
      expect.objectContaining({
        recordURL: "https://dbrepo.example/database/db-1",
        name: "Research data",
        dbrepoType: "database",
        databaseId: "db-1",
        resourceId: "",
        databaseName: "Research data",
        query: "",
        iconPath: DBREPO_LOGO_PATH,
      }),
      activeEditor,
    );
    expect(close).toHaveBeenCalled();
  });

  it("loads resources when a database is expanded and inserts a selected resource", async () => {
    const user = userEvent.setup();
    const editorListeners = new Map<string, () => void>();
    const insertTemplateIntoTinyMCE = vi.fn();
    const close = vi.fn();
    const activeEditor = {
      getBody: () => document.body,
      on: vi.fn((eventName: string, callback: () => void) => editorListeners.set(eventName, callback)),
      off: vi.fn((eventName: string) => editorListeners.delete(eventName)),
      windowManager: { close },
    };
    (window as unknown as { tinymce?: unknown }).tinymce = {
      activeEditor,
    };
    (window as unknown as { RS?: unknown }).RS = {
      insertTemplateIntoTinyMCE,
    };
    mockAxios.onGet("/apps/dbrepo/databases").reply(200, [
      {
        id: "db-1",
        name: "Research data",
        description: "Primary project database",
        url: "https://dbrepo.example/database/db-1",
      },
    ]);
    mockAxios.onGet("/apps/dbrepo/databases/db-1/resources").reply(200, {
      databaseId: "db-1",
      tables: [
        {
          id: "table-1",
          type: "table",
          label: "Experiments",
          secondaryText: "",
          url: "https://dbrepo.example/database/db-1/table/table-1",
        },
      ],
      views: [
        {
          id: "view-1",
          type: "view",
          label: "Recent experiments",
          secondaryText: "SELECT * FROM experiments",
          url: "https://dbrepo.example/database/db-1/view/view-1",
        },
      ],
      subsets: [
        {
          id: "subset-1",
          type: "subset",
          label: "SELECT * FROM experiments WHERE status = 'open'",
          secondaryText: "",
          url: "https://dbrepo.example/database/db-1/subset/subset-1",
        },
      ],
      failedTypes: [],
    });

    render(<DBRepo />);

    expect(await screen.findByText("Research data")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "workspace:tinymce.dbrepo.expandDatabase" }));

    expect(await screen.findByText("workspace:tinymce.dbrepo.categories.tables")).toBeVisible();
    expect(screen.getByText("workspace:tinymce.dbrepo.categories.views")).toBeVisible();
    expect(screen.getByText("workspace:tinymce.dbrepo.categories.subsets")).toBeVisible();
    expect(screen.getByText("Experiments")).toBeVisible();
    expect(screen.getByText("Recent experiments")).toBeVisible();
    expect(screen.getByText("SELECT * FROM experiments")).toBeVisible();
    expect(screen.getByText("SELECT * FROM experiments WHERE status = 'open'")).toBeVisible();

    await user.click(screen.getByRole("radio", { name: /Recent experiments/ }));
    editorListeners.get("dbrepo-insert")?.();

    expect(insertTemplateIntoTinyMCE).toHaveBeenCalledWith(
      "dbrepoLink",
      expect.objectContaining({
        recordURL: "https://dbrepo.example/database/db-1/view/view-1",
        name: "Recent experiments",
        dbrepoType: "view",
        databaseId: "db-1",
        resourceId: "view-1",
        databaseName: "Research data",
        query: "SELECT * FROM experiments",
      }),
      activeEditor,
    );
    expect(close).toHaveBeenCalled();
  });

  it("opens a row picker for a selected table and inserts selected rows", async () => {
    const user = userEvent.setup();
    const editorListeners = new Map<string, () => void>();
    const insertTemplateIntoTinyMCE = vi.fn();
    const execCommand = vi.fn();
    const close = vi.fn();
    const activeEditor = {
      getBody: () => document.body,
      execCommand,
      on: vi.fn((eventName: string, callback: () => void) => editorListeners.set(eventName, callback)),
      off: vi.fn((eventName: string) => editorListeners.delete(eventName)),
      windowManager: { close },
    };
    (window as unknown as { tinymce?: unknown }).tinymce = {
      activeEditor,
    };
    (window as unknown as { RS?: unknown }).RS = {
      insertTemplateIntoTinyMCE,
    };
    mockAxios.onGet("/apps/dbrepo/databases").reply(200, [
      {
        id: "db-1",
        name: "Research data",
        url: "https://dbrepo.example/database/db-1",
      },
    ]);
    mockAxios.onGet("/apps/dbrepo/databases/db-1/resources").reply(200, {
      databaseId: "db-1",
      tables: [
        {
          id: "table-1",
          type: "table",
          label: "Experiments",
          secondaryText: "",
          url: "https://dbrepo.example/database/db-1/table/table-1",
        },
      ],
      views: [],
      subsets: [],
      failedTypes: [],
    });
    mockAxios.onGet("/apps/dbrepo/databases/db-1/table/table-1/metadata").reply(200, {
      id: "table-1",
      type: "table",
      name: "Experiments",
      query: "",
      columns: [
        { id: "col-1", name: "Experiment ID", internalName: "experiment_id", type: "int" },
        { id: "col-2", name: "Title", internalName: "title", type: "varchar", size: 255 },
      ],
    });
    mockAxios.onGet("/apps/dbrepo/databases/db-1/table/table-1/rows").reply((config) => {
      expect(config.params).toEqual({ page: 0, size: 10 });
      return [
        200,
        {
          rows: [
            { "Experiment ID": 1, Title: "Alpha" },
            { "Experiment ID": 2, Title: "Beta" },
          ],
          page: 0,
          size: 10,
          totalCount: 2,
        },
      ];
    });

    render(<DBRepo />);

    expect(await screen.findByText("Research data")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "workspace:tinymce.dbrepo.expandDatabase" }));
    await user.click(await screen.findByRole("radio", { name: "Experiments" }));
    act(() => {
      editorListeners.get("dbrepo-insert-rows")?.();
    });

    expect(await screen.findByText("Experiment ID")).toBeVisible();
    expect(screen.getByText("varchar(255)")).toBeVisible();
    await user.click(screen.getByText("Alpha"));
    await user.click(screen.getByRole("button", { name: "workspace:tinymce.dbrepo.rows.insert" }));

    expect(execCommand).toHaveBeenCalledWith(
      "mceInsertContent",
      false,
      expect.stringContaining('data-tablesource="dbrepo"'),
    );
    expect(execCommand.mock.calls[0][2]).toContain("<th>Experiment ID</th>");
    expect(execCommand.mock.calls[0][2]).toContain("<td>Alpha</td>");
    expect(close).toHaveBeenCalled();
  });

  it("does not open the row picker for databases", async () => {
    const editorListeners = new Map<string, () => void>();
    const activeEditor = {
      getBody: () => document.body,
      execCommand: vi.fn(),
      on: vi.fn((eventName: string, callback: () => void) => editorListeners.set(eventName, callback)),
      off: vi.fn((eventName: string) => editorListeners.delete(eventName)),
      windowManager: { close: vi.fn() },
    };
    (window as unknown as { tinymce?: unknown }).tinymce = {
      activeEditor,
    };
    mockAxios.onGet("/apps/dbrepo/databases").reply(200, [
      {
        id: "db-1",
        name: "Research data",
        url: "https://dbrepo.example/database/db-1",
      },
    ]);

    render(<DBRepo />);

    expect(await screen.findByText("Research data")).toBeVisible();
    editorListeners.get("dbrepo-insert-rows")?.();

    expect(screen.queryByText("workspace:tinymce.dbrepo.rows.tableLabel")).not.toBeInTheDocument();
  });

  it("selects a database when it is expanded", async () => {
    const user = userEvent.setup();
    const editorListeners = new Map<string, () => void>();
    const insertTemplateIntoTinyMCE = vi.fn();
    const close = vi.fn();
    const activeEditor = {
      getBody: () => document.body,
      on: vi.fn((eventName: string, callback: () => void) => editorListeners.set(eventName, callback)),
      off: vi.fn((eventName: string) => editorListeners.delete(eventName)),
      windowManager: { close },
    };
    (window as unknown as { tinymce?: unknown }).tinymce = {
      activeEditor,
    };
    (window as unknown as { RS?: unknown }).RS = {
      insertTemplateIntoTinyMCE,
    };
    mockAxios.onGet("/apps/dbrepo/databases").reply(200, [
      {
        id: "db-1",
        name: "Research data",
        url: "https://dbrepo.example/database/db-1",
      },
      {
        id: "db-2",
        name: "Archive data",
        url: "https://dbrepo.example/database/db-2",
      },
    ]);
    mockAxios.onGet("/apps/dbrepo/databases/db-2/resources").reply(200, {
      databaseId: "db-2",
      tables: [],
      views: [],
      subsets: [],
      failedTypes: [],
    });

    render(<DBRepo />);

    expect(await screen.findByText("Archive data")).toBeVisible();
    await user.click(screen.getAllByRole("button", { name: "workspace:tinymce.dbrepo.expandDatabase" })[1]);
    editorListeners.get("dbrepo-insert")?.();

    expect(insertTemplateIntoTinyMCE).toHaveBeenCalledWith(
      "dbrepoLink",
      expect.objectContaining({
        recordURL: "https://dbrepo.example/database/db-2",
        name: "Archive data",
        dbrepoType: "database",
        databaseId: "db-2",
        resourceId: "",
        databaseName: "Archive data",
        query: "",
      }),
      activeEditor,
    );
  });

  it("shows category failures while leaving successful resources selectable", async () => {
    const user = userEvent.setup();
    const editorListeners = new Map<string, () => void>();
    const insertTemplateIntoTinyMCE = vi.fn();
    const close = vi.fn();
    const activeEditor = {
      getBody: () => document.body,
      on: vi.fn((eventName: string, callback: () => void) => editorListeners.set(eventName, callback)),
      off: vi.fn((eventName: string) => editorListeners.delete(eventName)),
      windowManager: { close },
    };
    (window as unknown as { tinymce?: unknown }).tinymce = {
      activeEditor,
    };
    (window as unknown as { RS?: unknown }).RS = {
      insertTemplateIntoTinyMCE,
    };
    mockAxios.onGet("/apps/dbrepo/databases").reply(200, [
      {
        id: "db-1",
        name: "Research data",
        url: "https://dbrepo.example/database/db-1",
      },
    ]);
    mockAxios.onGet("/apps/dbrepo/databases/db-1/resources").reply(200, {
      databaseId: "db-1",
      tables: [
        {
          id: "table-1",
          type: "table",
          label: "Experiments",
          secondaryText: "",
          url: "https://dbrepo.example/database/db-1/table/table-1",
        },
      ],
      views: [],
      subsets: [],
      failedTypes: ["view"],
    });

    render(<DBRepo />);

    expect(await screen.findByText("Research data")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "workspace:tinymce.dbrepo.expandDatabase" }));

    expect(await screen.findByText("workspace:tinymce.dbrepo.errors.view")).toBeVisible();
    expect(screen.getByText("Experiments")).toBeVisible();

    await user.click(screen.getByRole("radio", { name: "Experiments" }));
    editorListeners.get("dbrepo-insert")?.();

    expect(insertTemplateIntoTinyMCE).toHaveBeenCalledWith(
      "dbrepoLink",
      expect.objectContaining({
        recordURL: "https://dbrepo.example/database/db-1/table/table-1",
        name: "Experiments",
        dbrepoType: "table",
        databaseId: "db-1",
        resourceId: "table-1",
        databaseName: "Research data",
        query: "",
      }),
      activeEditor,
    );
  });

  it("shows an empty state when DBRepo returns no databases", async () => {
    mockAxios.onGet("/apps/dbrepo/databases").reply(200, []);

    render(<DBRepo />);

    expect(await screen.findByText("workspace:tinymce.dbrepo.empty")).toBeVisible();
  });

  it("builds DBRepo link template data", () => {
    expect(
      buildDBRepoLinkTemplateData({
        name: "<script>alert(1)</script>",
        url: "https://dbrepo.example/database/db-1",
        dbrepoType: "database",
        databaseId: "db-1",
        resourceId: "",
        databaseName: "Research data",
        query: "",
      }),
    ).toEqual({
      id: expect.stringMatching(/^dbrepo--?\d+$/),
      recordURL: "https://dbrepo.example/database/db-1",
      name: "<script>alert(1)</script>",
      dbrepoType: "database",
      databaseId: "db-1",
      resourceId: "",
      databaseName: "Research data",
      query: "",
      iconPath: DBREPO_LOGO_PATH,
    });
  });
});

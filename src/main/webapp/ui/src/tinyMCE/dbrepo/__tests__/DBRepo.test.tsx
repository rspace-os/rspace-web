import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { afterEach, describe, expect, it, vi } from "vitest";
import axios from "@/common/axios";
import DBRepo, { buildDatabaseTemplateData, type DBRepoDatabase, removeExternalDocumentIcon } from "../DBRepo";

const DBREPO_LOGO_PATH = "/images/icons/dbrepo.svg";

const mockAxios = new MockAdapter(axios);

afterEach(() => {
  mockAxios.reset();
  vi.restoreAllMocks();
  delete (window as unknown as { tinymce?: unknown }).tinymce;
  delete (window as unknown as { RS?: unknown }).RS;
});

describe("DBRepo dialog body", () => {
  it("lists databases and inserts a selected external document template", async () => {
    const user = userEvent.setup();
    const insertTemplateIntoTinyMCE = vi.fn();
    const close = vi.fn();
    const activeEditor = {
      getBody: () => document.body,
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
    await user.click(screen.getByRole("button", { name: "workspace:tinymce.dbrepo.insert" }));

    expect(insertTemplateIntoTinyMCE).toHaveBeenCalledWith(
      "insertedExternalDocumentTemplate",
      expect.objectContaining({
        fileStore: "dbrepo",
        recordURL: "https://dbrepo.example/database/db-1",
        name: "Research data",
        iconPath: DBREPO_LOGO_PATH,
        badgeIconPath: DBREPO_LOGO_PATH,
      }),
      activeEditor,
      expect.any(Function),
    );
    expect(close).toHaveBeenCalled();
  });

  it("loads resources when a database is expanded and inserts a selected resource", async () => {
    const user = userEvent.setup();
    const insertTemplateIntoTinyMCE = vi.fn();
    const close = vi.fn();
    const activeEditor = {
      getBody: () => document.body,
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
    await user.click(screen.getByRole("button", { name: "workspace:tinymce.dbrepo.insert" }));

    expect(insertTemplateIntoTinyMCE).toHaveBeenCalledWith(
      "insertedExternalDocumentTemplate",
      expect.objectContaining({
        recordURL: "https://dbrepo.example/database/db-1/view/view-1",
        name: "Recent experiments",
      }),
      activeEditor,
      expect.any(Function),
    );
    expect(close).toHaveBeenCalled();
  });

  it("selects a database when it is expanded", async () => {
    const user = userEvent.setup();
    const insertTemplateIntoTinyMCE = vi.fn();
    const close = vi.fn();
    const activeEditor = {
      getBody: () => document.body,
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
    await user.click(screen.getByRole("button", { name: "workspace:tinymce.dbrepo.insert" }));

    expect(insertTemplateIntoTinyMCE).toHaveBeenCalledWith(
      "insertedExternalDocumentTemplate",
      expect.objectContaining({
        recordURL: "https://dbrepo.example/database/db-2",
        name: "Archive data",
      }),
      activeEditor,
      expect.any(Function),
    );
  });

  it("shows category failures while leaving successful resources selectable", async () => {
    const user = userEvent.setup();
    const insertTemplateIntoTinyMCE = vi.fn();
    const close = vi.fn();
    const activeEditor = {
      getBody: () => document.body,
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
    await user.click(screen.getByRole("button", { name: "workspace:tinymce.dbrepo.insert" }));

    expect(insertTemplateIntoTinyMCE).toHaveBeenCalledWith(
      "insertedExternalDocumentTemplate",
      expect.objectContaining({
        recordURL: "https://dbrepo.example/database/db-1/table/table-1",
        name: "Experiments",
      }),
      activeEditor,
      expect.any(Function),
    );
  });

  it("shows an empty state when DBRepo returns no databases", async () => {
    mockAxios.onGet("/apps/dbrepo/databases").reply(200, []);

    render(<DBRepo />);

    expect(await screen.findByText("workspace:tinymce.dbrepo.empty")).toBeVisible();
  });

  it("builds external document template data for a database", () => {
    const database: DBRepoDatabase = {
      id: "db-1",
      name: "<script>alert(1)</script>",
      url: "https://dbrepo.example/database/db-1",
    };

    expect(buildDatabaseTemplateData(database)).toEqual({
      id: expect.stringMatching(/^dbrepo--?\d+$/),
      fileStore: "dbrepo",
      recordURL: "https://dbrepo.example/database/db-1",
      name: "<script>alert(1)</script>",
      iconPath: DBREPO_LOGO_PATH,
      badgeIconPath: DBREPO_LOGO_PATH,
    });
  });

  it("removes the upper template icon from inserted DBRepo attachments", () => {
    const templateData = buildDatabaseTemplateData({
      id: "db-1",
      name: "Research data",
      url: "https://dbrepo.example/database/db-1",
    });
    document.body.innerHTML = `
      <div class="externalAttachmentDiv mceNonEditable">
        <a href="${templateData.recordURL}" target="_blank">
          <img class="attachmentIcon" src="${templateData.iconPath}" height="32" width="32" />
        </a>
        <p class="attachmentP">
          <img class="externalLinkBadge" src="${templateData.badgeIconPath}" height="20" width="20" />
          <a class="attachmentLinked" id="attachOnText_${templateData.id}" data-externalFileStore="dbrepo"
            href="${templateData.recordURL}" target="_blank">${templateData.name}</a>
        </p>
      </div>`;

    removeExternalDocumentIcon({ getBody: () => document.body, windowManager: { close: () => {} } }, templateData);

    expect(document.body.querySelector(".attachmentIcon")).not.toBeInTheDocument();
    expect(document.body.querySelector(".externalLinkBadge")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Research data" })).toBeVisible();
  });
});

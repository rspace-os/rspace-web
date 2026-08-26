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
    await userEvent.click(screen.getByRole("button", { name: "workspace:tinymce.dbrepo.insert" }));

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

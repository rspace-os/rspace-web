import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { afterEach, describe, expect, it, vi } from "vitest";
import axios from "@/common/axios";
import DBRepo, { buildDatabaseLinkHtml, type DBRepoDatabase } from "../DBRepo";

const mockAxios = new MockAdapter(axios);

afterEach(() => {
  mockAxios.reset();
  vi.restoreAllMocks();
  delete (window as unknown as { tinymce?: unknown }).tinymce;
});

describe("DBRepo dialog body", () => {
  it("lists databases and inserts a selected link", async () => {
    const execCommand = vi.fn();
    const close = vi.fn();
    (window as unknown as { tinymce?: unknown }).tinymce = {
      activeEditor: {
        execCommand,
        windowManager: { close },
      },
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

    expect(execCommand).toHaveBeenCalledWith(
      "mceInsertContent",
      false,
      '<a href="https://dbrepo.example/database/db-1" target="_blank" rel="noopener noreferrer">Research data</a>',
    );
    expect(close).toHaveBeenCalled();
  });

  it("shows an empty state when DBRepo returns no databases", async () => {
    mockAxios.onGet("/apps/dbrepo/databases").reply(200, []);

    render(<DBRepo />);

    expect(await screen.findByText("workspace:tinymce.dbrepo.empty")).toBeVisible();
  });

  it("escapes database names in inserted link markup", () => {
    const database: DBRepoDatabase = {
      id: "db-1",
      name: "<script>alert(1)</script>",
      url: "https://dbrepo.example/database/db-1",
    };

    expect(buildDatabaseLinkHtml(database)).toBe(
      '<a href="https://dbrepo.example/database/db-1" target="_blank" rel="noopener noreferrer">&lt;script&gt;alert(1)&lt;/script&gt;</a>',
    );
  });
});

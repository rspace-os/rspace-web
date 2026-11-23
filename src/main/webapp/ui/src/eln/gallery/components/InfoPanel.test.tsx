import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw/http";
import React from "react";
import { beforeEach, describe, expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import { Description, LocalGalleryFile } from "../useGalleryListing";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";
import { CallableSnippetPreview } from "./CallableSnippetPreview";
import { InfoPanelForLargeViewports, InfoPanelForSmallViewports } from "./InfoPanel";
import { galleryFile } from "./VersionHistoryDialog.story";

describe("InfoPanelForSmallViewports", () => {
  test("opens the primary action for a snippet", async () => {
    server.use(
      http.get("/api/v1/snippets/42/content", () => HttpResponse.text("<p>Snippet body</p>")),
      http.get("/deploymentproperties/ajax/property", () => HttpResponse.json(false)),
      http.get("/collaboraOnline/supportedExts", () => HttpResponse.json({})),
      http.get("/officeOnline/supportedExts", () => HttpResponse.json({})),
      http.get("/workspace/getReferencingInventoryItems/:globalId", () => HttpResponse.json({ referencingItems: [] })),
      http.get("/workspace/getAttachingInventoryItems/:globalId", () => HttpResponse.json({ referencingItems: [] })),
    );
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={queryClient}>
        <CallableSnippetPreview>
          <InfoPanelForSmallViewports
            file={galleryFile({
              name: "Protocol snippet",
              isSnippet: true,
            })}
          />
        </CallableSnippetPreview>
      </QueryClientProvider>,
    );

    await user.click(screen.getByRole("button", { name: "gallery:actionsMenu.view" }));

    expect(await screen.findByText("Snippet body")).toBeVisible();
  });
});

function SelectedDesktopInfoPanel({ file }: { file: LocalGalleryFile }) {
  const selection = useGallerySelection();
  React.useState(() => selection.append(file));
  return <InfoPanelForLargeViewports />;
}

describe("InfoPanelForLargeViewports inline rename", () => {
  beforeEach(() => {
    server.use(
      http.get("/gallery/ajax/getLinkedDocuments/:id", () => HttpResponse.json([])),
      http.get("/deploymentproperties/ajax/property", () => HttpResponse.json(false)),
      http.get("/collaboraOnline/supportedExts", () => HttpResponse.json({})),
      http.get("/officeOnline/supportedExts", () => HttpResponse.json({})),
      http.get("/workspace/getReferencingInventoryItems/:globalId", () => HttpResponse.json({ referencingItems: [] })),
      http.get("/workspace/getAttachingInventoryItems/:globalId", () => HttpResponse.json({ referencingItems: [] })),
    );
  });

  test.each([
    {
      name: "Protocol v1.2",
      type: "Snippet",
      replacement: null,
      expected: "Protocol v1.2",
      scenario: "keeps the full dotted snippet name on focus and unchanged Enter",
    },
    {
      name: "Protocol v1.2",
      type: "Snippet",
      replacement: "Updated protocol v1",
      expected: "Updated protocol v1.2",
      scenario: "keeps the dotted snippet suffix when editing its prefix",
    },
    {
      name: "README",
      type: "Document",
      replacement: "notes.txt",
      expected: "notes.txt",
      scenario: "shows the final name after adding the first dot to a filename",
    },
  ])("$scenario", async ({ name, type, replacement, expected }) => {
    const postedNames: FormDataEntryValue[] = [];
    server.use(
      http.post("/workspace/editor/structuredDocument/ajax/rename", async ({ request }) => {
        const form = await request.formData();
        const newName = form.get("newName");
        if (newName !== null) postedNames.push(newName);
        return HttpResponse.json({});
      }),
    );
    const user = userEvent.setup();
    const file = new LocalGalleryFile({
      id: 42,
      globalId: "GL42",
      name,
      extension: null,
      type,
      creationDate: new Date(),
      modificationDate: new Date(),
      isSystemFolder: false,
      isSharedFolder: false,
      ownerId: 1,
      ownerName: "Joe Bloggs",
      ownerUsername: "joebloggs",
      description: new Description({ key: "empty" }),
      version: 1,
      size: 1024,
      path: [],
      thumbnailId: null,
      gallerySection: type === "Snippet" ? "Snippets" : "Documents",
      originalImageId: null,
      metadata: {},
      token: "",
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <GallerySelection>
          <SelectedDesktopInfoPanel file={file} />
        </GallerySelection>
      </QueryClientProvider>,
    );
    const editor = screen.getByRole("textbox", { name: "gallery:infoPanel.nameLabel" });
    await user.click(editor);
    expect(editor).toHaveValue(name);
    if (replacement !== null) {
      if (file.isSnippet) {
        await user.type(editor, replacement, { initialSelectionStart: 0, initialSelectionEnd: "Protocol v1".length });
      } else {
        await user.clear(editor);
        await user.type(editor, replacement);
      }
    }
    await user.keyboard("{Enter}");
    await waitFor(() => expect(postedNames).toEqual([expected]));
    await waitFor(() => expect(editor).not.toHaveFocus());
    expect(file.name).toBe(expected);
    expect(editor).toHaveValue(expected);
  });
});

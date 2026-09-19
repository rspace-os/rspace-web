import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import { CallableSnippetPreview } from "./CallableSnippetPreview";
import { InfoPanelForSmallViewports } from "./InfoPanel";
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

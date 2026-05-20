import { beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { render, screen, waitFor } from "@/__tests__/customQueries";
import {
  CallableSnippetPreviewStory,
  CallableSnippetPreviewWithError,
  CallableSnippetPreviewWithTableContent,
} from "../CallableSnippetPreview.story";

vi.mock("@/hooks/auth/useOauthToken", () => ({
  __esModule: true,
  default: () => ({
    getToken: () => Promise.resolve("token"),
  }),
}));

const mockAxios = new MockAdapter(axios);

describe("CallableSnippetPreview", () => {
  beforeEach(() => {
    mockAxios.reset();
  });

  test("opens the dialog and renders snippet content", async () => {
    const user = userEvent.setup();
    mockAxios.onGet("/api/v1/snippets/123/content").reply(200, "<p>Test snippet content</p>");

    render(<CallableSnippetPreviewStory />);

    await user.click(screen.getByRole("button", { name: /open snippet preview/i }));

    expect(await screen.findByRole("dialog")).toBeVisible();
    expect(await screen.findByText(/test snippet content/i)).toBeVisible();
  });

  test("closes the dialog when escape is pressed", async () => {
    const user = userEvent.setup();
    mockAxios.onGet("/api/v1/snippets/123/content").reply(200, "<p>Test snippet content</p>");

    render(<CallableSnippetPreviewStory />);

    await user.click(screen.getByRole("button", { name: /open snippet preview/i }));
    expect(await screen.findByRole("dialog")).toBeVisible();

    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  test("renders table content", async () => {
    const user = userEvent.setup();
    mockAxios.onGet("/api/v1/snippets/124/content").reply(
      200,
      `<table><thead><tr><th>Header 1</th><th>Header 2</th><th>Header 3</th></tr></thead><tbody><tr><td>Cell 1</td><td>Cell 2</td><td>Cell 3</td></tr><tr><td>Cell 4</td><td>Cell 5</td><td>Cell 6</td></tr></tbody></table>`,
    );

    render(<CallableSnippetPreviewWithTableContent />);

    await user.click(
      screen.getByRole("button", { name: /open table snippet preview/i }),
    );

    const dialog = await screen.findByRole("dialog");
    expect(dialog.querySelectorAll("th")).toHaveLength(3);
    expect(dialog.querySelectorAll("td")).toHaveLength(6);
    expect(screen.getByText("Header 1")).toBeVisible();
    expect(screen.getByText("Cell 1")).toBeVisible();
  });

  test("renders an error message when loading fails", async () => {
    const user = userEvent.setup();
    mockAxios.onGet("/api/v1/snippets/999/content").reply(500, {
      message: "Failed to load snippet content",
    });

    render(<CallableSnippetPreviewWithError />);

    await user.click(screen.getByRole("button", { name: /open error snippet preview/i }));

    expect(
      await screen.findByText(/failed to load snippet content/i),
    ).toBeVisible();
  });

  test("is accessible when open", async () => {
    const user = userEvent.setup();
    mockAxios.onGet("/api/v1/snippets/123/content").reply(200, "<p>Test snippet content</p>");

    const { baseElement } = render(<CallableSnippetPreviewStory />);

    await user.click(screen.getByRole("button", { name: /open snippet preview/i }));
    await screen.findByText(/test snippet content/i);

    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    await expect(baseElement).toBeAccessible();
  });
});

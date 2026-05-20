import { beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import {
  CallableSnippetPreviewStory,
  CallableSnippetPreviewWithError,
  CallableSnippetPreviewWithTableContent,
} from "./CallableSnippetPreview.story";

vi.mock("@/hooks/auth/useOauthToken", () => ({
  __esModule: true,
  default: () => ({
    getToken: () => Promise.resolve("token"),
  }),
}));

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  mockAxios.reset();
  mockAxios.onGet("/api/v1/snippets/123/content").reply(200, "<p>Test snippet content</p>");
  mockAxios.onGet("/api/v1/snippets/124/content").reply(
    200,
    `<table><thead><tr><th>Header 1</th><th>Header 2</th><th>Header 3</th></tr></thead><tbody><tr><td>Cell 1</td><td>Cell 2</td><td>Cell 3</td></tr><tr><td>Cell 4</td><td>Cell 5</td><td>Cell 6</td></tr></tbody></table>`,
  );
  mockAxios.onGet("/api/v1/snippets/999/content").reply(500, {
    message: "Failed to load snippet content",
    error: "Internal Server Error",
  });
});

describe("CallableSnippetPreview", () => {
  test("opens the preview dialog and renders snippet content", async () => {
    const user = userEvent.setup();
    render(<CallableSnippetPreviewStory />);

    await user.click(screen.getByRole("button", { name: /open snippet preview/i }));

    expect(await screen.findByRole("dialog")).toBeVisible();
    expect(await screen.findByText(/test snippet content/i)).toBeVisible();
  });

  test("renders table snippet content", async () => {
    const user = userEvent.setup();
    render(<CallableSnippetPreviewWithTableContent />);

    await user.click(screen.getByRole("button", { name: /open table snippet preview/i }));

    expect(await screen.findByRole("table")).toBeVisible();
    expect(screen.getByText("Header 1")).toBeVisible();
    expect(screen.getByText("Cell 6")).toBeVisible();
  });

  test("shows an error when the snippet request fails", async () => {
    const user = userEvent.setup();
    render(<CallableSnippetPreviewWithError />);

    await user.click(screen.getByRole("button", { name: /open error snippet preview/i }));

    expect(
      await screen.findByText(/error: failed to load snippet content/i),
    ).toBeVisible();
  });

  test("is accessible once loaded", async () => {
    render(<CallableSnippetPreviewStory />);

    expect(await screen.findByRole("button", { name: /open snippet preview/i })).toBeVisible();
    /* eslint-disable @typescript-eslint/no-unsafe-call */
    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    await expect(document.body).toBeAccessible();
    /* eslint-enable @typescript-eslint/no-unsafe-call */
  });
});

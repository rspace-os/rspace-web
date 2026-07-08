import { afterEach, beforeEach, describe, expect, test } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { expectAccessible, render, within } from "@/__tests__/customQueries";
import axios from "@/common/axios";
import {
  CallableSnippetPreviewStory,
  CallableSnippetPreviewWithError,
  CallableSnippetPreviewWithTableContent,
} from "./CallableSnippetPreview.story";

const mockAxios = new MockAdapter(axios);

const tableHtml = `
        <table>
          <thead>
            <tr>
              <th>Header 1</th>
              <th>Header 2</th>
              <th>Header 3</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Cell 1</td>
              <td>Cell 2</td>
              <td>Cell 3</td>
            </tr>
            <tr>
              <td>Cell 4</td>
              <td>Cell 5</td>
              <td>Cell 6</td>
            </tr>
          </tbody>
        </table>
      `;

beforeEach(() => {
  mockAxios.reset();
  mockAxios.onGet("/api/v1/snippets/123/content").reply(200, "<p>Test snippet content</p>");
  mockAxios.onGet("/api/v1/snippets/124/content").reply(200, tableHtml);
  mockAxios.onGet("/api/v1/snippets/999/content").reply(500, {
    message: "Failed to load snippet content",
    error: "Internal Server Error",
  });
});

afterEach(() => {
  cleanup();
});

describe("CallableSnippetPreview", () => {
  describe("Dialog opening and closing", () => {
    test("Should open the preview dialog when triggered", async () => {
      const user = userEvent.setup();
      render(<CallableSnippetPreviewStory />);

      await user.click(screen.getByRole("button", { name: /open.*snippet.*preview/i }));

      expect(await screen.findByRole("dialog")).toBeVisible();
      expect(screen.getByText("gallery:snippetPreview.title")).toBeVisible();
    });

    test("Should close the dialog when close button is clicked", async () => {
      const user = userEvent.setup();
      render(<CallableSnippetPreviewStory />);

      await user.click(screen.getByRole("button", { name: /open.*snippet.*preview/i }));
      expect(await screen.findByRole("dialog")).toBeVisible();
      expect(screen.getByText("gallery:snippetPreview.title")).toBeVisible();

      await user.click(screen.getByRole("button", { name: "common:actions.close" }));

      await waitFor(() => {
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    });

    test("Should close the dialog when Escape key is pressed", async () => {
      const user = userEvent.setup();
      render(<CallableSnippetPreviewStory />);

      await user.click(screen.getByRole("button", { name: /open.*snippet.*preview/i }));
      expect(await screen.findByRole("dialog")).toBeVisible();
      expect(screen.getByText("gallery:snippetPreview.title")).toBeVisible();

      await user.keyboard("{Escape}");

      await waitFor(() => {
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    });
  });

  describe("Content rendering", () => {
    test("Should show loading state initially", async () => {
      const user = userEvent.setup();
      // Delay the request so the loading state is observable.
      mockAxios.onGet("/api/v1/snippets/123/content").reply(
        () =>
          new Promise((resolve) => {
            setTimeout(() => resolve([200, "<p>Test snippet content</p>"]), 1000);
          }),
      );
      render(<CallableSnippetPreviewStory />);

      await user.click(screen.getByRole("button", { name: /open.*snippet.*preview/i }));

      expect(await screen.findByText("gallery:snippetPreview.loading")).toBeVisible();
    });

    test("Should display snippet content after loading", async () => {
      const user = userEvent.setup();
      render(<CallableSnippetPreviewStory />);

      await user.click(screen.getByRole("button", { name: /open.*snippet.*preview/i }));

      expect(await screen.findByText("Test snippet content")).toBeVisible();
    });

    test("Should render HTML tables correctly", async () => {
      const user = userEvent.setup();
      render(<CallableSnippetPreviewWithTableContent />);

      await user.click(screen.getByRole("button", { name: /open.*snippet.*preview/i }));

      const dialog = await screen.findByRole("dialog");
      await within(dialog).findByText("Header 1");

      expect(within(dialog).getByRole("table")).toBeVisible();
      expect(dialog.querySelectorAll("th")).toHaveLength(3);
      expect(dialog.querySelectorAll("td")).toHaveLength(6);
      expect(within(dialog).getByText("Header 1")).toBeVisible();
      expect(within(dialog).getByText("Cell 1")).toBeVisible();
    });

    test("Should display error message when loading fails", async () => {
      const user = userEvent.setup();
      render(<CallableSnippetPreviewWithError />);

      await user.click(screen.getByRole("button", { name: /open.*snippet.*preview/i }));

      expect(await screen.findByText("gallery:snippetPreview.error")).toBeVisible();
    });
  });

  describe("Accessibility", () => {
    test("Should be accessible when opened", async () => {
      const user = userEvent.setup();
      render(<CallableSnippetPreviewStory />);

      await user.click(screen.getByRole("button", { name: /open.*snippet.*preview/i }));

      const dialog = await screen.findByRole("dialog");
      expect(dialog).toBeVisible();
      expect(screen.getByText("gallery:snippetPreview.title")).toBeVisible();
      expect(screen.getByRole("button", { name: "common:actions.close" })).toBeVisible();
    });

    test("Should have no axe violations", async () => {
      const user = userEvent.setup();
      const { baseElement } = render(<CallableSnippetPreviewStory />);

      await user.click(screen.getByRole("button", { name: /open.*snippet.*preview/i }));

      await screen.findByRole("dialog");
      // Wait for content to load so the rendered tree is stable.
      await screen.findByText("Test snippet content");

      await expectAccessible(baseElement);
    });
  });
});

import { afterEach, describe, expect, test } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import { render, screen } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";

import axios from "@/common/axios";
import { MoveDialogStory } from "./MoveDialog.story";

const mockAxios = new MockAdapter(axios);

afterEach(() => {
  mockAxios.reset();
});

describe("MoveDialog", () => {
  test("Should request only folders", async () => {
    mockAxios.onGet("/gallery/getUploadedFiles").reply(200, {
      data: {
        parentId: 1,
        items: {
          results: [],
        },
      },
    });

    render(<MoveDialogStory />);

    await screen.findByRole("dialog");

    const getUploadedFilesCalls = mockAxios.history.get.filter(({ url }) => /getUploadedFiles/.test(url ?? ""));
    expect(getUploadedFilesCalls.length).toBeGreaterThan(0);
    const params = getUploadedFilesCalls[0].params as URLSearchParams;
    expect(params.get("foldersOnly")).toBe("true");
  });
});

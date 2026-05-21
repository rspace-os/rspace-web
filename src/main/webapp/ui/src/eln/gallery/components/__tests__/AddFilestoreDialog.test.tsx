import { beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { LandmarksProvider } from "@/components/LandmarksContext";
import AddFilestoreDialog from "../AddFilestoreDialog";
import { render, screen, waitFor } from "@/__tests__/customQueries";

const getMock = vi.fn();
const postMock = vi.fn();

vi.mock("@/common/axios", () => ({
  __esModule: true,
  default: {
    create: vi.fn(() => ({
      get: getMock,
      post: postMock,
    })),
  },
}));

vi.mock("@/hooks/auth/useOauthToken", () => ({
  __esModule: true,
  default: () => ({
    getToken: () => Promise.resolve("token"),
  }),
}));

vi.mock("../FilestoreLoginDialog", () => ({
  useFilestoreLogin: () => ({
    login: () => Promise.resolve(true),
  }),
}));

function renderDialog(onClose = vi.fn()) {
  return {
    onClose,
    ...render(
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <LandmarksProvider>
          <AddFilestoreDialog open={true} onClose={onClose} />
        </LandmarksProvider>
      </ThemeProvider>,
    ),
  };
}

describe("AddFilestoreDialog", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    getMock.mockImplementation((url: string) => {
      if (url === "filesystems") {
        return {
          data: [
            {
              id: 1,
              name: "irods test",
              url: "irods-test.researchspace.com",
            },
          ],
        };
      }
      if (url === "filesystems/1/browse?remotePath=%2F") {
        return {
          data: {
            content: [
              {
                name: "selenium",
                folder: true,
                nfsId: 10809,
              },
              {
                name: "test",
                folder: true,
                nfsId: 10111,
              },
            ],
          },
        };
      }
      if (url === "filesystems/1/browse?remotePath=%2Ftest%2F") {
        return {
          data: {
            content: [],
          },
        };
      }
      throw new Error(`Unexpected GET ${url}`);
    });
    postMock.mockResolvedValue({ data: {} });
  });

  test("lets the user choose a filesystem and folder and submit the new filestore", async () => {
    const user = userEvent.setup();
    const { onClose } = renderDialog();

    await user.click(await screen.findByRole("radio", { name: /irods test/i }));
    await user.click(screen.getByRole("button", { name: /choose filesystem/i }));

    await user.click(await screen.findByText("test"));
    await user.click(screen.getByRole("button", { name: /choose folder/i }));

    await user.type(screen.getByRole("textbox", { name: /filestore name/i }), "My filestore");
    await user.click(screen.getByRole("button", { name: /add filestore/i }));

    await waitFor(() => {
      expect(postMock).toHaveBeenCalledWith(
        "filestores",
        {},
        {
          params: {
            filesystemId: 1,
            name: "My filestore",
            pathToSave: "/test/",
          },
        },
      );
    });
    expect(onClose).toHaveBeenCalledWith(true);
  });

  test("is accessible on the initial step", async () => {
    const { baseElement } = renderDialog();
    await screen.findByRole("radio", { name: /irods test/i });

    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
  });
});

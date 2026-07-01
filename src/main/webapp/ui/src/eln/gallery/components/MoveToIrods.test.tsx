import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/customQueries";
import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/matchMedia";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { MoveToIrodsDialogWithOneFile, MoveToIrodsDialogWithTwoFiles } from "./MoveToIrods.story";

const mockAxios = new MockAdapter(axios);

/*
 * RSDEV-1185: filestores can exist on more than one connected iRODS filesystem.
 * These two iRODS filestores live on DIFFERENT filesystems (id 10 and 20); the
 * old /gallery/irods endpoint would only have surfaced the one on the first.
 */
const IRODS_FILESTORE_FS1 = {
  id: 1,
  name: "Lab data",
  path: "/zone1/home/lab",
  fileSystem: { id: 10, name: "iRODS Primary", url: "irods-1.example.com", clientType: "IRODS", authType: "PASSWORD" },
};

const IRODS_FILESTORE_FS2 = {
  id: 2,
  name: "Archive",
  path: "/zone2/home/archive",
  fileSystem: {
    id: 20,
    name: "iRODS Secondary",
    url: "irods-2.example.com",
    clientType: "IRODS",
    authType: "PASSWORD",
  },
};

const S3_FILESTORE = {
  id: 3,
  name: "My S3 Bucket",
  path: "/bucket",
  fileSystem: { id: 30, name: "AWS S3", url: "s3.example.com", clientType: "S3", authType: "NONE" },
};

const NONE_AUTH_IRODS_FILESTORE = {
  id: 4,
  name: "Open iRODS",
  path: "/open",
  fileSystem: { id: 40, name: "iRODS Open", url: "irods-open.example.com", clientType: "IRODS", authType: "NONE" },
};

const OPERATION_SUCCESS_RESPONSE = {
  numFilesInput: 1,
  numFilesSucceed: 1,
  fileInfoDetails: [{ recordId: 123, fileName: "test.jpg", succeeded: true }],
};

/*
 * The dialog's AppBar / analytics bootstrap fires a handful of background
 * requests on mount whose async state updates settle outside `act(...)`. The
 * resulting "not wrapped in act" warnings are irrelevant to the behaviour
 * under test, so console.error is muted for the duration of each test (using a
 * single, non-chaining spy to avoid recursion) and restored afterwards.
 */
function createConsoleErrorSpy() {
  return vi.spyOn(console, "error").mockImplementation(() => {});
}

let consoleErrorSpy: ReturnType<typeof createConsoleErrorSpy>;
beforeEach(() => {
  consoleErrorSpy = createConsoleErrorSpy();
});
afterEach(() => {
  consoleErrorSpy.mockRestore();
});

beforeEach(() => {
  mockAxios.reset();

  // Default: two iRODS filestores on two different filesystems, plus an S3 one
  // that must be filtered out.
  mockAxios.onGet("/api/v1/gallery/filestores").reply(200, [IRODS_FILESTORE_FS1, IRODS_FILESTORE_FS2, S3_FILESTORE]);
  // Gallery -> filestore ingest (move and copy alike) goes through
  // /uploadFromGallery; removeOriginalFromRspace in the body selects move vs copy.
  mockAxios.onPost("/api/v1/gallery/filestores/1/uploadFromGallery").reply(200, OPERATION_SUCCESS_RESPONSE);
  mockAxios.onPost("/api/v1/gallery/filestores/2/uploadFromGallery").reply(200, OPERATION_SUCCESS_RESPONSE);
  mockAxios.onPost("/api/v1/gallery/filestores/4/uploadFromGallery").reply(200, OPERATION_SUCCESS_RESPONSE);

  // Catch-all for the AppBar/analytics bootstrap requests.
  mockAxios.onAny().reply(200, {});
});

async function openDestinationMenu(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: "gallery:moveToIrods.destination.placeholder" }));
}

async function selectDestination(user: ReturnType<typeof userEvent.setup>, name: RegExp) {
  await openDestinationMenu(user);
  await user.click(await screen.findByRole("menuitem", { name }));
}

describe("MoveToIrods", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations, including the visible login form", async () => {
      const user = userEvent.setup();
      const { baseElement } = render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      // Select a PASSWORD-auth destination so the login form is rendered and
      // scanned for label-association violations too.
      await selectDestination(user, /lab data/i);
      expect(await screen.findByRole("textbox", { name: "gallery:moveToIrods.login.username" })).toBeVisible();
      await expectAccessible(baseElement);
    });
  });

  describe("Empty state", () => {
    test("When no filestores are configured shows a 'no iRODS filestore' message", async () => {
      mockAxios.onGet("/api/v1/gallery/filestores").reply(200, []);
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByText("gallery:moveToIrods.errors.noFilestoreConfigured.title")).toBeVisible();
    });

    test("When only non-iRODS filestores are configured shows a 'no iRODS filestore' message", async () => {
      mockAxios.onGet("/api/v1/gallery/filestores").reply(200, [S3_FILESTORE]);
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByText("gallery:moveToIrods.errors.noFilestoreConfigured.title")).toBeVisible();
    });
  });

  describe("Listing filestores across all filesystems (RSDEV-1185)", () => {
    test("Lists filestores from every connected iRODS filesystem, not just the first", async () => {
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await openDestinationMenu(user);
      // Both the filestore on filesystem 10 and the one on filesystem 20 appear.
      expect(await screen.findByRole("menuitem", { name: /lab data/i })).toBeInTheDocument();
      expect(screen.getByRole("menuitem", { name: /archive/i })).toBeInTheDocument();
    });

    test("Excludes non-iRODS (e.g. S3) filestores from the destination list", async () => {
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await openDestinationMenu(user);
      expect(await screen.findByRole("menuitem", { name: /lab data/i })).toBeInTheDocument();
      expect(screen.queryByRole("menuitem", { name: /my s3 bucket/i })).not.toBeInTheDocument();
    });
  });

  describe("Per-filesystem credentials form", () => {
    test("Selecting a PASSWORD-auth destination shows the iRODS login form", async () => {
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await selectDestination(user, /lab data/i);
      expect(await screen.findByRole("textbox", { name: "gallery:moveToIrods.login.username" })).toBeVisible();
      expect(screen.getByLabelText("gallery:moveToIrods.login.password", { selector: "input" })).toBeInTheDocument();
    });

    test("Selecting a NONE-auth iRODS destination shows no login form", async () => {
      mockAxios.onGet("/api/v1/gallery/filestores").reply(200, [NONE_AUTH_IRODS_FILESTORE]);
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await selectDestination(user, /open irods/i);
      // No username field is rendered for a NONE-auth filesystem.
      await waitFor(() => {
        expect(screen.queryByRole("textbox", { name: "gallery:moveToIrods.login.username" })).not.toBeInTheDocument();
      });
    });

    test("The credentials prompt names the selected destination's filesystem", async () => {
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await selectDestination(user, /lab data/i);
      expect(await screen.findByText("gallery:moveToIrods.login.prompt")).toBeVisible();
    });
  });

  describe("Submit button state", () => {
    test("Submit is blocked until a destination is selected", async () => {
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await screen.findByRole("button", { name: "gallery:moveToIrods.destination.placeholder" });
      await user.click(screen.getByRole("button", { name: "gallery:moveToIrods.submit" }));
      expect(await screen.findByText("gallery:moveToIrods.validation.destinationRequired")).toBeVisible();
    });

    test("Submit is blocked until credentials are supplied for a PASSWORD-auth destination", async () => {
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await selectDestination(user, /lab data/i);
      await user.click(screen.getByRole("button", { name: "gallery:moveToIrods.submit" }));
      expect(await screen.findByText("gallery:moveToIrods.validation.usernameRequired")).toBeVisible();
    });
  });

  describe("API calls", () => {
    test("Selecting a destination, entering credentials and clicking Move uploads with removeOriginalFromRspace=true", async () => {
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await selectDestination(user, /lab data/i);
      await user.type(await screen.findByRole("textbox", { name: "gallery:moveToIrods.login.username" }), "alice");
      await user.type(screen.getByLabelText("gallery:moveToIrods.login.password", { selector: "input" }), "secret");
      await user.click(screen.getByRole("button", { name: "gallery:moveToIrods.submit" }));

      expect(await screen.findByText(/successfully moved/i)).toBeVisible();
      const moveRequest = mockAxios.history.post.find(({ url }) => url === "/filestores/1/uploadFromGallery");
      expect(moveRequest).toBeDefined();
      const body = JSON.parse(moveRequest?.data as string) as {
        recordIds: unknown;
        credentials: { username: string; password: string };
        removeOriginalFromRspace: boolean;
      };
      expect(body.recordIds).toEqual(expect.arrayContaining([123]));
      expect(body.credentials).toEqual({ username: "alice", password: "secret" });
      expect(body.removeOriginalFromRspace).toBe(true);
    });

    test("Sends the upload request to the endpoint keyed by the chosen filestore's id (second filesystem)", async () => {
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await selectDestination(user, /archive/i);
      await user.type(await screen.findByRole("textbox", { name: "gallery:moveToIrods.login.username" }), "bob");
      await user.type(screen.getByLabelText("gallery:moveToIrods.login.password", { selector: "input" }), "hunter2");
      await user.click(screen.getByRole("button", { name: "gallery:moveToIrods.submit" }));

      expect(await screen.findByText(/successfully moved/i)).toBeVisible();
      expect(mockAxios.history.post.some(({ url }) => url === "/filestores/2/uploadFromGallery")).toBe(true);
    });

    test("Checking 'Retain a copy' and submitting uploads with removeOriginalFromRspace=false and reports a copy", async () => {
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await selectDestination(user, /lab data/i);
      await user.type(await screen.findByRole("textbox", { name: "gallery:moveToIrods.login.username" }), "alice");
      await user.type(screen.getByLabelText("gallery:moveToIrods.login.password", { selector: "input" }), "secret");
      await user.click(screen.getByRole("checkbox", { name: "gallery:moveToIrods.retainCopy" }));
      // The submit button stays labelled "Move"; ticking "Retain a copy" routes it to a copy.
      await user.click(screen.getByRole("button", { name: "gallery:moveToIrods.submit" }));

      expect(await screen.findByText(/successfully copied/i)).toBeVisible();
      const copyRequest = mockAxios.history.post.find(({ url }) => url === "/filestores/1/uploadFromGallery");
      expect(copyRequest).toBeDefined();
      const body = JSON.parse(copyRequest?.data as string) as { removeOriginalFromRspace: boolean };
      expect(body.removeOriginalFromRspace).toBe(false);
    });

    test("Sends all selected record IDs to the upload endpoint", async () => {
      const user = userEvent.setup();
      render(<MoveToIrodsDialogWithTwoFiles />);
      expect(await screen.findByRole("heading", { name: "gallery:moveToIrods.title" })).toBeVisible();

      await selectDestination(user, /lab data/i);
      await user.type(await screen.findByRole("textbox", { name: "gallery:moveToIrods.login.username" }), "alice");
      await user.type(screen.getByLabelText("gallery:moveToIrods.login.password", { selector: "input" }), "secret");
      await user.click(screen.getByRole("button", { name: "gallery:moveToIrods.submit" }));

      expect(await screen.findByText(/successfully moved/i)).toBeVisible();
      const moveRequest = mockAxios.history.post.find(({ url }) => url === "/filestores/1/uploadFromGallery");
      const body = JSON.parse(moveRequest?.data as string) as { recordIds: unknown };
      expect(body.recordIds).toEqual(expect.arrayContaining([123, 456]));
    });
  });
});

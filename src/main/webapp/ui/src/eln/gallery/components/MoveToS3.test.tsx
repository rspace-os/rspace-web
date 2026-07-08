import "@/__tests__/__mocks__/muiTransitions";
import { beforeEach, describe, expect, test } from "vitest";
import { expectAccessible } from "@/__tests__/customQueries";
import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/matchMedia";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { stubAppChrome } from "@/__tests__/helpers/appChrome";
import axios from "@/common/axios";
import {
  MoveToS3DialogInTransferMode,
  MoveToS3DialogInTransferModeWithTwoFiles,
  MoveToS3DialogWithOneFile,
  MoveToS3DialogWithTwoFiles,
} from "./MoveToS3.story";

const mockAxios = new MockAdapter(axios);

const S3_FILESTORE = {
  id: 1,
  name: "My S3 Bucket",
  fileSystem: { id: 10, name: "AWS S3", clientType: "S3" },
};

const IRODS_FILESTORE = {
  id: 2,
  name: "iRODS server",
  fileSystem: { id: 20, name: "iRODS", clientType: "IRODS" },
};

const OPERATION_SUCCESS_RESPONSE = {
  numFilesInput: 1,
  numFilesSucceed: 1,
  fileInfoDetails: [{ recordId: 123, fileName: "test.jpg", succeeded: true }],
};

beforeEach(() => {
  mockAxios.reset();
  stubAppChrome(mockAxios);

  // Default: one S3 filestore available
  mockAxios.onGet("/api/v1/gallery/filestores").reply(200, [S3_FILESTORE]);
  // move and copy both POST to uploadFromGallery, differentiated by removeOriginalFromRspace
  mockAxios.onPost("/api/v1/gallery/filestores/1/uploadFromGallery").reply(200, OPERATION_SUCCESS_RESPONSE);
  // Default transfer route: source filestore id=2 (as used in transfer stories)
  mockAxios.onPost("/api/v1/gallery/filestores/2/transfer").reply(200, OPERATION_SUCCESS_RESPONSE);

  // Catch-all for the AppBar/analytics bootstrap requests (mirrors the
  // analyticsProperties / preference / deploymentproperties / nav stubs from
  // the original Playwright spec).
  mockAxios.onAny().reply(200, {});
});

async function selectMyS3Bucket(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: /select a filestore/i }));
  await user.click(await screen.findByRole("menuitem", { name: /my s3 bucket/i }));
}

describe("MoveToS3", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations", async () => {
      const { baseElement } = render(<MoveToS3DialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: /move to s3/i })).toBeVisible();
      // Wait for the filestore listing to resolve before scanning.
      await screen.findByRole("button", { name: /select a filestore/i });
      await expectAccessible(baseElement);
    });
  });

  describe("Empty state", () => {
    test("When no filestores are configured shows a 'no S3 filestore' message", async () => {
      mockAxios.onGet("/api/v1/gallery/filestores").reply(200, []);
      render(<MoveToS3DialogWithOneFile />);
      expect(await screen.findByText(/no s3 filestore has been configured/i)).toBeVisible();
    });

    test("When only non-S3 filestores are configured shows a 'no S3 filestore' message", async () => {
      mockAxios.onGet("/api/v1/gallery/filestores").reply(200, [IRODS_FILESTORE]);
      render(<MoveToS3DialogWithOneFile />);
      expect(await screen.findByText(/no s3 filestore has been configured/i)).toBeVisible();
    });
  });

  describe("Submit button state", () => {
    test("Submit button is disabled until a filestore is selected", async () => {
      const user = userEvent.setup();
      render(<MoveToS3DialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: /move to s3/i })).toBeVisible();

      // The submit button is not HTML-disabled; instead, submitting while no
      // filestore is selected surfaces a validation popover.
      await screen.findByRole("button", { name: /select a filestore/i });
      await user.click(screen.getByRole("button", { name: /^move$/i }));
      expect(await screen.findByText(/a destination filestore is required/i)).toBeVisible();

      // Dismiss the validation popover (the spec clicked its backdrop).
      await user.keyboard("{Escape}");
      await waitFor(() => {
        expect(screen.queryByText(/a destination filestore is required/i)).not.toBeInTheDocument();
      });

      // Selecting a filestore clears the validation error so submit succeeds.
      await selectMyS3Bucket(user);
      expect(await screen.findByRole("button", { name: /^move$/i })).toBeVisible();
    });

    test("Submit button label switches from 'Move' to 'Copy' when retain-copy is checked", async () => {
      const user = userEvent.setup();
      render(<MoveToS3DialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: /move to s3/i })).toBeVisible();
      expect(await screen.findByRole("button", { name: /^move$/i })).toBeVisible();

      await user.click(screen.getByRole("checkbox", { name: /retain a copy in rspace/i }));
      expect(await screen.findByRole("button", { name: /^copy$/i })).toBeVisible();
    });
  });

  describe("API calls", () => {
    test("Selecting a filestore and clicking Move calls the move endpoint", async () => {
      const user = userEvent.setup();
      render(<MoveToS3DialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: /move to s3/i })).toBeVisible();

      await selectMyS3Bucket(user);
      await user.click(screen.getByRole("button", { name: /^move$/i }));

      expect(await screen.findByText(/successfully moved/i)).toBeVisible();
      const req = mockAxios.history.post.find(({ url }) => url === "/filestores/1/uploadFromGallery");
      expect(req).toBeDefined();
      expect((JSON.parse(req?.data as string) as { removeOriginalFromRspace: boolean }).removeOriginalFromRspace).toBe(
        true,
      );
    });

    test("Checking 'Retain a copy' and clicking Copy calls the copy endpoint", async () => {
      const user = userEvent.setup();
      render(<MoveToS3DialogWithOneFile />);
      expect(await screen.findByRole("heading", { name: /move to s3/i })).toBeVisible();

      await selectMyS3Bucket(user);
      await user.click(screen.getByRole("checkbox", { name: /retain a copy in rspace/i }));
      await user.click(screen.getByRole("button", { name: /^copy$/i }));

      expect(await screen.findByText(/successfully copied/i)).toBeVisible();
      const req = mockAxios.history.post.find(({ url }) => url === "/filestores/1/uploadFromGallery");
      expect(req).toBeDefined();
      expect((JSON.parse(req?.data as string) as { removeOriginalFromRspace: boolean }).removeOriginalFromRspace).toBe(
        false,
      );
    });

    test("The correct record IDs are sent to the move endpoint", async () => {
      const user = userEvent.setup();
      render(<MoveToS3DialogWithTwoFiles />);
      expect(await screen.findByRole("heading", { name: /move to s3/i })).toBeVisible();

      await selectMyS3Bucket(user);
      await user.click(screen.getByRole("button", { name: /^move$/i }));

      expect(await screen.findByText(/successfully moved/i)).toBeVisible();
      const moveRequest = mockAxios.history.post.find(({ url }) => url === "/filestores/1/uploadFromGallery");
      expect(moveRequest).toBeDefined();
      const body = JSON.parse(moveRequest?.data as string) as {
        recordIds: unknown;
      };
      expect(body.recordIds).toEqual(expect.arrayContaining([123, 456]));
    });
  });

  describe("Transfer mode", () => {
    test("Should have no axe violations in transfer mode", async () => {
      const { baseElement } = render(<MoveToS3DialogInTransferMode />);
      expect(await screen.findByRole("heading", { name: /transfer to s3/i })).toBeVisible();
      await screen.findByRole("button", { name: /select a filestore/i });
      await expectAccessible(baseElement);
    });

    test("Transfer mode shows 'Transfer' button and 'retain on source bucket' checkbox", async () => {
      render(<MoveToS3DialogInTransferMode />);
      expect(await screen.findByRole("heading", { name: /transfer to s3/i })).toBeVisible();
      // Wait for the filestore listing to resolve before the checkbox renders.
      await screen.findByRole("button", { name: /select a filestore/i });
      expect(screen.getByRole("button", { name: /^transfer$/i })).toBeVisible();
      // MUI renders the native checkbox input visually hidden (an SVG stands in
      // for it), so under jsdom assert presence rather than `toBeVisible`.
      expect(
        screen.getByRole("checkbox", {
          name: /retain a copy on source bucket/i,
        }),
      ).toBeInTheDocument();
    });

    test("Transfer button is disabled until a destination filestore is selected", async () => {
      const user = userEvent.setup();
      render(<MoveToS3DialogInTransferMode />);
      expect(await screen.findByRole("heading", { name: /transfer to s3/i })).toBeVisible();

      // Not HTML-disabled; submitting without a filestore shows a validation
      // popover.
      await screen.findByRole("button", { name: /select a filestore/i });
      await user.click(screen.getByRole("button", { name: /^transfer$/i }));
      expect(await screen.findByText(/a destination filestore is required/i)).toBeVisible();

      // Dismiss the validation popover (the spec clicked its backdrop).
      await user.keyboard("{Escape}");
      await waitFor(() => {
        expect(screen.queryByText(/a destination filestore is required/i)).not.toBeInTheDocument();
      });

      await selectMyS3Bucket(user);
      expect(await screen.findByRole("button", { name: /^transfer$/i })).toBeVisible();
    });

    test("Clicking Transfer calls the transfer endpoint and shows a success alert", async () => {
      const user = userEvent.setup();
      render(<MoveToS3DialogInTransferMode />);
      expect(await screen.findByRole("heading", { name: /transfer to s3/i })).toBeVisible();

      await selectMyS3Bucket(user);
      await user.click(screen.getByRole("button", { name: /^transfer$/i }));

      expect(await screen.findByText(/successfully transferred/i)).toBeVisible();
      expect(mockAxios.history.post.some(({ url }) => url === "/filestores/2/transfer")).toBe(true);
    });

    test("Transfer sends correct body with deleteSource=true by default", async () => {
      const user = userEvent.setup();
      render(<MoveToS3DialogInTransferMode />);
      await screen.findByRole("heading", { name: /transfer to s3/i });

      await selectMyS3Bucket(user);
      await user.click(screen.getByRole("button", { name: /^transfer$/i }));

      await waitFor(() => {
        expect(mockAxios.history.post.some(({ url }) => url === "/filestores/2/transfer")).toBe(true);
      });
      const transferRequest = mockAxios.history.post.find(({ url }) => url === "/filestores/2/transfer");
      expect(JSON.parse(transferRequest?.data as string)).toMatchObject({
        sourcePath: "/data/file.jpg",
        destFilestoreId: 1,
        destPath: "file.jpg",
        deleteSource: true,
      });
    });

    test("Checking 'Retain a copy on source bucket' sends deleteSource=false", async () => {
      const user = userEvent.setup();
      render(<MoveToS3DialogInTransferMode />);
      await screen.findByRole("heading", { name: /transfer to s3/i });

      await selectMyS3Bucket(user);
      await user.click(
        screen.getByRole("checkbox", {
          name: /retain a copy on source bucket/i,
        }),
      );
      await user.click(screen.getByRole("button", { name: /^transfer$/i }));

      await waitFor(() => {
        expect(mockAxios.history.post.some(({ url }) => url === "/filestores/2/transfer")).toBe(true);
      });
      const transferRequest = mockAxios.history.post.find(({ url }) => url === "/filestores/2/transfer");
      expect(JSON.parse(transferRequest?.data as string)).toMatchObject({
        deleteSource: false,
      });
    });

    test("All source paths are sent to the transfer endpoint for multiple files", async () => {
      const user = userEvent.setup();
      render(<MoveToS3DialogInTransferModeWithTwoFiles />);
      await screen.findByRole("heading", { name: /transfer to s3/i });

      await selectMyS3Bucket(user);
      await user.click(screen.getByRole("button", { name: /^transfer$/i }));

      expect((await screen.findAllByText(/successfully transferred/i))[0]).toBeVisible();

      const transferRequests = mockAxios.history.post.filter(({ url }) => url === "/filestores/2/transfer");
      expect(transferRequests).toHaveLength(2);
      const capturedBodies = transferRequests.map(({ data }) => JSON.parse(data as string) as unknown);
      expect(capturedBodies).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            sourcePath: "/data/file1.jpg",
            destPath: "file1.jpg",
          }),
          expect.objectContaining({
            sourcePath: "/data/file2.jpg",
            destPath: "file2.jpg",
          }),
        ]),
      );
    });
  });
});

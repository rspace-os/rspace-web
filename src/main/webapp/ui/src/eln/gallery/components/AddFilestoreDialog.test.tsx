import { afterEach, beforeEach, describe, expect, test } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { expectAccessible, render } from "@/__tests__/customQueries";
import axios from "@/common/axios";
import { AddFilestoreDialogStory } from "./AddFilestoreDialog.story";

const mockAxios = new MockAdapter(axios);

const filesystem = {
  id: 1,
  name: "irods test",
  url: "irods-test.researchspace.com",
  clientType: "IRODS",
  authType: "PASSWORD",
  options: {},
  loggedAs: null,
};

const rootListing = {
  remotePath: "/",
  filesystemInfo: filesystem,
  loggedUser: "alice",
  showExtraDirs: false,
  showCurrentDir: false,
  content: [
    {
      name: "selenium",
      logicPath: "/tempZone/home/alice/selenium",
      fileSize: 0,
      nfsId: 10809,
      folder: true,
      modificationDate: "2024-07-02T08:16:38.000Z",
    },
    {
      name: "test",
      logicPath: "/tempZone/home/alice/test",
      fileSize: 0,
      nfsId: 10111,
      folder: true,
      modificationDate: "2024-12-16T01:32:36.000Z",
    },
    {
      name: "training_jpgs",
      logicPath: "/tempZone/home/alice/training_jpgs",
      fileSize: 0,
      nfsId: 10024,
      folder: true,
      modificationDate: "2024-05-17T06:30:18.000Z",
    },
    {
      name: "unit_test_DO_NOT_MANUAL_USE",
      logicPath: "/tempZone/home/alice/unit_test_DO_NOT_MANUAL_USE",
      fileSize: 0,
      nfsId: 10851,
      folder: true,
      modificationDate: "2024-12-16T01:18:08.000Z",
    },
  ],
};

const emptyListing = {
  remotePath: "/",
  filesystemInfo: filesystem,
  loggedUser: "alice",
  showExtraDirs: false,
  showCurrentDir: false,
  content: [],
};

const s3Filesystem = {
  id: 2,
  name: "s3 test",
  url: "s3.example.com",
  clientType: "S3",
  authType: "PASSWORD",
  options: {},
  loggedAs: null,
};

const s3RootListing = {
  remotePath: "/",
  filesystemInfo: s3Filesystem,
  loggedUser: "alice",
  showExtraDirs: false,
  showCurrentDir: false,
  content: [],
};

describe("AddFilestoreDialog", () => {
  beforeEach(() => {
    mockAxios.reset();
    mockAxios.onGet("/api/v1/gallery/filesystems").reply(200, [filesystem, s3Filesystem]);
    mockAxios.onGet("/api/v1/gallery/filesystems/1/browse?remotePath=%2F").reply(200, rootListing);
    mockAxios.onGet("/api/v1/gallery/filesystems/1/browse?remotePath=%2Ftest%2F").reply(200, emptyListing);
    mockAxios.onGet("/api/v1/gallery/filesystems/2/browse?remotePath=%2F").reply(200, s3RootListing);
  });

  afterEach(() => {
    cleanup();
  });

  test("Should have no axe violations", async () => {
    const user = userEvent.setup();
    const { baseElement } = render(<AddFilestoreDialogStory />);

    // the filesystem radio is rendered once the filesystems request resolves
    await screen.findByRole("radio", { name: "irods test" });

    // axe check on the freshly mounted dialog
    await expectAccessible(baseElement);

    // the user selects a filesystem
    await user.click(screen.getByRole("radio", { name: "irods test" }));
    await user.click(screen.getByRole("button", { name: "gallery:addFilestoreDialog.chooseFilesystem" }));

    // the folder tree is rendered once the browse request resolves
    await screen.findByRole("treeitem", { name: "test" });

    // axe check on the folder-selection step
    await expectAccessible(baseElement);

    // the user selects a folder. The clickable surface is the treeitem's
    // content div, mirroring the spec's `.locator("> div")`.
    const testTreeItem = screen.getByRole("treeitem", { name: "test" });
    const content = testTreeItem.querySelector<HTMLElement>(":scope > div") ?? testTreeItem;
    await user.click(content);
    await user.click(screen.getByRole("button", { name: "gallery:addFilestoreDialog.chooseFolder" }));

    // wait for the name step to become active
    await screen.findByRole("textbox", { name: "gallery:addFilestoreDialog.nameLabel" });

    // axe check on the name step
    await expectAccessible(baseElement);
  });

  test("Selecting a filesystem advances to the folder-selection step", async () => {
    const user = userEvent.setup();
    render(<AddFilestoreDialogStory />);

    await user.click(await screen.findByRole("radio", { name: "irods test" }));
    await user.click(screen.getByRole("button", { name: "gallery:addFilestoreDialog.chooseFilesystem" }));

    expect(await screen.findByRole("treeitem", { name: "test" })).toBeVisible();
    expect(screen.getByRole("button", { name: "gallery:addFilestoreDialog.chooseFolder" })).toBeInTheDocument();
  });

  test("Selecting a folder advances to the name step", async () => {
    const user = userEvent.setup();
    render(<AddFilestoreDialogStory />);

    await user.click(await screen.findByRole("radio", { name: "irods test" }));
    await user.click(screen.getByRole("button", { name: "gallery:addFilestoreDialog.chooseFilesystem" }));

    const testTreeItem = await screen.findByRole("treeitem", {
      name: "test",
    });
    const content = testTreeItem.querySelector<HTMLElement>(":scope > div") ?? testTreeItem;
    await user.click(content);
    await user.click(screen.getByRole("button", { name: "gallery:addFilestoreDialog.chooseFolder" }));

    expect(await screen.findByRole("textbox", { name: "gallery:addFilestoreDialog.nameLabel" })).toBeVisible();
  });

  test("S3 filesystem offers a bucket top-level option and creates a root-path filestore", async () => {
    const user = userEvent.setup();
    let postedPathToSave: string | undefined;
    mockAxios.onPost("/api/v1/gallery/filestores").reply((config) => {
      postedPathToSave = (config.params as { pathToSave?: string }).pathToSave;
      return [200, {}];
    });

    render(<AddFilestoreDialogStory />);

    await user.click(await screen.findByRole("radio", { name: "s3 test" }));
    await user.click(screen.getByRole("button", { name: "gallery:addFilestoreDialog.chooseFilesystem" }));

    // S3 offers a selectable "(bucket top level)" entry (no subfolder needed); clicking its label
    // selects it (the click bubbles to the tree-item content handler).
    await user.click(await screen.findByText("gallery:addFilestoreDialog.bucketTopLevel"));
    await user.click(screen.getByRole("button", { name: "gallery:addFilestoreDialog.chooseFolder" }));

    const nameField = await screen.findByRole("textbox", { name: "gallery:addFilestoreDialog.nameLabel" });
    await user.type(nameField, "Root Filestore");
    await user.click(screen.getByRole("button", { name: "gallery:addFilestoreDialog.addFilestore" }));

    // the filestore is created with an empty path (the bucket top level)
    await waitFor(() => {
      expect(postedPathToSave).toBe("");
    });
  });

  test("Non-S3 filesystem does not offer the bucket top-level option", async () => {
    const user = userEvent.setup();
    render(<AddFilestoreDialogStory />);

    await user.click(await screen.findByRole("radio", { name: "irods test" }));
    await user.click(screen.getByRole("button", { name: "gallery:addFilestoreDialog.chooseFilesystem" }));

    await screen.findByRole("treeitem", { name: "test" });
    expect(screen.queryByText("gallery:addFilestoreDialog.bucketTopLevel")).not.toBeInTheDocument();
  });
});

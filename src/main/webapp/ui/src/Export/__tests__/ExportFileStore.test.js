/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import {
  render,
  cleanup,
  screen,
  act,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import ExportFileStore from "../ExportFileStore";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import CREATE_QUICK_EXPORT_PLAN from "./createQuickExportPlan";
import { mkValidator } from "../../util/Validator";
import userEvent from "@testing-library/user-event";

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("ExportFileStore", () => {
  test("Exporting document without any filestore links should show a warning.", async () => {
    mockAxios.onPost("/nfsExport/ajax/createQuickExportPlan").reply(200, {
      ...CREATE_QUICK_EXPORT_PLAN,
      foundFileSystems: [],
    });

    await act(
      () =>
        void render(
          <ExportFileStore
            nfsConfig={{
              maxFileSizeInMB: 1,
              excludedFileExtensions: "",
              includeNfsFiles: false,
            }}
            exportSelection={{
              type: "selection",
              exportTypes: [],
              exportNames: [],
              exportIds: [],
            }}
            exportConfig={{
              archiveType: "pdf",
              repository: false,
              fileStores: true,
              allVersions: false,
              repoData: [],
            }}
            updateFilters={() => {}}
            validator={mkValidator()}
          />
        )
    );

    expect(
      screen.getByText("No filestore links found in exported content.")
    ).toBeVisible();
  });
  test("Found filestore links dialog should show linked file.", async () => {
    const user = userEvent.setup();
    mockAxios
      .onPost("/nfsExport/ajax/createQuickExportPlan")
      .reply(200, CREATE_QUICK_EXPORT_PLAN);

    await act(
      () =>
        void render(
          <ExportFileStore
            nfsConfig={{
              maxFileSizeInMB: 1,
              excludedFileExtensions: "",
              includeNfsFiles: false,
            }}
            exportSelection={{
              type: "selection",
              exportTypes: [],
              exportNames: [],
              exportIds: [],
            }}
            exportConfig={{
              archiveType: "pdf",
              repository: false,
              fileStores: true,
              allVersions: false,
              repoData: [],
            }}
            updateFilters={() => {}}
            validator={mkValidator()}
          />
        )
    );

    void (await waitFor(async () => {
      expect(
        await screen.findByRole("button", {
          name: "Show found filestore links",
        })
      ).toBeVisible();
    }));

    await user.click(
      screen.getByRole("button", { name: "Show found filestore links" })
    );

    expect(
      within(within(screen.getByRole("dialog")).getByRole("table")).getByRole(
        "rowheader",
        { name: "/test.txt" }
      )
    ).toBeVisible();

    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: /OK/i })
    );
  });
  test("Filesystem login details dialog should show such info.", async () => {
    const user = userEvent.setup();
    mockAxios.onPost("/nfsExport/ajax/createQuickExportPlan").reply(200, {
      ...CREATE_QUICK_EXPORT_PLAN,
      foundFileSystems: [
        {
          ...CREATE_QUICK_EXPORT_PLAN.foundFileSystems[0],
          loggedAs: "sambatest",
        },
      ],
    });

    await act(
      () =>
        void render(
          <ExportFileStore
            nfsConfig={{
              maxFileSizeInMB: 1,
              excludedFileExtensions: "",
              includeNfsFiles: false,
            }}
            exportSelection={{
              type: "selection",
              exportTypes: [],
              exportNames: [],
              exportIds: [],
            }}
            exportConfig={{
              archiveType: "pdf",
              repository: false,
              fileStores: true,
              allVersions: false,
              repoData: [],
            }}
            updateFilters={() => {}}
            validator={mkValidator()}
          />
        )
    );

    void (await waitFor(async () => {
      expect(
        await screen.findByRole("button", {
          name: /Check file systems login details/i,
        })
      ).toBeVisible();
    }));

    await user.click(
      screen.getByRole("button", { name: /Check file systems login details/i })
    );

    expect(
      within(within(screen.getByRole("dialog")).getByRole("table")).getByRole(
        "rowheader",
        { name: "samba-folder" }
      )
    ).toBeVisible();
    expect(
      within(within(screen.getByRole("dialog")).getByRole("table")).getByRole(
        "cell",
        { name: "sambatest" }
      )
    ).toBeVisible();

    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: /OK/i })
    );
  });
});

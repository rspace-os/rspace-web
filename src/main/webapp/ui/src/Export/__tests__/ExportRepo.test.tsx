import { test, describe, expect, beforeEach, vi } from 'vitest';
import ExportRepo from "../ExportRepo";
import React from "react";
import {
  render,
  cleanup,
  screen,
  within,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import repoList from "./repoList.json";
import repoConfig from "./repoConfig.json";
import funders from "./funders.json";
import zenodoRepoList from "./zenodoRepoList.json";
import fc, { type Arbitrary } from "fast-check";
import { mkValidator } from "../../util/Validator";
import "../../../__mocks__/matchMedia";
import { type Tag } from "../repositories/Tags";

type DMP = {
  dmpUserInternalId: number;
  dmpTitle: string;
  dmpId: string;
};

window.fetch = vi.fn(() =>
  Promise.resolve({
    status: 200,
    ok: true,
    json: () => Promise.resolve(funders),
  } as Response),
);

beforeEach(() => {
  vi.clearAllMocks();
});


const props = {
  repoList: repoList.map((repo) => ({
    ...repo,
    repoCfg: -1,
  })),
  repoDetails: repoConfig,
  validator: mkValidator(),
  updateRepoConfig: () => {},
  fetchTags: vi.fn().mockResolvedValue([] as Array<Tag>),
};

describe("ExportRepo", () => {
  test("Should display selection message", async () => {
    render(<ExportRepo {...props} />);
    await waitFor(() => {
      expect(props.fetchTags).toHaveBeenCalled();
    });
    expect(
      screen.getByText(
        "Please choose one of your configured repositories to submit your export to:",
      ),
    ).toBeInTheDocument();
  });
  test("Should display repository list with two repositories", async () => {
    render(<ExportRepo {...props} />);
    await waitFor(() => {
      expect(props.fetchTags).toHaveBeenCalled();
    });
    expect(screen.getByText("Repository 1")).toBeInTheDocument();
    expect(screen.getByText("Repository 2")).toBeInTheDocument();
  });

  test("Should have first repository in list selected", async () => {
    render(<ExportRepo {...props} />);
    await waitFor(() => {
      expect(props.fetchTags).toHaveBeenCalled();
    });
    const repo1RadioButton = screen.getByTestId<HTMLInputElement>(
      "radio-button-app.repo1",
    );
    expect(repo1RadioButton).toBeChecked();
  });

  const arbListOfDMPs = (n: number): Arbitrary<Array<DMP>> =>
    fc.array(
      fc.record<DMP>({
        dmpUserInternalId: fc.nat(),
        dmpTitle: fc.string(),
        dmpId: fc.string(),
      }),
      { maxLength: n, minLength: n },
    );

  const arbSetOfIndexes = (min: number, max: number): Arbitrary<Set<number>> =>
    fc
      .uniqueArray(fc.nat(max - 1), { minLength: min, maxLength: max })
      .map((arr) => new Set(arr));

  test(
    "If chosen repository is Zenodo and more than 1 DMP is selected then a warning should be shown.",
    async () => {
      await fc.assert(
        fc
          .asyncProperty(
            // limit of 10 DMPs is set to cap memory usage
            fc
              .integer({ min: 2, max: 10 })
              .chain((n) =>
                fc.tuple<[Set<number>, Array<DMP>]>(
                  arbSetOfIndexes(2, n),
                  arbListOfDMPs(n),
                ),
              )
              .filter(([, linkedDMPs]) => {
                const ids = new Set(
                  linkedDMPs.map((dmp) => dmp.dmpUserInternalId),
                );
                return ids.size === linkedDMPs.length;
              }),
            async ([indexes, linkedDMPs]) => {
              const generatedRepoList = [
                { ...zenodoRepoList[0], linkedDMPs, repoCfg: -1 },
              ];
              render(
                <ExportRepo
                  repoDetails={props.repoDetails}
                  repoList={generatedRepoList}
                  updateRepoConfig={() => {}}
                  validator={mkValidator()}
                  fetchTags={props.fetchTags}
                />,
              );
              await waitFor(() => {
                expect(props.fetchTags).toHaveBeenCalled();
              });
              const repoChoice = await screen.findByRole("radiogroup", {
                name: "Repository choice",
              });

              expect(
                await within(repoChoice).findByRole("radio", {
                  name: "Zenodo",
                }),
              ).toBeChecked();

              fireEvent.click(
                await screen.findByRole("checkbox", {
                  name: "Associate export with a Data Management Plans (DMPs)",
                }),
              );

              const dmpTable = await screen.findByRole("table");

              (
                await within(dmpTable).findAllByRole("checkbox", {
                  name: "Plan selection",
                })
              ).forEach((c, i) => {
                if (indexes.has(i)) fireEvent.click(c);
              });

              expect(await screen.findByRole("alert")).toHaveTextContent(
                "Only one DMP can be associated with an export to Zenodo.",
              );
            },
          )
          .beforeEach(() => {
            cleanup();
            props.fetchTags.mockClear();
          }),

        { numRuns: 1 }
      );
    },
    30 * 1000,
  );
});


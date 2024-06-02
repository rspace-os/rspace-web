/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import ExportRepo from "../ExportRepo";
import React from "react";
import {
  render,
  cleanup,
  screen,
  within,
  fireEvent,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import repoList from "./repoList.json";
import repoConfig from "./repoConfig.json";
import funders from "./funders.json";
import zenodoRepoList from "./zenodoRepoList";
import fc, { type Arbitrary } from "fast-check";
import { mkValidator } from "../../util/Validator";
import "../../../__mocks__/matchMedia.js";
import { type Tag } from "../repositories/Tags";

type DMP = {|
  dmpUserInternalId: number,
  dmpTitle: string,
  dmpId: string,
|};

window.fetch = jest.fn(() =>
  Promise.resolve({
    status: 200,
    ok: true,
    json: () => Promise.resolve(funders),
  })
);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const props = {
  repoList,
  repoDetails: repoConfig,
  validator: mkValidator(),
  updateRepoConfig: () => {},
  fetchTags: () => Promise.resolve(([]: Array<Tag>)),
};

describe("ExportRepo", () => {
  test("Should display selection message", () => {
    render(<ExportRepo {...props} />);
    expect(
      screen.getByText(
        "Please choose one of your configured repositories to submit your export to:"
      )
    ).toBeInTheDocument();
  });
  test("Should display repository list with two repositories", () => {
    render(<ExportRepo {...props} />);
    expect(screen.getByText("Repository 1")).toBeInTheDocument();
    expect(screen.getByText("Repository 2")).toBeInTheDocument();
  });

  test("Should have first repository in list selected", () => {
    render(<ExportRepo {...props} />);
    const repo1RadioButton = screen.getByTestId("radio-button-app.repo1");
    expect(repo1RadioButton.checked).toEqual(true);
  });

  const arbListOfDMPs = (n: number): Arbitrary<Array<DMP>> =>
    fc.array(
      fc.record<DMP>({
        dmpUserInternalId: fc.nat(),
        dmpTitle: fc.string(),
        dmpId: fc.string(),
      }),
      { maxLength: n, minLength: n }
    );

  const arbSetOfIndexes = (min: number, max: number): Arbitrary<Set<number>> =>
    fc
      .uniqueArray(fc.nat(max - 1), { minLength: min, maxLength: max })
      .map((arr) => new Set(arr));

  test("If chosen repository is Zenodo and more than 1 DMP is selected then a warning should be shown.", async () => {
    await fc.assert(
      fc
        .asyncProperty(
          // limit of 10 DMPs is set to cap memory usage
          fc
            .integer({ min: 2, max: 10 })
            .chain((n) =>
              fc.tuple<Set<number>, Array<DMP>>(
                arbSetOfIndexes(2, n),
                arbListOfDMPs(n)
              )
            ),
          async ([indexes, linkedDMPs]) => {
            const generatedRepoList = [{ ...zenodoRepoList[0], linkedDMPs }];
            render(
              <ExportRepo
                repoDetails={props.repoDetails}
                repoList={generatedRepoList}
                updateRepoConfig={() => {}}
                validator={mkValidator()}
                fetchTags={() => Promise.resolve([])}
              />
            );
            const repoChoice = await screen.findByRole("radiogroup", {
              name: "Repository choice",
            });

            expect(
              await within(repoChoice).findByRole("radio", { name: "Zenodo" })
            ).toBeChecked();

            fireEvent.click(
              await screen.findByRole("checkbox", {
                name: "Associate export with a Data Management Plans (DMPs)",
              })
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
              "Only one DMP can be associated with an export to Zenodo."
            );
          }
        )
        .beforeEach(() => {
          cleanup();
        }),

      { numRuns: 1 }
    );
  });
});

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import fc, { type Arbitrary } from "fast-check";
import { HttpResponse, http } from "msw/http";
import { describe, expect, test, vi } from "vitest";
import { mkValidator } from "../../util/Validator";
import ExportRepo from "../ExportRepo";
import funders from "./funders.json";
import repoConfig from "./repoConfig.json";
import repoList from "./repoList.json";
import zenodoRepoList from "./zenodoRepoList.json";
import "@/__tests__/__mocks__/matchMedia";
import { server } from "@/__tests__/mswServer";
import { DEFAULT_STATE } from "@/Export/constants";
import { DEFAULT_REPO_CONFIG, type RepoDetails } from "../repositories/common";
import type { Tag } from "../repositories/Tags";

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
const props = {
  repoList: repoList.map((repo) => ({
    ...repo,
    repoCfg: -1,
  })),
  state: DEFAULT_STATE,
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
    expect(screen.getByText("workspace:export.repositories.choiceLabel")).toBeInTheDocument();
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
    const repo1RadioButton = screen.getByTestId<HTMLInputElement>("radio-button-app.repo1");
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
              .chain((n) => fc.tuple<[Set<number>, Array<DMP>]>(arbSetOfIndexes(2, n), arbListOfDMPs(n)))
              .filter(([, linkedDMPs]) => {
                const ids = new Set(linkedDMPs.map((dmp) => dmp.dmpUserInternalId));
                return ids.size === linkedDMPs.length;
              }),
            async ([indexes, linkedDMPs]) => {
              const user = userEvent.setup();
              const generatedRepoList = [{ ...zenodoRepoList[0], linkedDMPs, repoCfg: -1 }];
              render(
                <ExportRepo
                  repoDetails={props.repoDetails}
                  repoList={generatedRepoList}
                  state={DEFAULT_STATE}
                  updateRepoConfig={() => {}}
                  validator={mkValidator()}
                  fetchTags={props.fetchTags}
                />,
              );
              await waitFor(() => {
                expect(props.fetchTags).toHaveBeenCalled();
              });
              const repoChoice = await screen.findByRole("radiogroup", {
                name: "workspace:export.repositories.labels.choice",
              });
              expect(
                await within(repoChoice).findByRole("radio", {
                  name: "Zenodo",
                }),
              ).toBeChecked();
              await user.click(
                await screen.findByRole("checkbox", {
                  name: "workspace:export.repositories.dmp.associateLabel",
                }),
              );

              const dmpTable = await screen.findByRole("table");
              const checkboxes = await within(dmpTable).findAllByRole("checkbox", {
                name: "apps:dmpIntegrations.dialog.selectPlanLabel",
              });
              for (const [index, checkbox] of checkboxes.entries()) {
                if (indexes.has(index)) await user.click(checkbox);
              }
              expect(await screen.findByRole("alert")).toHaveTextContent(
                "workspace:export.repositories.dmp.zenodoLimit",
              );
            },
          )
          .beforeEach(() => {
            cleanup();
            props.fetchTags.mockClear();
          }),
        { numRuns: 1 },
      );
    },
    30 * 1000,
  );
  test("Zenodo's placeholder people and subject do not leak into Dataverse, also after Back and Next.", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/directory/ajax/subject", () =>
        HttpResponse.json({ fullName: "Test User", email: "test@example.com" }),
      ),
    );
    const zenodoThenDataverse = [
      { ...zenodoRepoList[0], repoCfg: -1 },
      { ...repoList[0], repoName: "app.dataverse", displayName: "Dataverse", repoCfg: -1 },
    ];
    const updateRepoConfig = vi.fn<(config: RepoDetails) => void>();
    const validator = mkValidator();
    const renderRepo = (repoDetails: RepoDetails) =>
      render(
        <ExportRepo
          state={DEFAULT_STATE}
          repoList={zenodoThenDataverse}
          repoDetails={repoDetails}
          validator={validator}
          updateRepoConfig={updateRepoConfig}
          fetchTags={vi.fn().mockResolvedValue([])}
        />,
      );
    const switchFromZenodoToDataverse = async () => {
      await user.click(screen.getByRole("radio", { name: "Zenodo" }));
      // Title and description are valid, so only Dataverse's missing subject can fail validation.
      await user.type(screen.getByRole("textbox", { name: "workspace:export.repositories.common.title" }), "Title");
      await user.type(
        screen.getByRole("textbox", { name: "workspace:export.repositories.common.description" }),
        "Description",
      );
      await user.click(screen.getByRole("radio", { name: "Dataverse" }));
      // The current user is the default author and contact.
      expect(await screen.findAllByText(/^Test User - /)).toHaveLength(2);
      expect(screen.queryByText(/DUMMY_VALUE/)).not.toBeInTheDocument();
      const config = updateRepoConfig.mock.lastCall?.[0];
      expect(JSON.stringify(config)).not.toContain("DUMMY_VALUE");
      await expect(validator.isValid()).resolves.toBe(false);
      return config;
    };

    const { unmount } = renderRepo({ ...DEFAULT_REPO_CONFIG, repoChoice: 0 });
    const config = await switchFromZenodoToDataverse();

    // Back then Next re-seeds ExportRepo from the config it last reported.
    unmount();
    if (!config) throw new Error("ExportRepo did not report its config");
    renderRepo(config);
    await switchFromZenodoToDataverse();
  });
});

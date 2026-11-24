import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import fc, { type Arbitrary } from "fast-check";
import { HttpResponse, http } from "msw/http";
import { describe, expect, type Mock, test, vi } from "vitest";
import { mkValidator } from "../../util/Validator";
import ExportRepo from "../ExportRepo";
import funders from "./funders.json";
import repoConfig from "./repoConfig.json";
import repoList from "./repoList.json";
import zenodoRepoList from "./zenodoRepoList.json";
import "@/__tests__/__mocks__/matchMedia";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { DEFAULT_STATE } from "@/Export/constants";
import appsEn from "@/modules/common/i18n/locales/en-US/apps.json";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import workspaceEn from "@/modules/common/i18n/locales/en-US/workspace.json";
import { DEFAULT_REPO_CONFIG, type Plan, type RepoDetails } from "../repositories/common";
import type { Tag } from "../repositories/Tags";

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
  const arbListOfDMPs = (n: number): Arbitrary<Array<Plan>> =>
    fc.array(
      fc.record<Plan>({
        dmpUserInternalId: fc.nat(),
        dmpTitle: fc.string(),
        dmpId: fc.string(),
        platformName: fc.string(),
        writeBackStatus: fc.constantFrom("OK", "NOT_CONNECTED"),
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
              .chain((n) => fc.tuple<[Set<number>, Array<Plan>]>(arbSetOfIndexes(2, n), arbListOfDMPs(n)))
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

  describe("Plans sent with the export", () => {
    const plans: Array<Plan> = [
      {
        dmpUserInternalId: 1,
        dmpTitle: "Soil survey plan",
        dmpId: "plan-1",
        platformName: "DMPTool",
        writeBackStatus: "OK",
      },
      {
        dmpUserInternalId: 2,
        dmpTitle: "River survey plan",
        dmpId: "plan-2",
        platformName: "DMPTool",
        writeBackStatus: "OK",
      },
    ];
    const renderWithPlans = async (selectedDMPs: Array<number> = []) => {
      const user = userEvent.setup();
      const updateRepoConfig = vi.fn<(repoDetails: RepoDetails) => void>();
      const validator = mkValidator();
      await renderWithRealI18n(
        <ExportRepo
          repoDetails={{
            ...props.repoDetails,
            selectedDMPs,
            meta: {
              ...props.repoDetails.meta,
              title: "Survey data",
              description: "Survey data",
              subject: "Mathematics",
            },
          }}
          repoList={[{ ...zenodoRepoList[0], linkedDMPs: plans, repoCfg: -1 }]}
          state={DEFAULT_STATE}
          updateRepoConfig={updateRepoConfig}
          validator={validator}
          fetchTags={props.fetchTags}
        />,
        { resources: { workspace: workspaceEn, apps: appsEn, common: commonEn }, defaultNS: "workspace" },
      );
      const associateSwitch = await screen.findByRole("checkbox", {
        name: "Associate export with a Data Management Plans (DMPs)",
      });
      return { user, updateRepoConfig, associateSwitch, validator };
    };
    const tick = (user: ReturnType<typeof userEvent.setup>, title: string) =>
      user.click(screen.getByRole("checkbox", { name: `Select plan: ${title}` }));
    const lastSentPlans = (updateRepoConfig: Mock<(repoDetails: RepoDetails) => void>) =>
      updateRepoConfig.mock.lastCall?.[0].selectedDMPs;

    test("sends a plan ticked as the last action", async () => {
      const { user, updateRepoConfig, associateSwitch } = await renderWithPlans();
      await user.click(associateSwitch);
      await user.click(screen.getByRole("checkbox", { name: "Select plan: River survey plan" }));
      expect(lastSentPlans(updateRepoConfig)).toEqual([2]);
    });

    test("sends no plans after the association switch is turned off", async () => {
      const { user, updateRepoConfig, associateSwitch } = await renderWithPlans();
      await user.click(associateSwitch);
      await user.click(screen.getByRole("checkbox", { name: "Select plan: River survey plan" }));
      expect(lastSentPlans(updateRepoConfig)).toEqual([2]);
      await user.click(associateSwitch);
      expect(lastSentPlans(updateRepoConfig)).toEqual([]);
    });

    test("blocks a Zenodo export with two plans ticked", async () => {
      const { user, associateSwitch, validator } = await renderWithPlans();
      await user.click(associateSwitch);
      await tick(user, "Soil survey plan");
      await tick(user, "River survey plan");
      expect(await validator.isValid()).toBe(false);
    });

    test("lets a Zenodo export continue with two plans ticked once the association switch is off", async () => {
      const { user, updateRepoConfig, associateSwitch, validator } = await renderWithPlans();
      await user.click(associateSwitch);
      await tick(user, "Soil survey plan");
      await tick(user, "River survey plan");
      await user.click(associateSwitch);
      expect(await validator.isValid()).toBe(true);
      expect(lastSentPlans(updateRepoConfig)).toEqual([]);
    });

    test("restores the switch and ticked plans from the saved request when reopened", async () => {
      const { associateSwitch } = await renderWithPlans([2]);
      expect(associateSwitch).toBeChecked();
      expect(screen.getByRole("checkbox", { name: "Select plan: River survey plan" })).toBeChecked();
      expect(screen.getByRole("checkbox", { name: "Select plan: Soil survey plan" })).not.toBeChecked();
    });
  });

  describe("Digital Commons Data", () => {
    const renderDigitalCommonsData = async (linkedDMPs: Array<Plan>) => {
      const user = userEvent.setup();
      const repo = {
        ...zenodoRepoList[0],
        repoName: "app.digitalCommonsData",
        displayName: "Digital Commons Data",
        linkedDMPs,
        repoCfg: -1,
      };
      const { baseElement } = await renderWithRealI18n(
        <ExportRepo
          repoDetails={props.repoDetails}
          repoList={[repo]}
          state={DEFAULT_STATE}
          updateRepoConfig={() => {}}
          validator={mkValidator()}
          fetchTags={props.fetchTags}
        />,
        { resources: { workspace: workspaceEn, apps: appsEn, common: commonEn }, defaultNS: "workspace" },
      );
      await user.click(
        await screen.findByRole("checkbox", {
          name: "Associate export with a Data Management Plans (DMPs)",
        }),
      );
      return { user, baseElement, table: await screen.findByRole("table") };
    };

    test("lists a plan on a connected platform without a warning", async () => {
      const { table } = await renderDigitalCommonsData([
        {
          dmpUserInternalId: 1,
          dmpTitle: "Soil survey plan",
          dmpId: "plan-1",
          platformName: "DMPTool",
          writeBackStatus: "OK",
        },
      ]);
      expect(
        within(table).getByRole("checkbox", { name: "Select plan: Soil survey plan" }),
      ).not.toHaveAccessibleDescription();
      expect(within(table).queryByText(/is not connected/)).not.toBeInTheDocument();
    });

    test("warns that a plan on a platform the user has not connected will not get the DOI, and still lets it be ticked", async () => {
      const { user, table } = await renderDigitalCommonsData([
        {
          dmpUserInternalId: 1,
          dmpTitle: "Soil survey plan",
          dmpId: "plan-1",
          platformName: "DMPonline",
          writeBackStatus: "NOT_CONNECTED",
        },
      ]);
      const checkbox = within(table).getByRole("checkbox", { name: "Select plan: Soil survey plan" });
      expect(checkbox).toHaveAccessibleDescription(
        "DMPonline is not connected, so this plan will not be updated with the dataset's DOI.",
      );
      expect(checkbox).toBeEnabled();
      await user.click(checkbox);
      expect(checkbox).toBeChecked();
    });

    test("has no accessibility violations when a plan's platform is not connected", async () => {
      const { baseElement } = await renderDigitalCommonsData([
        {
          dmpUserInternalId: 1,
          dmpTitle: "Soil survey plan",
          dmpId: "plan-1",
          platformName: "DMPonline",
          writeBackStatus: "NOT_CONNECTED",
        },
      ]);
      await expectAccessible(baseElement);
    });

    test("describes only the checkbox of the plan whose platform is not connected", async () => {
      const { table } = await renderDigitalCommonsData([
        {
          dmpUserInternalId: 1,
          dmpTitle: "Soil survey plan",
          dmpId: "plan-1",
          platformName: "DMPTool",
          writeBackStatus: "OK",
        },
        {
          dmpUserInternalId: 2,
          dmpTitle: "River survey plan",
          dmpId: "plan-2",
          platformName: "DMPonline",
          writeBackStatus: "NOT_CONNECTED",
        },
      ]);
      const connected = within(table).getByRole("checkbox", { name: "Select plan: Soil survey plan" });
      const notConnected = within(table).getByRole("checkbox", { name: "Select plan: River survey plan" });
      expect(connected).not.toHaveAttribute("aria-describedby");
      expect(notConnected).toHaveAccessibleDescription(
        "DMPonline is not connected, so this plan will not be updated with the dataset's DOI.",
      );
    });
  });
});

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
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import { DEFAULT_STATE } from "@/Export/constants";
import type { Plan } from "@/eln-dmp-integration/linkedPlan";
import appsEn from "@/modules/common/i18n/locales/en-US/apps.json";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import workspaceEn from "@/modules/common/i18n/locales/en-US/workspace.json";
import { DEFAULT_REPO_CONFIG, type RepoDetails } from "../repositories/common";
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
      { ...zenodoRepoList[0], linkedDMPs: zenodoRepoList[0].linkedDMPs as Array<Plan>, repoCfg: -1 },
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

    test("sends the earlier ticks again after the association switch is turned off and on", async () => {
      const { user, updateRepoConfig, associateSwitch } = await renderWithPlans();
      await user.click(associateSwitch);
      await tick(user, "River survey plan");
      await user.click(associateSwitch);
      await user.click(associateSwitch);
      expect(screen.getByRole("checkbox", { name: "Select plan: River survey plan" })).toBeChecked();
      expect(lastSentPlans(updateRepoConfig)).toEqual([2]);
    });

    test("sends only the plan still ticked after another is unticked", async () => {
      const { user, updateRepoConfig, associateSwitch } = await renderWithPlans();
      await user.click(associateSwitch);
      await tick(user, "Soil survey plan");
      await tick(user, "River survey plan");
      await tick(user, "Soil survey plan");
      expect(lastSentPlans(updateRepoConfig)).toEqual([2]);
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

    const dataverseWithTwoLicences = {
      ...repoList[0],
      repoName: "app.dataverse",
      displayName: "Dataverse",
      license: {
        ...repoList[0].license,
        licenses: [
          ...repoList[0].license.licenses,
          {
            licenseDefinition: { name: "CC-BY-4.0", url: "https://creativecommons.org/licenses/by/4.0/" },
            defaultLicense: null,
          },
        ],
      },
      linkedDMPs: plans,
      repoCfg: -1,
    };
    const renderDataverse = async (repoDetails: RepoDetails, fetchTags: () => Promise<Array<Tag>>) => {
      const user = userEvent.setup();
      const updateRepoConfig = vi.fn<(config: RepoDetails) => void>();
      await renderWithRealI18n(
        <ExportRepo
          repoDetails={repoDetails}
          repoList={[dataverseWithTwoLicences]}
          state={DEFAULT_STATE}
          updateRepoConfig={updateRepoConfig}
          validator={mkValidator()}
          fetchTags={fetchTags}
        />,
        { resources: { workspace: workspaceEn, apps: appsEn, common: commonEn }, defaultNS: "workspace" },
      );
      await user.click(
        await screen.findByRole("checkbox", { name: "Associate export with a Data Management Plans (DMPs)" }),
      );
      // the request also carries the record tags, which RepoDetails does not declare
      const sentMeta = () =>
        updateRepoConfig.mock.lastCall?.[0].meta as (RepoDetails["meta"] & { tags?: Array<Tag> }) | undefined;
      return { user, sentMeta };
    };

    test("shows and keeps the saved licence after going back, whether a plan or the title changes", async () => {
      const { user, sentMeta } = await renderDataverse(
        {
          ...props.repoDetails,
          repoChoice: 0,
          meta: {
            ...props.repoDetails.meta,
            title: "Survey data",
            description: "Survey data",
            licenseName: "CC-BY-4.0",
          },
        },
        props.fetchTags,
      );
      expect(screen.getByRole("combobox", { name: /License/ })).toHaveTextContent("CC-BY-4.0");

      await tick(user, "River survey plan");
      expect(sentMeta()?.licenseName).toBe("CC-BY-4.0");

      await user.type(screen.getByRole("textbox", { name: /Title/ }), "!");
      expect(sentMeta()?.licenseName).toBe("CC-BY-4.0");
    });

    test("sends record tags that finish loading after the metadata was entered", async () => {
      const tags: Array<Tag> = [{ value: "soil", vocabulary: "RSpace", uri: "", version: "" }];
      let loadTags: (loaded: Array<Tag>) => void = () => {};
      const { user, sentMeta } = await renderDataverse(
        props.repoDetails,
        () =>
          new Promise<Array<Tag>>((resolve) => {
            loadTags = resolve;
          }),
      );
      await user.type(screen.getByRole("textbox", { name: /Title/ }), "Survey data");
      loadTags(tags);
      await tick(user, "River survey plan");
      await waitFor(() => expect(sentMeta()?.tags).toEqual(tags));
    });

    test("saves record tags that finish loading after the form was saved, so going back keeps them", async () => {
      const tags: Array<Tag> = [{ value: "soil", vocabulary: "RSpace", uri: "", version: "" }];
      let loadTags: (loaded: Array<Tag>) => void = () => {};
      const { user, sentMeta } = await renderDataverse(
        props.repoDetails,
        () =>
          new Promise<Array<Tag>>((resolve) => {
            loadTags = resolve;
          }),
      );
      await user.type(screen.getByRole("textbox", { name: /Title/ }), "Survey data");
      expect(sentMeta()?.tags).toEqual([]);

      loadTags(tags);

      await waitFor(() => expect(sentMeta()?.tags).toEqual(tags));
    });

    test("switches to a repository with fewer licences after the saved licence was restored", async () => {
      const user = userEvent.setup();
      const updateRepoConfig = vi.fn<(config: RepoDetails) => void>();
      await renderWithRealI18n(
        <ExportRepo
          repoDetails={{
            ...props.repoDetails,
            repoChoice: 0,
            meta: {
              ...props.repoDetails.meta,
              title: "Survey data",
              description: "Survey data",
              licenseName: "CC-BY-4.0",
            },
          }}
          repoList={[dataverseWithTwoLicences, { ...zenodoRepoList[0], linkedDMPs: plans, repoCfg: -1 }]}
          state={DEFAULT_STATE}
          updateRepoConfig={updateRepoConfig}
          validator={mkValidator()}
          fetchTags={props.fetchTags}
        />,
        { resources: { workspace: workspaceEn, apps: appsEn, common: commonEn }, defaultNS: "workspace" },
      );
      await user.click(await screen.findByRole("radio", { name: "Zenodo" }));

      const sent = updateRepoConfig.mock.lastCall?.[0] as (RepoDetails & { appName?: string }) | undefined;
      expect(sent?.appName).toBe("app.zenodo");
      expect(sent?.meta.licenseName).toBe("CC-0");
    });

    test.each([
      ["edited", [{ value: "edited", vocabulary: "RSpace", uri: "", version: "" }]],
      ["cleared", []],
    ])("keeps %s tags from the saved request when a plan is ticked after going back", async (_, savedTags) => {
      const fetchTags = vi.fn().mockResolvedValue([{ value: "original", vocabulary: "RSpace", uri: "", version: "" }]);
      const { user, sentMeta } = await renderDataverse(
        {
          ...props.repoDetails,
          meta: { ...props.repoDetails.meta, title: "Survey data", description: "Survey data", tags: savedTags },
        } as RepoDetails,
        fetchTags,
      );
      await tick(user, "River survey plan");

      expect(sentMeta()?.tags).toEqual(savedTags);
    });

    test("restores the switch and ticked plans from the saved request when reopened", async () => {
      const { associateSwitch } = await renderWithPlans([2]);
      expect(associateSwitch).toBeChecked();
      expect(screen.getByRole("checkbox", { name: "Select plan: River survey plan" })).toBeChecked();
      expect(screen.getByRole("checkbox", { name: "Select plan: Soil survey plan" })).not.toBeChecked();
    });

    const renderThreeRepos = async () => {
      const user = userEvent.setup();
      const updateRepoConfig = vi.fn<(repoDetails: RepoDetails) => void>();
      const validator = mkValidator();
      const withPlans = (repoName: string, displayName: string) => ({
        ...repoList[0],
        repoName,
        displayName,
        linkedDMPs: plans,
        repoCfg: -1,
      });
      await renderWithRealI18n(
        <ExportRepo
          repoDetails={{
            ...props.repoDetails,
            repoChoice: 0,
            meta: {
              ...props.repoDetails.meta,
              title: "Survey data",
              description: "Survey data",
              subject: "Mathematics",
            },
          }}
          repoList={[
            withPlans("app.dryad", "Dryad"),
            withPlans("app.dataverse", "Dataverse"),
            { ...zenodoRepoList[0], linkedDMPs: plans, repoCfg: -1 },
          ]}
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

    test("still sends plans ticked on Dryad after switching to Dataverse", async () => {
      const { user, updateRepoConfig, associateSwitch } = await renderThreeRepos();
      await user.click(associateSwitch);
      await tick(user, "Soil survey plan");
      await tick(user, "River survey plan");
      await user.click(screen.getByRole("radio", { name: "Dataverse" }));
      expect(lastSentPlans(updateRepoConfig)).toEqual([1, 2]);
    });

    test("blocks the export and explains the limit after switching to Zenodo with two plans ticked", async () => {
      const { user, validator } = await renderThreeRepos();
      await user.click(screen.getByRole("radio", { name: "Dataverse" }));
      await user.click(screen.getByRole("checkbox", { name: "Associate export with a Data Management Plans (DMPs)" }));
      await tick(user, "Soil survey plan");
      await tick(user, "River survey plan");
      await user.click(screen.getByRole("radio", { name: "Zenodo" }));
      expect(await validator.isValid()).toBe(false);
      expect(screen.getByText("Only one DMP can be associated with an export to Zenodo.")).toBeVisible();
    });

    test("does not send a remembered plan that is no longer listed", async () => {
      const { user, updateRepoConfig } = await renderWithPlans([2, 99]);
      await tick(user, "Soil survey plan");
      expect(lastSentPlans(updateRepoConfig)).toEqual([2, 1]);
    });

    test("does not block a Zenodo export with one listed plan and one remembered plan no longer listed", async () => {
      const { validator } = await renderWithPlans([2, 99]);
      expect(await validator.isValid()).toBe(true);
      expect(screen.queryByText("Only one DMP can be associated with an export to Zenodo.")).not.toBeInTheDocument();
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
      const updateRepoConfig = vi.fn<(repoDetails: RepoDetails) => void>();
      const { baseElement } = await renderWithRealI18n(
        <ExportRepo
          repoDetails={props.repoDetails}
          repoList={[repo]}
          state={DEFAULT_STATE}
          updateRepoConfig={updateRepoConfig}
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
      return { user, baseElement, updateRepoConfig, table: await screen.findByRole("table") };
    };

    test("sends a ticked plan with the export", async () => {
      const { user, updateRepoConfig, table } = await renderDigitalCommonsData([
        {
          dmpUserInternalId: 1,
          dmpTitle: "Soil survey plan",
          dmpId: "plan-1",
          platformName: "DMPTool",
          writeBackStatus: "OK",
        },
      ]);
      await user.click(within(table).getByRole("checkbox", { name: "Select plan: Soil survey plan" }));
      expect(updateRepoConfig.mock.lastCall?.[0].selectedDMPs).toEqual([1]);
    });

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

    test("warns that a plan on a platform the user has not connected will not be updated, and still lets it be ticked", async () => {
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
      expect(checkbox).toHaveAccessibleDescription("DMPonline is not connected, so this plan will not be updated.");
      expect(checkbox).toBeEnabled();
      await user.click(checkbox);
      expect(checkbox).toBeChecked();
    });

    test.each([
      ["PLATFORM_OFF", "DMPTool", "DMPTool is switched off, so this plan will not be updated."],
      [
        "NOT_SUPPORTED",
        "DSW / FAIR Wizard",
        "RSpace cannot yet update plans on DSW / FAIR Wizard, so this plan will not be updated.",
      ],
      ["UNKNOWN_SOURCE", "", "RSpace does not know which service this plan came from, so it will not be updated."],
    ] as const)(
      "warns when a plan's status is %s, and still sends it when ticked",
      async (writeBackStatus, platformName, warning) => {
        const { user, updateRepoConfig, table } = await renderDigitalCommonsData([
          { dmpUserInternalId: 1, dmpTitle: "Soil survey plan", dmpId: "plan-1", platformName, writeBackStatus },
        ]);
        const checkbox = within(table).getByRole("checkbox", { name: "Select plan: Soil survey plan" });
        expect(checkbox).toHaveAccessibleDescription(warning);
        await user.click(checkbox);
        expect(updateRepoConfig.mock.lastCall?.[0].selectedDMPs).toEqual([1]);
      },
    );

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
      expect(notConnected).toHaveAccessibleDescription("DMPonline is not connected, so this plan will not be updated.");
    });
  });
});

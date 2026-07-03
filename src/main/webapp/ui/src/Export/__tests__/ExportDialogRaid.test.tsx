import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { I18nextProvider } from "react-i18next";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import workspaceEn from "@/modules/common/i18n/locales/en-US/workspace.json";
import type { GroupInfo } from "@/modules/groups/schema";
import type { useCommonGroupsShareListingQuery } from "@/modules/share/queries";
import materialTheme from "../../theme";
import { DEFAULT_STATE } from "../constants";
import ExportDialogRaid from "../ExportDialogRaid";

type CommonGroupsQueryArgs = Parameters<typeof useCommonGroupsShareListingQuery>;
type CommonGroupsQueryResult = ReturnType<typeof useCommonGroupsShareListingQuery>;

const mockedUseCommonGroupsShareListingQuery = vi.fn<(...args: CommonGroupsQueryArgs) => CommonGroupsQueryResult>();

const makeQueryResult = (overrides: Partial<CommonGroupsQueryResult> = {}): CommonGroupsQueryResult =>
  ({
    data: undefined,
    error: null,
    ...overrides,
  }) as CommonGroupsQueryResult;

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: () => ({ data: "test-token" }),
}));

vi.mock("@/modules/share/queries", () => ({
  useCommonGroupsShareListingQuery: (...args: CommonGroupsQueryArgs) => mockedUseCommonGroupsShareListingQuery(...args),
}));

const makeState = (overrides: Partial<typeof DEFAULT_STATE> = {}) => ({
  ...DEFAULT_STATE,
  exportSelection: {
    ...DEFAULT_STATE.exportSelection,
    exportIds: ["1", "2"],
  },
  repositoryConfig: {
    ...DEFAULT_STATE.repositoryConfig,
  },
  ...overrides,
});

const renderExportDialogRaid = (stateOverrides: Partial<typeof DEFAULT_STATE> = {}, updateRepoConfig = vi.fn()) =>
  render(
    <ThemeProvider theme={materialTheme}>
      <ExportDialogRaid state={makeState(stateOverrides)} updateRepoConfig={updateRepoConfig} />
    </ThemeProvider>,
  );

const renderExportDialogRaidWithRealI18n = async (
  stateOverrides: Partial<typeof DEFAULT_STATE> = {},
  updateRepoConfig = vi.fn(),
) => {
  const i18n = await createTestI18n({ workspace: workspaceEn }, "workspace");
  return render(
    <I18nextProvider i18n={i18n}>
      <ThemeProvider theme={materialTheme}>
        <ExportDialogRaid state={makeState(stateOverrides)} updateRepoConfig={updateRepoConfig} />
      </ThemeProvider>
    </I18nextProvider>,
  );
};

const makeGroup = (id: number, overrides: Partial<GroupInfo> = {}): GroupInfo => ({
  id,
  globalId: `GR${id}`,
  name: `Group ${id}`,
  type: "PROJECT_GROUP",
  sharedFolderId: 1000 + id,
  sharedSnippetFolderId: 2000 + id,
  members: [],
  raid: {
    raidServerAlias: "raid-server",
    raidIdentifier: `raid-${id}`,
    raidTitle: `Test RAiD ${id}`,
  },
  ...overrides,
});

const makeProjectGroup = (id: number): GroupInfo => makeGroup(id, { name: `Project Group ${id}` });

describe("ExportDialogRaid", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders a spinner when data has not loaded yet", () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(makeQueryResult({ data: undefined }));

    renderExportDialogRaid();

    const spinner = screen.getByRole("img", { hidden: true });
    expect(spinner).toHaveAttribute("data-icon", "spinner");
  });

  it("renders an error alert when the query errors", async () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({ data: new Map(), error: new Error("Boom") }),
    );

    await renderExportDialogRaidWithRealI18n();

    const alert = screen.getByRole("alert");
    expect(within(alert).getByText("Error")).toBeInTheDocument();
    expect(alert).toHaveTextContent("Boom");
    expect(alert).toHaveTextContent("press Next to continue without reporting to RAiD");
  });

  it("renders an ineligible message when groups are missing", async () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([[101, null]]),
      }),
    );

    await renderExportDialogRaidWithRealI18n();

    const alert = screen.getByRole("alert");
    expect(within(alert).getByText("Cannot report to RAiD")).toBeInTheDocument();
    expect(alert).toHaveTextContent("101");
  });

  it("renders an ineligible message when no project groups are available", () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([[2, makeGroup(2, { name: "Lab Group 2", type: "LAB_GROUP" })]]),
      }),
    );

    renderExportDialogRaid();

    expect(screen.getByText("workspace:export.raid.ineligible.title")).toBeInTheDocument();
    expect(screen.getByText("workspace:export.raid.ineligible.noProjectGroups")).toBeInTheDocument();
  });

  it("renders an ineligible message when no RAiD association is found", async () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([
          [7, makeGroup(7, { name: "Project Group 7", raid: null })],
          [8, makeGroup(8, { name: "Project Group 8", raid: null })],
        ]),
      }),
    );

    await renderExportDialogRaidWithRealI18n();

    const alert = screen.getByRole("alert");
    expect(within(alert).getByText("Cannot report to RAiD")).toBeInTheDocument();
    expect(alert).toHaveTextContent("Project Group 7");
    expect(alert).toHaveTextContent("Project Group 8");
  });

  it("renders an ineligible message when multiple RAiD associations are found", async () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([
          [3, makeProjectGroup(3)],
          [4, makeProjectGroup(4)],
        ]),
      }),
    );

    await renderExportDialogRaidWithRealI18n();

    const alert = screen.getByRole("alert");
    expect(within(alert).getByText("Cannot report to RAiD")).toBeInTheDocument();
    expect(alert).toHaveTextContent("Project Group 3");
    expect(alert).toHaveTextContent("Project Group 4");
  });

  it("renders the RAiD info and toggles exportToRaid when eligible", async () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([[5, makeProjectGroup(5)]]),
      }),
    );

    const updateRepoConfig = vi.fn();

    await renderExportDialogRaidWithRealI18n({}, updateRepoConfig);

    const alert = screen.getByRole("alert");
    expect(alert).toBeInTheDocument();
    expect(within(alert).getByText("Report to RAiD")).toBeInTheDocument();
    expect(alert).toHaveTextContent("Project Group 5");
    expect(alert).toHaveTextContent("Test RAiD 5");
    expect(alert).toHaveTextContent("raid-5");

    const user = userEvent.setup();
    const toggle = screen.getByRole("checkbox", { name: "Report to RAiD" });
    await user.click(toggle);

    expect(updateRepoConfig).toHaveBeenCalledWith(expect.objectContaining({ exportToRaid: true }));
  });
});

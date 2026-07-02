import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
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

  it("renders an error alert when the query errors", () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({ data: new Map(), error: new Error("Boom") }),
    );

    renderExportDialogRaid();

    expect(screen.getByText("workspace:export.raid.error.title")).toBeInTheDocument();
    expect(screen.getByText("workspace:export.raid.error.message")).toBeInTheDocument();
    expect(screen.getByText("workspace:export.raid.error.nextHint")).toBeInTheDocument();
  });

  it("renders an ineligible message when groups are missing", () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([[101, null]]),
      }),
    );

    renderExportDialogRaid();

    expect(screen.getByText("workspace:export.raid.ineligible.title")).toBeInTheDocument();
    expect(screen.getByText("workspace:export.raid.ineligible.missingGroups")).toBeInTheDocument();
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

  it("renders an ineligible message when no RAiD association is found", () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([
          [7, makeGroup(7, { name: "Project Group 7", raid: null })],
          [8, makeGroup(8, { name: "Project Group 8", raid: null })],
        ]),
      }),
    );

    renderExportDialogRaid();

    expect(screen.getByText("workspace:export.raid.ineligible.title")).toBeInTheDocument();
    expect(screen.getByText("workspace:export.raid.ineligible.noRaidAssociation")).toBeInTheDocument();
  });

  it("renders an ineligible message when multiple RAiD associations are found", () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([
          [3, makeProjectGroup(3)],
          [4, makeProjectGroup(4)],
        ]),
      }),
    );

    renderExportDialogRaid();

    expect(screen.getByText("workspace:export.raid.ineligible.title")).toBeInTheDocument();
    expect(screen.getByText("workspace:export.raid.ineligible.multipleRaids")).toBeInTheDocument();
  });

  it("renders the RAiD info and toggles exportToRaid when eligible", async () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([[5, makeProjectGroup(5)]]),
      }),
    );

    const updateRepoConfig = vi.fn();

    renderExportDialogRaid({}, updateRepoConfig);

    const alert = screen.getByRole("alert");
    expect(alert).toBeInTheDocument();
    expect(within(alert).getByText("workspace:export.raid.eligible.title")).toBeInTheDocument();
    expect(screen.getByText("workspace:export.raid.eligible.projectGroupLine")).toBeInTheDocument();
    expect(screen.getByText("workspace:export.raid.eligible.raidDetails")).toBeInTheDocument();

    const user = userEvent.setup();
    const toggle = screen.getByRole("checkbox", { name: /export.raid.eligible.reportLabel/i });
    await user.click(toggle);

    expect(updateRepoConfig).toHaveBeenCalledWith(expect.objectContaining({ exportToRaid: true }));
  });
});

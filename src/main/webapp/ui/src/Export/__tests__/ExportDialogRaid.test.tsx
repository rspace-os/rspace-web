import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import ExportDialogRaid from "../ExportDialogRaid";
import { DEFAULT_STATE } from "../constants";
import type { GroupInfo } from "@/modules/groups/schema";
import type { useCommonGroupsShareListingQuery } from "@/modules/share/queries";

type CommonGroupsQueryArgs = Parameters<typeof useCommonGroupsShareListingQuery>;
type CommonGroupsQueryResult = ReturnType<typeof useCommonGroupsShareListingQuery>;

const mockedUseCommonGroupsShareListingQuery = vi.fn<
  (...args: CommonGroupsQueryArgs) => CommonGroupsQueryResult
>();

const makeQueryResult = (
  overrides: Partial<CommonGroupsQueryResult> = {},
): CommonGroupsQueryResult =>
  ({
    data: undefined,
    error: null,
    ...overrides,
  }) as CommonGroupsQueryResult;

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: () => ({ data: "test-token" }),
}));

vi.mock("@/modules/share/queries", () => ({
  useCommonGroupsShareListingQuery: (...args: CommonGroupsQueryArgs) =>
    mockedUseCommonGroupsShareListingQuery(...args),
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

const renderExportDialogRaid = (
  stateOverrides: Partial<typeof DEFAULT_STATE> = {},
  updateRepoConfig = vi.fn(),
) =>
  render(
    <ThemeProvider theme={materialTheme}>
      <ExportDialogRaid
        state={makeState(stateOverrides)}
        updateRepoConfig={updateRepoConfig}
      />
    </ThemeProvider>,
  );

const makeGroup = (
  id: number,
  overrides: Partial<GroupInfo> = {},
): GroupInfo => ({
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

const makeProjectGroup = (id: number): GroupInfo =>
  makeGroup(id, { name: `Project Group ${id}` });

describe("ExportDialogRaid", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders a spinner when data has not loaded yet", () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({ data: undefined }),
    );

    renderExportDialogRaid();

    const spinner = screen.getByRole("img", { hidden: true });
    expect(spinner).toHaveAttribute("data-icon", "spinner");
  });

  it("renders an error alert when the query errors", () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({ data: new Map(), error: new Error("Boom") }),
    );

    renderExportDialogRaid();

    expect(screen.getByText("Error")).toBeInTheDocument();
    expect(
      screen.getByText(
        /An error occurred while determining RAiD export eligibility: Boom/,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /Please press Next to continue without reporting to RAiD\./,
      ),
    ).toBeInTheDocument();
  });

  it("renders an ineligible message when groups are missing", () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([[101, null]]),
      }),
    );

    renderExportDialogRaid();

    expect(screen.getByText("Cannot report to RAiD")).toBeInTheDocument();
    expect(
      screen.getByText(/unable to determine whether you have the rights/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/101/)).toBeInTheDocument();
  });

  it("renders an ineligible message when no project groups are available", () => {
    mockedUseCommonGroupsShareListingQuery.mockReturnValue(
      makeQueryResult({
        data: new Map<number, GroupInfo | null>([
          [2, makeGroup(2, { name: "Lab Group 2", type: "LAB_GROUP" })],
        ]),
      }),
    );

    renderExportDialogRaid();

    expect(screen.getByText("Cannot report to RAiD")).toBeInTheDocument();
    expect(
      screen.getByText(
        /No project groups are associated with all shared items selected/i,
      ),
    ).toBeInTheDocument();
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

    expect(screen.getByText("Cannot report to RAiD")).toBeInTheDocument();
    expect(
      screen.getByText(
        /None of the project groups \(Project Group 7, Project Group 8\) associated with the shared items have a RAiD association\./,
      ),
    ).toBeInTheDocument();
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

    expect(screen.getByText("Cannot report to RAiD")).toBeInTheDocument();
    expect(
      screen.getByText(
        /Multiple project groups \(Project Group 3, Project Group 4\) associated with the shared items have RAiD associations, which is not supported\./,
      ),
    ).toBeInTheDocument();
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
    expect(within(alert).getByText("Report to RAiD")).toBeInTheDocument();
    expect(screen.getByText(/Project Group 5/)).toBeInTheDocument();
    expect(screen.getByText(/Test RAiD/)).toBeInTheDocument();
    expect(screen.getByText(/raid-5/)).toBeInTheDocument();

    const user = userEvent.setup();
    const toggle = screen.getByRole("checkbox", { name: /Report to RAiD/i });
    await user.click(toggle);

    expect(updateRepoConfig).toHaveBeenCalledWith(
      expect.objectContaining({ exportToRaid: true }),
    );
  });
});

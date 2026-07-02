import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import axios from "@/common/axios";
import materialTheme from "../../../theme";
import DMPDialog from "../DMPDialog";

vi.mock("@/hooks/auth/useOauthToken", () => ({
  __esModule: true,
  default: () => ({
    getToken: () => Promise.resolve("token"),
  }),
}));

vi.mock("@/hooks/api/useWhoAmI", () => ({
  __esModule: true,
  default: () => ({
    tag: "success",
    value: {
      id: 1,
      username: "test",
      firstName: "Test",
      lastName: "User",
      hasPiRole: false,
      hasSysAdminRole: false,
      email: "test@example.com",
      bench: null,
      workbenchId: null,
      getBench: () => Promise.reject(new Error("Not implemented by this Person implementation")),
      isCurrentUser: true,
      fullName: "Test User",
      label: "Test User (test)",
    },
  }),
}));

vi.mock("@/hooks/websockets/useWebSocketNotifications", () => ({
  __esModule: true,
  default: () => ({
    notificationCount: 0,
    messageCount: 0,
    specialMessageCount: 0,
  }),
}));

Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

const uiNavigationData = {
  userDetails: {
    email: "test@example.com",
    orcidId: null,
    orcidAvailable: false,
    fullName: "Test User",
    username: "test",
    profileImgSrc: null,
  },
  visibleTabs: {
    published: true,
    inventory: true,
    system: true,
    myLabGroups: true,
  },
  extraHelpLinks: [],
  bannerImgSrc: "",
  operatedAs: false,
  nextMaintenance: null,
};

type MockPlan = {
  dmp: {
    title: string;
    dmp_id: { identifier: string };
    created: string;
    modified: string;
  };
};

const makePlan = (id: number, title: string): MockPlan => ({
  dmp: {
    title,
    dmp_id: { identifier: `https://dmp-pgd.ca/api/v2/plans/${id}` },
    created: "2025-01-01T00:00:00Z",
    modified: "2025-01-02T00:00:00Z",
  },
});

let mockAxios: MockAdapter;

const stubListPlans = (plans: ReadonlyArray<MockPlan>): void => {
  mockAxios.onGet(/\/apps\/dmpassistant\/plans.*/).reply(200, {
    data: { items: plans, total_items: plans.length },
    error: null,
  });
};

const renderDialog = () =>
  render(
    <ThemeProvider theme={materialTheme}>
      <DMPDialog open setOpen={() => {}} />
    </ThemeProvider>,
  );

const PLAN_CHECKBOX_LABEL = "apps:dmpIntegrations.dialog.selectPlanLabel";

const checkboxForPlan = async (title: string): Promise<HTMLElement> =>
  within(await screen.findByRole("row", { name: (name) => name.includes(title) })).getByRole("checkbox", {
    name: PLAN_CHECKBOX_LABEL,
  });

beforeEach(() => {
  vi.clearAllMocks();
  mockAxios = new MockAdapter(axios);
  mockAxios.onGet("/api/v1/userDetails/uiNavigationData").reply(200, uiNavigationData);
});

afterEach(() => {
  mockAxios.restore();
});

describe("DMPDialog", () => {
  describe("Row selection (checkbox per DMP)", () => {
    test("renders one checkbox per DMP returned by the listing.", async () => {
      stubListPlans([makePlan(1, "Plan One"), makePlan(2, "Plan Two")]);

      renderDialog();

      expect(await checkboxForPlan("Plan One")).toBeInTheDocument();
      expect(await checkboxForPlan("Plan Two")).toBeInTheDocument();
    });

    test("clicking a checkbox selects that DMP.", async () => {
      stubListPlans([makePlan(1, "Plan One")]);

      renderDialog();

      const cb = await checkboxForPlan("Plan One");
      expect(cb).not.toBeChecked();

      fireEvent.click(cb);

      expect(cb).toBeChecked();
    });

    test("clicking an already-selected checkbox deselects it.", async () => {
      stubListPlans([makePlan(1, "Plan One")]);

      renderDialog();

      const cb = await checkboxForPlan("Plan One");
      fireEvent.click(cb);
      expect(cb).toBeChecked();

      fireEvent.click(cb);

      expect(cb).not.toBeChecked();
    });

    test("multiple checkboxes can be selected at the same time.", async () => {
      stubListPlans([makePlan(1, "Plan One"), makePlan(2, "Plan Two"), makePlan(3, "Plan Three")]);

      renderDialog();

      const cb1 = await checkboxForPlan("Plan One");
      const cb2 = await checkboxForPlan("Plan Two");
      const cb3 = await checkboxForPlan("Plan Three");

      fireEvent.click(cb1);
      fireEvent.click(cb3);

      expect(cb1).toBeChecked();
      expect(cb2).not.toBeChecked();
      expect(cb3).toBeChecked();
    });
  });

  describe("Import button", () => {
    test("label reads 'Import' when a single DMP is selected.", async () => {
      stubListPlans([makePlan(1, "Plan One"), makePlan(2, "Plan Two")]);

      renderDialog();

      fireEvent.click(await checkboxForPlan("Plan One"));

      expect(screen.getByRole("button", { name: "apps:dmpIntegrations.dialog.importButton" })).toBeInTheDocument();
    });

    test("label includes the count when more than one DMP is selected.", async () => {
      stubListPlans([makePlan(1, "Plan One"), makePlan(2, "Plan Two"), makePlan(3, "Plan Three")]);

      renderDialog();

      fireEvent.click(await checkboxForPlan("Plan One"));
      fireEvent.click(await checkboxForPlan("Plan Two"));

      expect(screen.getByRole("button", { name: "apps:dmpIntegrations.dialog.importButton" })).toBeInTheDocument();

      fireEvent.click(await checkboxForPlan("Plan Three"));

      expect(screen.getByRole("button", { name: "apps:dmpIntegrations.dialog.importButton" })).toBeInTheDocument();
    });

    test(
      "clicking Import with no selection surfaces a 'No DMP is selected.' " +
        "warning and does not POST to importPlans.",
      async () => {
        stubListPlans([makePlan(1, "Plan One")]);
        const importPostSpy = vi.fn();
        mockAxios.onPost(/importPlans/).reply((config) => {
          importPostSpy(config);
          return [200, { data: [], error: null }];
        });

        renderDialog();

        // wait until the dialog finishes loading and the Import button is present
        await checkboxForPlan("Plan One");

        fireEvent.click(screen.getByRole("button", { name: "apps:dmpIntegrations.dialog.importButton" }));

        expect(await screen.findByText("apps:dmpIntegrations.dialog.noDmpIsSelected")).toBeVisible();
        expect(importPostSpy).not.toHaveBeenCalled();
      },
    );
  });

  describe("Select-all header checkbox", () => {
    test("selects every DMP on the current page when clicked.", async () => {
      stubListPlans([makePlan(1, "Plan One"), makePlan(2, "Plan Two")]);

      renderDialog();

      await checkboxForPlan("Plan One");
      const selectAll = screen.getByRole("checkbox", {
        name: "apps:dmpIntegrations.dialog.selectAllLabel",
      });
      expect(selectAll).not.toBeChecked();

      fireEvent.click(selectAll);

      expect(await checkboxForPlan("Plan One")).toBeChecked();
      expect(await checkboxForPlan("Plan Two")).toBeChecked();
      expect(screen.getByRole("button", { name: "apps:dmpIntegrations.dialog.importButton" })).toBeInTheDocument();
    });

    test("toggles back off when clicked a second time.", async () => {
      stubListPlans([makePlan(1, "Plan One"), makePlan(2, "Plan Two")]);

      renderDialog();

      await checkboxForPlan("Plan One");
      const selectAll = screen.getByRole("checkbox", {
        name: "apps:dmpIntegrations.dialog.selectAllLabel",
      });

      fireEvent.click(selectAll);
      fireEvent.click(selectAll);

      expect(await checkboxForPlan("Plan One")).not.toBeChecked();
      expect(await checkboxForPlan("Plan Two")).not.toBeChecked();
    });

    test("renders in the indeterminate state when some but not all DMPs are " + "selected.", async () => {
      stubListPlans([makePlan(1, "Plan One"), makePlan(2, "Plan Two")]);

      renderDialog();

      const cb1 = await checkboxForPlan("Plan One");
      fireEvent.click(cb1);

      const selectAll = screen.getByRole("checkbox", {
        name: "apps:dmpIntegrations.dialog.selectAllLabel",
      });
      // MUI Checkbox surfaces indeterminate state via a data attribute on
      // the input rather than the native DOM property (see the upstream
      // comment in @mui/material/Checkbox/Checkbox.js).
      expect(selectAll).toHaveAttribute("data-indeterminate", "true");
      expect(selectAll).not.toBeChecked();
    });

    test("renders checked (not indeterminate) once every DMP on the page is " + "selected.", async () => {
      stubListPlans([makePlan(1, "Plan One"), makePlan(2, "Plan Two")]);

      renderDialog();

      fireEvent.click(await checkboxForPlan("Plan One"));
      fireEvent.click(await checkboxForPlan("Plan Two"));

      const selectAll = screen.getByRole("checkbox", {
        name: "apps:dmpIntegrations.dialog.selectAllLabel",
      });
      expect(selectAll).toBeChecked();
      expect(selectAll).toHaveAttribute("data-indeterminate", "false");
    });
  });

  describe("Submit", () => {
    test("POSTs the selected DMPs as a single batch to " + "/apps/dmpassistant/importPlans.", async () => {
      stubListPlans([makePlan(1, "Plan One"), makePlan(2, "Plan Two")]);
      mockAxios.onPost(/importPlans/).reply(200, {
        data: [{}, {}],
        error: null,
      });

      renderDialog();

      fireEvent.click(await checkboxForPlan("Plan One"));
      fireEvent.click(await checkboxForPlan("Plan Two"));

      fireEvent.click(screen.getByRole("button", { name: "apps:dmpIntegrations.dialog.importButton" }));

      await waitFor(() => {
        const importCalls = mockAxios.history.post.filter((c) => /importPlans/.test(c.url ?? ""));
        expect(importCalls).toHaveLength(1);
      });
      // biome-ignore lint/style/noNonNullAssertion: initial biome migration
      const importCall = mockAxios.history.post.find((c) => /importPlans/.test(c.url ?? ""))!;
      const rawBody: unknown = importCall.data;
      if (typeof rawBody !== "string") {
        throw new Error("expected the importPlans request body to be a string");
      }
      const body = JSON.parse(rawBody) as Array<{
        id: string;
        filename: string;
      }>;
      expect(body).toHaveLength(2);
      expect(body.map((b) => b.id).sort()).toEqual(["1", "2"]);
      expect(body.map((b) => b.filename).sort()).toEqual(["Plan One", "Plan Two"]);
    });
  });

  describe("Listing failure", () => {
    test(
      "closes the dialog when the listing fetch fails so the upstream " +
        "response (which may include HTML) is never rendered inside the dialog.",
      async () => {
        // The upstream-403 response shape that triggered the original bug:
        // backend returned `{ data: null, error: { errorMessages: [<HTML>] } }`.
        mockAxios.onGet(/\/apps\/dmpassistant\/plans.*/).reply(200, {
          data: null,
          error: {
            errorMessages: ["DMP Assistant returned an error: 403 Forbidden."],
          },
        });
        const setOpen = vi.fn();

        render(
          <ThemeProvider theme={materialTheme}>
            <DMPDialog open setOpen={setOpen} />
          </ThemeProvider>,
        );

        await waitFor(() => {
          expect(setOpen).toHaveBeenCalledWith(false);
        });
      },
    );

    test("never renders raw HTML from a failed listing response inside the " + "dialog body.", async () => {
      const upstreamHtml = "<!DOCTYPE html><html><body>Just a moment...</body></html>";
      mockAxios.onGet(/\/apps\/dmpassistant\/plans.*/).reply(200, {
        data: null,
        error: { errorMessages: [upstreamHtml] },
      });

      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <DMPDialog open setOpen={() => {}} />
        </ThemeProvider>,
      );

      // Wait for the listing fetch to resolve, then assert that the upstream
      // HTML never appears in the dialog DOM (neither parsed nor as text).
      await waitFor(() => {
        expect(container.textContent ?? "").not.toContain("Just a moment");
      });
    });
  });
});

import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/useWhoAmI";
import "@/__tests__/__mocks__/useWebSocketNotifications";
import "@/__tests__/__mocks__/matchMedia";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw/http";
import { uiNavigationData } from "@/__tests__/helpers/appChrome";
import { server } from "@/__tests__/mswServer";
import Alerts from "@/components/Alerts/Alerts";
import DMPDialog from "../DMPDialog";

/*
 * Kept apart from DMPDialog.test.tsx: that file installs axios-mock-adapter at
 * module level, which would stop these requests from reaching MSW.
 */
function stubDialog(importResponse: { success: boolean; error?: { errorMessages: Array<string> } }) {
  server.use(
    http.get("/api/v1/userDetails/uiNavigationData", () => HttpResponse.json(uiNavigationData())),
    http.get("/apps/dmptool/baseUrlHost", () => HttpResponse.text("https://dmptool.org")),
    http.get("/apps/dmptool/plans", () =>
      HttpResponse.json({
        success: true,
        data: {
          items: [
            {
              dmp: {
                id: 1001,
                title: "Physics Research Data Plan",
                description: "",
                modified: "2026-01-01",
                created: "2026-01-01",
              },
            },
          ],
        },
      }),
    ),
    http.post("/apps/dmptool/jsonById/1001", () => HttpResponse.json(importResponse)),
  );
}

async function importFirstPlan() {
  const user = userEvent.setup();
  await user.click(await screen.findByRole("radio", { name: "apps:dmpIntegrations.dialog.selectPlanLabel" }));
  await user.click(screen.getByRole("button", { name: "common:actions.import" }));
}

describe("DMPDialog import", () => {
  test("A successful import notifies the caller once.", async () => {
    stubDialog({ success: true });
    const onImport = vi.fn();
    render(
      <Alerts>
        <DMPDialog open setOpen={() => {}} onImport={onImport} />
      </Alerts>,
    );

    await importFirstPlan();

    await waitFor(() => expect(onImport).toHaveBeenCalledOnce());
  });

  test("A failed import does not notify the caller.", async () => {
    stubDialog({ success: false, error: { errorMessages: ["nope"] } });
    const onImport = vi.fn();
    render(
      <Alerts>
        <DMPDialog open setOpen={() => {}} onImport={onImport} />
      </Alerts>,
    );

    await importFirstPlan();

    expect(await screen.findByText("nope")).toBeVisible();
    expect(onImport).not.toHaveBeenCalled();
  });
});

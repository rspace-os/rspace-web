import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import DMPDialog from "./DMPDialog";
import AxeBuilder from "@axe-core/playwright";

test.beforeEach(async ({ router }) => {
  await router.route(/\/apps\/argos\/plans.*/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          totalCount: 2,
          data: [
            {
              id: "e27789f1-de35-4b4a-9587-a46d131c366e",
              label: "Foo",
              grant: "Foo's grant",
              createdAt: 0,
              modifiedAt: 0,
            },
            {
              id: "e9a73d77-adfa-4546-974f-4a4a623b53a8",
              label: "Bar",
              grant: "Bar's grant",
              createdAt: 0,
              modifiedAt: 0,
            },
          ],
        },
        error: null,
        errorMsg: null,
        success: true,
      }),
    });
  });
  await router.route("/userform/ajax/inventoryOauthToken", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3NDI5MTc1NTgsImV4cCI6MTc0MjkyMTE1OCwicmVmcmVzaFRva2VuSGFzaCI6ImE3ZGZkYjVkMjhiMmRkYWRmYWJhYzhkOTRlM2ZlZWE2Y2QxM2I3M2EyMWVmYTRmNTJmMDVkOTI4MTBmMTc5YTQifQ.JsqUiGczASw4Pr9pEsc3aYFqgymMGVvHNCsyL6oudjY",
      }),
    });
  });
  await router.route("/api/v1/userDetails/uiNavigationData", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        bannerImgSrc: "/public/banner",
        visibleTabs: {
          inventory: true,
          myLabGroups: true,
          published: false,
          system: false,
        },
        userDetails: {
          username: "user1a",
          fullName: "user user",
          email: "user@user.com",
          orcidId: null,
          orcidAvailable: false,
          profileImgSrc: null,
          lastSession: "2025-03-25T15:45:57.000Z",
        },
        operatedAs: false,
        nextMaintenance: null,
      }),
    });
  });
});

test("Should have no axe violations.", async ({ mount, page }) => {
  await mount(<DMPDialog open={true} setOpen={() => {}} />);
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByText("foo")).toBeVisible();

  const rows = page.getByRole("row");
  expect(await rows.count()).toBeGreaterThan(1);

  const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
  expect(
    accessibilityScanResults.violations.filter((v) => {
      /*
       * MUI DataGrid renders its immediate children with role=presentation,
       * which Firefox considers to be a violation of the ARIA specification.
       * There's nothing we can do about it and Chrome and Safari, which the
       * vast majority of our users use, does not consider it a violation.
       */
      return (
        v.description !==
        "Ensure elements with an ARIA role that require child roles contain them"
      );
    })
  ).toEqual([]);
});

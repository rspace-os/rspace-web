import {
  test,
  expect,
  type MountResult,
} from "@playwright/experimental-ct-react";
import React from "react";
import { SimplePageWithAppBar } from "./index.story";

const feature = test.extend<{
  Given: {
    "the app bar is being shown": (
      props: React.ComponentProps<typeof SimplePageWithAppBar>
    ) => Promise<void>;
  };
  Once: {};
  When: {};
  Then: {
    "a visually hidden heading with {title} should be shown": (props: {
      title: string;
    }) => Promise<void>;
    "a dialog heading with {title} should be shown": (props: {
      title: string;
    }) => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the app bar is being shown": async (props) => {
        const mountResult: MountResult = await mount(
          <SimplePageWithAppBar {...props} />
        );
        expect(mountResult).toBeDefined();
      },
    });
  },
  Once: async ({ page }, use) => {
    await use({});
  },
  When: async ({ page }, use) => {
    await use({});
  },
  Then: async ({ page }, use) => {
    await use({
      "a visually hidden heading with {title} should be shown": async ({
        title,
      }) => {
        const heading = page.getByRole("heading", { level: 1 });
        await expect(heading).toHaveText(title);
        /*
         * Difficult to assert that the heading is visually hidden, as a few
         * different CSS styles are applied to ensure that it is hidden by all
         * browsers but is still picked up by screen readers.
         */
      },
      "a dialog heading with {title} should be shown": async ({ title }) => {
        const heading = page.getByRole("heading", { level: 2 });
        await expect(heading).toHaveText(title);
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({ router }) => {
  await router.route("/userform/ajax/inventoryOauthToken", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3NDI5MTc1NTgsImV4cCI6MTc0MjkyMTE1OCwicmVmcmVzaFRva2VuSGFzaCI6ImE3ZGZkYjVkMjhiMmRkYWRmYWJhYzhkOTRlM2ZlZWE2Y2QxM2I3M2EyMWVmYTRmNTJmMDVkOTI4MTBmMTc5YTQifQ.JsqUiGczASw4Pr9pEsc3aYFqgymMGVvHNCsyL6oudjY",
      }),
    });
  });
  await router.route("/session/ajax/livechatProperties", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ livechatEnabled: false }),
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
  await router.route("/public/banner", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "image/png",
      body: Buffer.from("fake image data"),
    });
  });
});

feature.afterEach(({}) => {});

test.describe("App Bar", () => {
  feature(
    "On page variant, the heading is only exposed to screen readers",
    async ({ Given, Then }) => {
      await Given["the app bar is being shown"]({
        variant: "page",
        currentPage: "Test Page",
      });
      await Then["a visually hidden heading with {title} should be shown"]({
        title: "Test Page",
      });
      /*
       * The app bar should have a visually hidden heading that is accessible to
       * screen readers when we don't show the heading to all users.
       */
    }
  );
  feature(
    "On dialog variant, the heading is always shown",
    async ({ Given, Then }) => {
      await Given["the app bar is being shown"]({
        variant: "dialog",
        currentPage: "Test Page",
      });
      await Then["a dialog heading with {title} should be shown"]({
        title: "Test Page",
      });
    }
  );
});

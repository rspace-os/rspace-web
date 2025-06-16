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
  When: {
    "the user clicks the avatar": () => Promise<void>;
  };
  Then: {
    "a visually hidden heading with {title} should be shown": (props: {
      title: string;
    }) => Promise<void>;
    "a dialog heading with {title} should be shown": (props: {
      title: string;
    }) => Promise<void>;
    "the profile and logout options should be visible": () => Promise<void>;
    "the Inventory link should be hidden": () => Promise<void>;
    "the MyRSpace link should point to /groups/viewPIGroup": () => Promise<void>;
    "the MyRSpace link should point to /userform": () => Promise<void>;
    "the System link should be hidden": () => Promise<void>;
    "the Published menuitem should be hidden": () => Promise<void>;
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
    await use({
      "the user clicks the avatar": async () => {
        const avatar = page.getByRole("button", { name: "Account Menu" });
        await expect(avatar).toBeVisible();
        await avatar.click();
      },
    });
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
      "the profile and logout options should be visible": async () => {
        const accountMenu = page.getByRole("menu", {
          name: /account menu/i,
        });
        await expect(accountMenu).toHaveText(/user user/i);
        const logoutOption = page.getByRole("menuitem", { name: /log out/i });
        await expect(logoutOption).toBeVisible();
      },
      "the Inventory link should be hidden": async () => {
        await expect(
          page.getByRole("link", { name: /inventory/i })
        ).not.toBeVisible();
      },
      "the System link should be hidden": async () => {
        await expect(
          page.getByRole("link", { name: /system/i })
        ).not.toBeVisible();
      },
      "the Published menuitem should be hidden": async () => {
        const accontMenuButton = page.getByRole("button", {
          name: "Account Menu",
        });
        await accontMenuButton.click();
        await expect(
          page.getByRole("menuitem", { name: /published/i })
        ).not.toBeVisible();
      },
      "the MyRSpace link should point to /groups/viewPIGroup": async () => {
        const myRSpaceLink = page.getByRole("link", {
          name: /my rspace/i,
        });
        await expect(myRSpaceLink).toHaveAttribute(
          "href",
          "/groups/viewPIGroup"
        );
      },
      "the MyRSpace link should point to /userform": async () => {
        const myRSpaceLink = page.getByRole("link", {
          name: /my rspace/i,
        });
        await expect(myRSpaceLink).toHaveAttribute("href", "/userform");
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
          published: true,
          system: true,
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

  feature(
    "When the user avatar is clicked, a menu should appear with profile and logout options",
    async ({ Given, When, Then }) => {
      await Given["the app bar is being shown"]({
        variant: "page",
        currentPage: "Test Page",
      });
      await When["the user clicks the avatar"]();
      await Then["the profile and logout options should be visible"]();
    }
  );

  /*
   * The /uiNavigationData endpoint provides the visibleTabs object, which
   * determines which navigation opions are available in the app bar and the
   * behaviour of the ones that are visible. These options are determined based
   * on the user's permissions and the server * configuration.
   */
  feature.describe("visibleTabs", () => {
    feature(
      "When visibleTabs.inventory is false, the link should be hidden",
      async ({ Given, Then, page }) => {
        await page.route(
          "/api/v1/userDetails/uiNavigationData",
          async (route) => {
            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify({
                bannerImgSrc: "/public/banner",
                visibleTabs: {
                  inventory: false,
                  myLabGroups: true,
                  published: false,
                  system: false,
                },
                userDetails: {
                  username: "user1a",
                  fullName: "user user",
                  email: "",
                  orcidId: null,
                  orcidAvailable: false,
                  profileImgSrc: null,
                  lastSession: "2025-03-25T15:45:57.000Z",
                },
                operatedAs: false,
                nextMaintenance: null,
              }),
            });
          }
        );
        await Given["the app bar is being shown"]({
          variant: "page",
        });
        await Then["the Inventory link should be hidden"]();
        /*
         * This is when the sysadmin has disallowed Inventory entirely.
         */
      }
    );
    feature(
      "When visibleTabs.myLabGroups is true, the link should point to /groups/viewPIGroup",
      async ({ Given, Then, page }) => {
        await page.route(
          "/api/v1/userDetails/uiNavigationData",
          async (route) => {
            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify({
                bannerImgSrc: "/public/banner",
                visibleTabs: {
                  inventory: false,
                  myLabGroups: true,
                  published: false,
                  system: false,
                },
                userDetails: {
                  username: "user1a",
                  fullName: "user user",
                  email: "",
                  orcidId: null,
                  orcidAvailable: false,
                  profileImgSrc: null,
                  lastSession: "2025-03-25T15:45:57.000Z",
                },
                operatedAs: false,
                nextMaintenance: null,
              }),
            });
          }
        );
        await Given["the app bar is being shown"]({
          variant: "page",
        });
        await Then["the MyRSpace link should point to /groups/viewPIGroup"]();
        /*
         * For PIs, the MyRSpace link points to the group page for which the
         * user is the PI, which is more helpful to them than their personal
         * page.
         */
      }
    );
    feature(
      "When visibleTabs.myLabGroups is false, the link should point to /userform",
      async ({ Given, Then, page }) => {
        await page.route(
          "/api/v1/userDetails/uiNavigationData",
          async (route) => {
            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify({
                bannerImgSrc: "/public/banner",
                visibleTabs: {
                  inventory: false,
                  myLabGroups: false,
                  published: false,
                  system: false,
                },
                userDetails: {
                  username: "user1a",
                  fullName: "user user",
                  email: "",
                  orcidId: null,
                  orcidAvailable: false,
                  profileImgSrc: null,
                  lastSession: "2025-03-25T15:45:57.000Z",
                },
                operatedAs: false,
                nextMaintenance: null,
              }),
            });
          }
        );
        await Given["the app bar is being shown"]({
          variant: "page",
        });
        await Then["the MyRSpace link should point to /userform"]();
        /*
         * For non-PIs, the MyRSpace link points to their account page,
         * which is more helpful to them than the group page.
         */
      }
    );
    feature(
      "When visibleTabs.system is false, the link should be hidden",
      async ({ Given, Then, page }) => {
        await page.route(
          "/api/v1/userDetails/uiNavigationData",
          async (route) => {
            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify({
                bannerImgSrc: "/public/banner",
                visibleTabs: {
                  inventory: false,
                  myLabGroups: true,
                  published: false,
                  system: false,
                },
                userDetails: {
                  username: "user1a",
                  fullName: "user user",
                  email: "",
                  orcidId: null,
                  orcidAvailable: false,
                  profileImgSrc: null,
                  lastSession: "2025-03-25T15:45:57.000Z",
                },
                operatedAs: false,
                nextMaintenance: null,
              }),
            });
          }
        );
        await Given["the app bar is being shown"]({
          variant: "page",
        });
        await Then["the System link should be hidden"]();
        /*
         * The System page is only available to sysadmins, so if the user
         * does not have the permissions to view it, it should not be
         * shown.
         */
      }
    );
    feature(
      "When visibleTabs.published is false, the menuitem should be hidden",
      async ({ Given, Then, page }) => {
        await page.route(
          "/api/v1/userDetails/uiNavigationData",
          async (route) => {
            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify({
                bannerImgSrc: "/public/banner",
                visibleTabs: {
                  inventory: false,
                  myLabGroups: true,
                  published: false,
                  system: false,
                },
                userDetails: {
                  username: "user1a",
                  fullName: "user user",
                  email: "",
                  orcidId: null,
                  orcidAvailable: false,
                  profileImgSrc: null,
                  lastSession: "2025-03-25T15:45:57.000Z",
                },
                operatedAs: false,
                nextMaintenance: null,
              }),
            });
          }
        );
        await Given["the app bar is being shown"]({
          variant: "page",
        });
        await Then["the Published menuitem should be hidden"]();
        /*
         * The published page is another piece of functionality that the
         * sysadmin can disable for all users, so if it has not been
         * enabled then the menuitem should not be shown.
         */
      }
    );
  });
});

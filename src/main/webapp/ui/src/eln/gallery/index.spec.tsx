import { test, expect, MountResult } from "@playwright/experimental-ct-react";
import React from "react";
import { GalleryStory } from "./index.story";
import * as Jwt from "jsonwebtoken";
import fc from "fast-check";

const feature = test.extend<{
  Let: {
    "the id be an integer": (
      cb: (id: number) => Promise<void>,
    ) => Promise<void>;
  };
  Given: {
    "the Gallery is mounted": ({
      url,
    }?: {
      url?: React.ComponentProps<typeof GalleryStory>["urlSuffix"];
    }) => Promise<MountResult>;
  };
  Once: {};
  When: {};
  Then: {
    "the page title should be": (title: string) => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Let: async ({}, use) => {
    await use({
      "the id be an integer": async (cb: (id: number) => Promise<void>) => {
        await fc.assert(
          fc.asyncProperty(fc.integer({ min: 1 }), async (id) => {
            await cb(id);
          }),
          { numRuns: 5 },
        );
      },
    });
  },
  Given: async ({ mount }, use) => {
    await use({
      "the Gallery is mounted": async ({
        url,
      }: {
        url?: React.ComponentProps<typeof GalleryStory>["urlSuffix"];
      } = {}) => {
        return mount(<GalleryStory urlSuffix={url} />);
      },
    });
  },
  Once: async ({ page }, use) => {
    await use({});
  },
  When: async ({ page }, use) => {
    await use({});
  },
  Then: async ({ page, networkRequests }, use) => {
    await use({
      "the page title should be": async (title: string) => {
        await expect(page).toHaveTitle(title);
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({ router }) => {
  await router.route("/userform/ajax/inventoryOauthToken", (route) => {
    const payload = {
      iss: "http://localhost:8080",
      iat: new Date().getTime(),
      exp: Math.floor(Date.now() / 1000) + 300,
      refreshTokenHash:
        "fe15fa3d5e3d5a47e33e9e34229b1ea2314ad6e6f13fa42addca4f1439582a4d",
    };
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: Jwt.sign(payload, "dummySecretKey"),
      }),
    });
  });
  await router.route("/session/ajax/analyticsProperties", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        analyticsEnabled: false,
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
  await router.route("/deploymentproperties/ajax/property*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(false),
    });
  });
  await router.route("/userform/ajax/preference*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
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
  await router.route("/*/supportedExts", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
    });
  });
  await router.route("/gallery/getUploadedFiles*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          parentId: 1,
          items: {
            results: [],
          },
        },
      }),
    });
  });
  await router.route("/api/v1/folders/123*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: 123,
        globalId: "GF123",
        name: "Examples",
        created: "2025-07-07T11:09:18.126Z",
        lastModified: "2025-07-07T11:09:18.126Z",
        parentFolderId: 131,
        notebook: false,
        mediaType: "Images",
        pathToRootFolder: [
          {
            id: 131,
            globalId: "GF131",
            name: "Images",
            created: "2025-07-07T11:09:18.119Z",
            lastModified: "2025-07-07T11:09:18.119Z",
            parentFolderId: 130,
            notebook: false,
            mediaType: "Images",
            pathToRootFolder: null,
            _links: [],
          },
          {
            id: 130,
            globalId: "GF130",
            name: "Gallery",
            created: "2025-07-07T11:09:18.112Z",
            lastModified: "2025-07-07T11:09:18.112Z",
            parentFolderId: 124,
            notebook: false,
            mediaType: null,
            pathToRootFolder: null,
            _links: [],
          },
        ],
        _links: [
          {
            link: "http://localhost:8080/api/v1/folders/132",
            rel: "self",
          },
        ],
      }),
    });
  });
  await router.route("/api/v1/files/*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: 456,
        globalId: "GL456",
        name: "Picture1.png",
        caption: null,
        contentType: "image/x-png",
        created: "2025-07-07T11:09:18.312Z",
        size: 40721,
        version: 1,
        parentFolderId: 123,
        _links: [
          {
            link: "http://localhost:8080/api/v1/files/149",
            rel: "self",
          },
          {
            link: "http://localhost:8080/api/v1/files/149/file",
            rel: "enclosure",
          },
        ],
      }),
    });
  });
});

// feature.afterEach(({}) => {});

test.describe("Gallery", () => {
  test.describe("Should have a title that describes the current page", () => {
    //     /*
    //      * This an a11y requirement, under WCAG 2.2 criteria 2.4.2 Page Titled (A),
    //      * see https://www.w3.org/WAI/WCAG21/Understanding/page-titled.html
    //      */
    //     feature(
    //       "On `/gallery', the title should be 'Images | RSpace Gallery'",
    //       async ({ Given, Then }) => {
    //         await Given["the Gallery is mounted"]();
    //         await Then["the page title should be"]("Images | RSpace Gallery");
    //         /*
    //          * The images is the default gallery section.
    //          */
    //       },
    //     );
    //     feature(
    //       "On '/gallery?mediaType=Videos', the title should be 'Videos | RSpace Gallery'",
    //       async ({ Given, Then }) => {
    //         await Given["the Gallery is mounted"]({ url: "?mediaType=Videos" });
    //         await Then["the page title should be"]("Videos | RSpace Gallery");
    //       },
    //     );
    //     feature(
    //       "On '/123', the title should be 'Examples | RSpace Gallery'",
    //       async ({ Given, Then }) => {
    //         await Given["the Gallery is mounted"]({ url: "/123" });
    //         await Then["the page title should be"]("Examples | RSpace Gallery");
    //       },
    //     );
    //     feature(
    //       "On '/item/456', the title should be 'Picture1.png | RSpace Gallery'",
    //       async ({ Given, Then }) => {
    //         return fc.assert(
    //           fc.asyncProperty(fc.integer({ min: 1 }), async (id) => {
    //             const component = await Given["the Gallery is mounted"]({
    //               url: "/item/456",
    //             });
    //             await Then["the page title should be"](
    //               "Picture1.png | RSpace Gallery",
    //             );
    //             component.unmount();
    //           }),
    //         );
    //       },
    //     );

    feature("fast-check test", async ({ mount, Then }) => {
      await fc.assert(
        fc.asyncProperty(fc.integer({ min: 1 }), async (id) => {
          const component = await mount(
            <GalleryStory urlSuffix={`/item/${id}`} />,
          );
          await Then["the page title should be"](
            "Picture1.png | RSpace Gallery",
          );
          await component.unmount();
        }),
        { numRuns: 5 },
      );
    });

    feature("fast-check test 2", async ({ Let, Given, Then }) => {
      await Let["the id be an integer"](async (id) => {
        await Given["the Gallery is mounted"]({
          url: `/item/${id}`,
        });
        await Then["the page title should be"]("Picture1.png | RSpace Gallery");
      });
    });
  });
});

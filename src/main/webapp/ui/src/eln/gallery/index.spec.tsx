import { test, expect, MountResult } from "@playwright/experimental-ct-react";
import React from "react";
import { GalleryStory } from "./index.story";
import * as Jwt from "jsonwebtoken";
import fc from "fast-check";
import { type RouterFixture } from "@playwright/experimental-ct-core";
import { GallerySection } from "./common";

type GivenSteps = {
  "the Gallery is mounted": ({
    url,
  }?: {
    url?: React.ComponentProps<typeof GalleryStory>["urlSuffix"];
  }) => Promise<MountResult>;
};

type ThenSteps = {
  "the page title should be": (title: string) => Promise<void>;
};

const feature = test.extend<{
  Given: GivenSteps;
  Then: ThenSteps;
}>({
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
  Then: async ({ page }, use) => {
    await use({
      "the page title should be": async (title: string) => {
        await expect(page).toHaveTitle(title);
      },
    });
  },
});

function property(name: string) {
  return {
    let<T extends object>(
      letBindings: ({
        IsAny,
        IsOneOf,
      }: {
        IsAny: (
          desc:
            | "file id"
            | "file name without extension"
            | "folder id"
            | "folder name",
          ...args: Array<unknown>
        ) => unknown;
        IsOneOf: (...options: Array<unknown>) => unknown;
      }) => T
    ) {
      return {
        checkThat: (
          testFn: (
            values: T,
            helpers: {
              Given: GivenSteps;
              Then: ThenSteps;
              router: RouterFixture;
            }
          ) => Promise<void>
        ) => {
          let mountedComponent: MountResult | null = null;
          return feature.extend({
            /*
             * With each iteration of the test, we need to make sure that
             * react has a clean state so we wrap the mount function to keep
             * track of the mounted components, so that after the test is done,
             * we can unmount them.
             */
            mount: async ({ mount }, use) => {
              await use(async (component, options) => {
                mountedComponent = await mount(component, options);
                return mountedComponent;
              });
            },
          })(name, async ({ Given, Then, router }) => {
            const letBindingsToArbitraries = letBindings({
              IsAny: (description) => {
                if (description === "file id") {
                  return fc.integer({ min: 1, max: 10000 });
                }
                if (description === "file name without extension") {
                  return fc.string({ minLength: 1, maxLength: 20 });
                }
                if (description === "folder id") {
                  return fc.nat(1000);
                }
                if (description === "folder name") {
                  return fc.string({ minLength: 1, maxLength: 20 });
                }
              },
              IsOneOf: (...options) => fc.constantFrom(...options),
            });

            await fc.assert(
              fc.asyncProperty(
                fc.record(letBindingsToArbitraries),
                async (generated) => {
                  try {
                    await testFn(generated as T, {
                      Given,
                      Then,
                      router,
                    });
                  } finally {
                    if (mountedComponent) {
                      await mountedComponent.unmount();
                      mountedComponent = null;
                    }
                  }
                }
              ),
              { numRuns: 5 }
            );
          });
        },
      };
    },
  };
}

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
  await router.route("/api/v1/folders/*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: 123,
        globalId: `GF123`,
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
      }),
    })
  );
});

feature.afterEach(({}) => {});

test.describe("Gallery", () => {
  test.describe("Should have a title that describes the current page", () => {
    /*
     * This an a11y requirement, under WCAG 2.2 criteria 2.4.2 Page Titled (A),
     * see https://www.w3.org/WAI/WCAG21/Understanding/page-titled.html
     */
    feature(
      "On `/gallery', the title should be 'Images | RSpace Gallery'",
      async ({ Given, Then }) => {
        await Given["the Gallery is mounted"]();
        await Then["the page title should be"]("Images | RSpace Gallery");
        /*
         * The images is the default gallery section.
         */
      }
    );

    property(
      "On '?mediaType={section}', the title should be '{section} | RSpace Gallery'"
    )
      .let(({ IsOneOf }) => ({
        section: IsOneOf(
          "Images",
          "Audios",
          "Videos",
          "Documents",
          "Chemistry",
          "DMPs",
          "Snippets",
          "Miscellaneous",
          "PdfDocuments"
        ) as GallerySection,
      }))
      .checkThat(async ({ section }, { Given, Then }) => {
        await Given["the Gallery is mounted"]({
          url: `?mediaType=${section}`,
        });
        /*
         * For some of the pages, we keep the old URL for backwards compatibility but use a more descriptive title
         */
        if (section === "PdfDocuments") {
          await Then["the page title should be"]("Exports | RSpace Gallery");
        } else if (section === "Audios") {
          await Then["the page title should be"]("Audio | RSpace Gallery");
        } else {
          await Then["the page title should be"](`${section} | RSpace Gallery`);
        }
      });

    property("On '/{id}', the title should be '{folder name} | RSpace Gallery'")
      .let(({ IsAny }) => ({
        id: IsAny("folder id") as number,
        folderName: IsAny("folder name") as string,
      }))
      .checkThat(async ({ id, folderName }, { Given, Then, router }) => {
        await router.route("/api/v1/folders/*", (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              id,
              globalId: `GF${id}`,
              name: folderName,
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
            }),
          })
        );

        await Given["the Gallery is mounted"]({
          url: `/${id}`,
        });
        await Then["the page title should be"](
          `${folderName} | RSpace Gallery`
        );
      });

    property(
      "On '/item/{id}', the title should be '{filename} | RSpace Gallery'"
    )
      .let(({ IsAny }) => ({
        id: IsAny("file id") as number,
        filename: IsAny("file name without extension") as string,
      }))
      .checkThat(async ({ id, filename }, { Given, Then, router }) => {
        await router.route(`/api/v1/files/${id}`, (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              id,
              globalId: `GL${id}`,
              name: `${filename}.jpg`,
              caption: null,
              contentType: "image/jpeg",
              created: "2025-07-07T11:09:18.312Z",
              size: 40721,
              version: 1,
              parentFolderId: 123,
            }),
          })
        );

        await Given["the Gallery is mounted"]({
          url: `/item/${id}`,
        });
        await Then["the page title should be"](
          `${filename}.jpg | RSpace Gallery`
        );
      });
  });
});

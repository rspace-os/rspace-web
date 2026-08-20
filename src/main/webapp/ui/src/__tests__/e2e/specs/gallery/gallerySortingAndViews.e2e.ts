import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { TINY_PNG, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Gallery sorting and views", () => {
  test("As a user, I can sort files by Modification Date and by Name, in either direction", async ({
    pageGallery,
    clientFiles,
    page,
  }) => {
    const token = uniqueName("e2e-gallery-sort");

    const nameB = `b-${token}.png`;
    const nameA = `a-${token}.png`;
    const nameC = `c-${token}.png`;

    const orderOf = async (names: string[]): Promise<string[]> => {
      const cellTexts = await pageGallery.fileGrid.getByRole("gridcell").allInnerTexts();
      return cellTexts.filter((text) => names.includes(text));
    };

    await test.step("Given three files uploaded one at a time, crossing a whole-second boundary each time", async () => {
      let parentFolderId: number | undefined;
      for (const name of [nameB, nameA, nameC]) {
        const uploaded = await clientFiles.uploadFile({ name, mimeType: "image/png", buffer: TINY_PNG });
        parentFolderId = uploaded.parentFolderId;
        await page.waitForTimeout(1100);
      }
      await pageGallery.open(parentFolderId);
      await pageGallery.isLoaded();
    });

    await test.step("Then the default sort (Modification Date, newest first) shows upload order reversed", async () => {
      await expect.poll(() => orderOf([nameA, nameB, nameC])).toEqual([nameC, nameA, nameB]);
    });

    await test.step("When I toggle the active sort direction", async () => {
      await pageGallery.sort.toggleActiveDirection();
    });

    await test.step("Then Modification Date oldest-first shows the actual upload order", async () => {
      await expect.poll(() => orderOf([nameA, nameB, nameC])).toEqual([nameB, nameA, nameC]);
    });

    await test.step("When I sort by Name", async () => {
      await pageGallery.sort.sortBy("Name");
    });

    await test.step("Then Name ascending (A to Z) is alphabetical", async () => {
      await expect.poll(() => orderOf([nameA, nameB, nameC])).toEqual([nameA, nameB, nameC]);
    });

    await test.step("When I toggle the active sort direction again", async () => {
      await pageGallery.sort.toggleActiveDirection();
    });

    await test.step("Then Name descending (Z to A) is reverse-alphabetical", async () => {
      await expect.poll(() => orderOf([nameA, nameB, nameC])).toEqual([nameC, nameB, nameA]);
    });
  });

  test("As a user, I can switch between Grid, Tree, and Carousel views", async ({ pageGallery, clientFiles, page }) => {
    await test.step("Given at least one uploaded file", async () => {
      await clientFiles.uploadFile({
        name: `${uniqueName("e2e-gallery-views")}.png`,
        mimeType: "image/png",
        buffer: TINY_PNG,
      });
      await pageGallery.open();
      await pageGallery.isLoaded();
    });

    await test.step("Grid is the default view", async () => {
      await expect(pageGallery.fileGrid).toBeVisible();
    });

    await test.step("When I switch to Tree view", async () => {
      await pageGallery.views.switchTo("Tree");
    });

    await test.step("Then the tree view of files is shown", async () => {
      await expect(page.getByRole("tree")).toBeVisible();
    });

    await test.step("When I switch to Carousel view", async () => {
      await pageGallery.views.switchTo("Carousel");
    });

    await test.step("Then the carousel view of files is shown", async () => {
      await expect(page.getByRole("region", { name: "Carousel view of files" })).toBeVisible();
    });
  });
});

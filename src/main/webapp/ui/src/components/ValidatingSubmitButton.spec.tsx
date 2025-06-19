import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { ProgressExample, SimpleExample } from "./ValidatingSubmitButton.story";
import AxeBuilder from "@axe-core/playwright";

const createOnClickSpy = () => {
  let clicked = false;

  const handler = () => {
    clicked = true;
  };

  const hasBeenClicked = () => clicked;

  return {
    handler,
    hasBeenClicked,
  };
};

const feature = test.extend<{
  Given: {
    "the ValidatingSubmitButton is rendered": () => Promise<{
      onClickSpy: ReturnType<typeof createOnClickSpy>;
    }>;
    "the ValidatingSubmitButton with progress is rendered": () => Promise<{
      onClickSpy: ReturnType<typeof createOnClickSpy>;
    }>;
  };
  When: {
    "the loading state is triggered": () => Promise<void>;
    "an invalid state is triggered": () => Promise<void>;
    "the user clicks the button": () => Promise<void>;
  };
  Then: {
    "the button should be disabled": () => Promise<void>;
    "the button should be enabled": () => Promise<void>;
    "the validation error popover should be visible": () => Promise<void>;
    "the validation error popover should not be visible": () => Promise<void>;
    "the {onClickSpy} should have been triggered": ({
      onClickSpy,
    }: {
      onClickSpy: ReturnType<typeof createOnClickSpy>;
    }) => void;
    "the progress indicator should not be visible": () => Promise<void>;
    "the progress indicator should be visible": () => Promise<void>;
    "the progress indicator should disappear after completion": () => Promise<void>;
    "the button should have the label 'Submit'": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
    "the button should have type 'submit'": () => Promise<void>;
    "the validation error popover should contain a warning alert with the correct aria-label": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the ValidatingSubmitButton is rendered": async () => {
        const onClickSpy = createOnClickSpy();
        await mount(<SimpleExample onClick={onClickSpy.handler} />);
        return { onClickSpy };
      },
      "the ValidatingSubmitButton with progress is rendered": async () => {
        const onClickSpy = createOnClickSpy();
        await mount(<ProgressExample onClick={onClickSpy.handler} />);
        return { onClickSpy };
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the loading state is triggered": async () => {
        await page.getByRole("button", { name: /Toggle Loading/ }).click();
      },
      "an invalid state is triggered": async () => {
        await page.getByRole("button", { name: /Set Invalid/ }).click();
      },
      "the user clicks the button": async () => {
        await page.getByRole("button", { name: "Submit" }).click();
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "the button should have the label 'Submit'": async () => {
        const button = page.getByRole("button", { name: "Submit" });
        await expect(button).toBeVisible();
        await expect(button).toHaveText("Submit");
      },
      "the button should be disabled": async () => {
        const button = page.getByRole("button", { name: "Submit" });
        await expect(button).toBeDisabled();
      },
      "the button should be enabled": async () => {
        const button = page.getByRole("button", { name: "Submit" });
        await expect(button).toBeEnabled();
      },
      "the validation error popover should be visible": async () => {
        const popover = page.getByRole("dialog");
        await expect(popover).toBeVisible();
      },
      "the validation error popover should not be visible": async () => {
        const popover = page.getByRole("dialog");
        await expect(popover).not.toBeVisible();
      },
      "the {onClickSpy} should have been triggered": ({ onClickSpy }) => {
        expect(onClickSpy.hasBeenClicked()).toBe(true);
      },
      "the progress indicator should not be visible": async () => {
        const progressBar = page.locator('[role="progressbar"]');
        await expect(progressBar).not.toBeVisible();
      },
      "the progress indicator should be visible": async () => {
        const progressBar = page.getByRole("progressbar");
        await expect(progressBar).toBeVisible();
      },
      "the progress indicator should disappear after completion": async () => {
        const progressBar = page.getByRole("progressbar");
        await expect(progressBar).not.toBeVisible({ timeout: 10000 });
      },
      "there shouldn't be any axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(
          accessibilityScanResults.violations.filter((v) => {
            /*
             * These violations are expected in component tests as we're not rendering
             * a complete page with proper document structure:
             *
             * 1. MUI DataGrid renders its immediate children with role=presentation,
             *    which Firefox considers to be a violation
             * 2. Component tests don't have main landmarks as they're isolated components
             * 3. Component tests typically don't have h1 headings as they're not full pages
             * 4. Content not in landmarks is expected in component testing context
             */
            return (
              v.description !==
                "Ensure elements with an ARIA role that require child roles contain them" &&
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region"
            );
          })
        ).toEqual([]);
      },
      "the button should have type 'submit'": async () => {
        await expect(
          page.getByRole("button", { name: /Submit/ })
        ).toHaveAttribute("type", "submit");
      },
      "the validation error popover should contain a warning alert with the correct aria-label":
        async () => {
          const alert = page.getByRole("alert", { name: "Warning" });
          await expect(alert).toBeVisible();
        },
    });
  },
});

test.describe("ValidatingSubmitButton", () => {
  feature("The button shows its descendants", async ({ Given, Then }) => {
    await Given["the ValidatingSubmitButton is rendered"]();
    await Then["the button should have the label 'Submit'"]();
  });

  feature("The button should have type 'submit'", async ({ Given, Then }) => {
    await Given["the ValidatingSubmitButton with progress is rendered"]();
    await Then["the button should have type 'submit'"]();
  });

  feature(
    "When the button is loading, it should be disabled",
    async ({ Given, When, Then }) => {
      await Given["the ValidatingSubmitButton is rendered"]();
      await When["the loading state is triggered"]();
      await Then["the button should be disabled"]();
    }
  );

  feature(
    "When the button is not loading, it should be enabled",
    async ({ Given, Then }) => {
      await Given["the ValidatingSubmitButton is rendered"]();
      await Then["the button should be enabled"]();
    }
  );

  feature(
    "When validation fails, the validation error popover should be visible",
    async ({ Given, When, Then }) => {
      await Given["the ValidatingSubmitButton is rendered"]();
      await When["an invalid state is triggered"]();
      await When["the user clicks the button"]();
      await Then["the validation error popover should be visible"]();
    }
  );

  feature(
    "When validation passes, the validation error popover should not be visible",
    async ({ Given, When, Then }) => {
      await Given["the ValidatingSubmitButton is rendered"]();
      await When["the user clicks the button"]();
      await Then["the validation error popover should not be visible"]();
    }
  );

  feature(
    "When validation passes, the onClick handler should be called",
    async ({ Given, When, Then }) => {
      const { onClickSpy } = await Given[
        "the ValidatingSubmitButton is rendered"
      ]();
      await When["the user clicks the button"]();
      Then["the {onClickSpy} should have been triggered"]({
        onClickSpy,
      });
    }
  );

  test.describe("Progress prop", () => {
    feature(
      "When progress is undefined, the progress indicator should not be visible",
      async ({ Given, Then }) => {
        await Given["the ValidatingSubmitButton is rendered"]();
        await Then["the progress indicator should not be visible"]();
      }
    );

    feature(
      "When progress is set, the progress indicator should be visible",
      async ({ Given, When, Then }) => {
        await Given["the ValidatingSubmitButton with progress is rendered"]();
        await When["the user clicks the button"]();
        await Then["the progress indicator should be visible"]();
      }
    );

    feature(
      "When progress reaches 100, the progress indicator should disappear",
      async ({ Given, When, Then }) => {
        await Given["the ValidatingSubmitButton with progress is rendered"]();
        await When["the user clicks the button"]();
        await Then[
          "the progress indicator should disappear after completion"
        ]();
      }
    );
  });

  test.describe("Accessibility", () => {
    feature("Should have no axe violations", async ({ Given, Then }) => {
      await Given["the ValidatingSubmitButton with progress is rendered"]();
      await Then["there shouldn't be any axe violations"]();
    });

    /*
     * Note that the accessible role of the popup is asserted by the
     * "When validation fails, the validation error popover should be visible"
     * feature assertion.
     */

    feature(
      "When validation fails, the validation error popover should contain a warning alert",
      async ({ Given, When, Then }) => {
        await Given["the ValidatingSubmitButton is rendered"]();
        await When["an invalid state is triggered"]();
        await When["the user clicks the button"]();
        await Then["the validation error popover should be visible"]();
        await Then[
          "the validation error popover should contain a warning alert with the correct aria-label"
        ]();
      }
    );
  });
});

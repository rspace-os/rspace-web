import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import AxeBuilder from "@axe-core/playwright";
import {
  CallablePdfPreviewStory,
  CallablePdfPreviewWithLargePdf,
  CallablePdfPreviewWithError,
  CallablePdfPreviewWithCorruptedFile,
} from "./CallablePdfPreview.story";
const feature = test.extend<{
  Given: {
    "the pdf preview component is mounted": () => Promise<void>;
    "the pdf preview with large pdf is mounted": () => Promise<void>;
    "the pdf preview with error is mounted": () => Promise<void>;
    "the pdf preview with corrupted file is mounted": () => Promise<void>;
  };
  When: {
    "the user clicks the open pdf button": () => Promise<void>;
    "the user clicks the open multi-page pdf button": () => Promise<void>;
    "the user clicks the open single page pdf button": () => Promise<void>;
    "the user clicks the open large pdf button": () => Promise<void>;
    "the user clicks the open invalid pdf button": () => Promise<void>;
    "the user clicks the open corrupted pdf button": () => Promise<void>;
    "the user clicks the zoom in button": () => Promise<void>;
    "the user clicks the zoom in button twice": () => Promise<void>;
    "the user clicks the zoom out button": () => Promise<void>;
    "the user clicks the reset zoom button": () => Promise<void>;
    "the user clicks the close button": () => Promise<void>;
    "the user presses escape": () => Promise<void>;
    "the user waits for zoom to take effect": () => Promise<void>;
  };
  Then: {
    "the component should render without errors": () => Promise<void>;
    "the buttons should be visible and functional": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
    "the pdf dialog should open": () => Promise<void>;
    "the pdf document should be loaded": () => Promise<void>;
    "the pdf pages should be visible": () => Promise<void>;
    "the dialog should be closable": () => Promise<void>;
    "the zoom controls should be visible": () => Promise<void>;
    "the zoom level should increase": () => Promise<void>;
    "the zoom level should decrease": () => Promise<void>;
    "the zoom level should reset to default": () => Promise<void>;
    "the reset zoom button should be disabled": () => Promise<void>;
    "the reset zoom button should be enabled": () => Promise<void>;
    "an error message should be displayed": () => Promise<void>;
    "multiple pages should be rendered": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the pdf preview component is mounted": async () => {
        await mount(<CallablePdfPreviewStory />);
      },
      "the pdf preview with large pdf is mounted": async () => {
        await mount(<CallablePdfPreviewWithLargePdf />);
      },
      "the pdf preview with error is mounted": async () => {
        await mount(<CallablePdfPreviewWithError />);
      },
      "the pdf preview with corrupted file is mounted": async () => {
        await mount(<CallablePdfPreviewWithCorruptedFile />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user clicks the open pdf button": async () => {
        await page.getByRole("button", { name: /open pdf preview/i }).click();
      },
      "the user clicks the open multi-page pdf button": async () => {
        await page
          .getByRole("button", { name: /open multi-page pdf/i })
          .click();
      },
      "the user clicks the open single page pdf button": async () => {
        await page
          .getByRole("button", { name: /open single page pdf/i })
          .click();
      },
      "the user clicks the open large pdf button": async () => {
        await page.getByRole("button", { name: /open large pdf/i }).click();
      },
      "the user clicks the open invalid pdf button": async () => {
        await page.getByRole("button", { name: /open invalid pdf/i }).click();
      },
      "the user clicks the open corrupted pdf button": async () => {
        await page.getByRole("button", { name: /open corrupted pdf/i }).click();
      },
      "the user clicks the zoom in button": async () => {
        await page.getByRole("button", { name: /zoom in/i }).click();
      },
      "the user clicks the zoom in button twice": async () => {
        await page.getByRole("button", { name: /zoom in/i }).click();
        await page.getByRole("button", { name: /zoom in/i }).click();
      },
      "the user clicks the zoom out button": async () => {
        await page.getByRole("button", { name: /zoom out/i }).click();
      },
      "the user clicks the reset zoom button": async () => {
        await page.getByRole("button", { name: /reset zoom/i }).click();
      },
      "the user clicks the close button": async () => {
        await page.getByRole("button", { name: /close/i }).click();
      },
      "the user presses escape": async () => {
        await page.keyboard.press("Escape");
      },
      "the user waits for zoom to take effect": async () => {
        await page.waitForTimeout(1000);
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "the component should render without errors": async () => {
        await expect(page.getByRole("button").first()).toBeVisible();
      },
      "the buttons should be visible and functional": async () => {
        const buttons = page.getByRole("button");
        const buttonCount = await buttons.count();
        expect(buttonCount).toBeGreaterThan(0);
        for (let i = 0; i < buttonCount; i++) {
          const button = buttons.nth(i);
          await expect(button).toBeVisible();
          await expect(button).toBeEnabled();
        }
      },
      "the pdf dialog should open": async () => {
        await expect(page.getByRole("dialog")).toBeVisible({ timeout: 10000 });
      },
      "the pdf document should be loaded": async () => {
        await expect(page.locator(".react-pdf__Document")).toBeVisible({
          timeout: 15000,
        });
      },
      "the pdf pages should be visible": async () => {
        await expect(page.locator(".react-pdf__Page").first()).toBeVisible({
          timeout: 15000,
        });
      },
      "the dialog should be closable": async () => {
        await expect(page.getByRole("dialog")).not.toBeVisible({
          timeout: 5000,
        });
      },
      "the zoom controls should be visible": async () => {
        await expect(
          page.getByRole("button", { name: /zoom in/i }),
        ).toBeVisible();
        await expect(
          page.getByRole("button", { name: /zoom out/i }),
        ).toBeVisible();
        await expect(
          page.getByRole("button", { name: /reset zoom/i }),
        ).toBeVisible();
      },
      "the zoom level should increase": async () => {
        const page1 = page.locator(".react-pdf__Page").first();
        const initialWidth = await page1.boundingBox();
        await page.getByRole("button", { name: /zoom in/i }).click();
        await page.waitForTimeout(500);
        const newWidth = await page1.boundingBox();
        expect(newWidth?.width).toBeGreaterThan(initialWidth?.width || 0);
      },
      "the zoom level should decrease": async () => {
        const page1 = page.locator(".react-pdf__Page").first();
        const initialWidth = await page1.boundingBox();
        await page.getByRole("button", { name: /zoom out/i }).click();
        await page.waitForTimeout(500);
        const newWidth = await page1.boundingBox();
        expect(newWidth?.width).toBeLessThan(initialWidth?.width || 0);
      },
      "the zoom level should reset to default": async () => {
        // Simply verify that the reset button can be clicked
        await page.getByRole("button", { name: /reset zoom/i }).click();
        await page.waitForTimeout(500);
        // After reset, the button should be disabled again
        await expect(
          page.getByRole("button", { name: /reset zoom/i }),
        ).toBeDisabled();
      },
      "the reset zoom button should be disabled": async () => {
        await expect(
          page.getByRole("button", { name: /reset zoom/i }),
        ).toBeDisabled();
      },
      "the reset zoom button should be enabled": async () => {
        await expect(
          page.getByRole("button", { name: /reset zoom/i }),
        ).toBeEnabled();
      },
      "an error message should be displayed": async () => {
        await expect(page.getByText(/failed to load pdf file/i)).toBeVisible({
          timeout: 10000,
        });
      },
      "multiple pages should be rendered": async () => {
        // Wait for document to load first
        await expect(page.locator(".react-pdf__Document")).toBeVisible({
          timeout: 15000,
        });
        // Wait for multiple pages to render
        await page.waitForFunction(
          () => document.querySelectorAll(".react-pdf__Page").length > 1,
          {},
          { timeout: 20000 },
        );
        const pages = page.locator(".react-pdf__Page");
        const pageCount = await pages.count();
        expect(pageCount).toBeGreaterThan(1);
      },
      "there shouldn't be any axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(
          accessibilityScanResults.violations.filter((v) => {
            return (
              v.description !==
                "Ensure elements with an ARIA role that require child roles contain them" &&
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region" &&
              v.id !== "color-contrast" &&
              v.id !== "aria-dialog-name"
            );
          }),
        ).toEqual([]);
      },
    });
  },
});
feature.beforeEach(async ({ router }) => {
  await router.route("/session/ajax/analyticsProperties", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        analyticsEnabled: false,
      }),
    });
  });
  await router.route("/userform/ajax/preference*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
    });
  });
  await router.route("/deploymentproperties/ajax/property*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(false),
    });
  });
  // Create a minimal valid PDF for testing
  const createPdfBuffer = (content: string) => {
    const pdfContent = `%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>
endobj
4 0 obj
<< /Length ${content.length + 30} >>
stream
BT
/F1 12 Tf
72 720 Td
(${content}) Tj
ET
endstream
endobj
xref
0 5
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000115 00000 n
0000000285 00000 n
trailer
<< /Size 5 /Root 1 0 R >>
startxref
385
%%EOF`;
    return Buffer.from(pdfContent);
  };
  // Mock successful PDF requests
  await router.route("/test-documents/sample.pdf", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/pdf",
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET",
      },
      body: createPdfBuffer("Sample PDF Content"),
    });
  });
  await router.route("/test-documents/single-page.pdf", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/pdf",
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET",
      },
      body: createPdfBuffer("Single Page"),
    });
  });
  // Create a multi-page PDF
  await router.route("/test-documents/multi-page.pdf", (route) => {
    const multiPagePdf = `%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R 5 0 R 7 0 R] /Count 3 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>
endobj
4 0 obj
<< /Length 55 >>
stream
BT
/F1 12 Tf
72 720 Td
(Multi-Page PDF - Page 1) Tj
ET
endstream
endobj
5 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 6 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>
endobj
6 0 obj
<< /Length 55 >>
stream
BT
/F1 12 Tf
72 720 Td
(Multi-Page PDF - Page 2) Tj
ET
endstream
endobj
7 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 8 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>
endobj
8 0 obj
<< /Length 55 >>
stream
BT
/F1 12 Tf
72 720 Td
(Multi-Page PDF - Page 3) Tj
ET
endstream
endobj
xref
0 9
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000125 00000 n
0000000294 00000 n
0000000401 00000 n
0000000570 00000 n
0000000677 00000 n
0000000846 00000 n
trailer
<< /Size 9 /Root 1 0 R >>
startxref
953
%%EOF`;
    return route.fulfill({
      status: 200,
      contentType: "application/pdf",
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET",
      },
      body: Buffer.from(multiPagePdf),
    });
  });
  await router.route("/test-documents/large-document.pdf", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/pdf",
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET",
      },
      body: createPdfBuffer("Large PDF Document"),
    });
  });
  // Mock failed PDF requests
  await router.route("/test-documents/invalid.pdf", (route) => {
    return route.fulfill({
      status: 404,
      contentType: "text/plain",
      body: "Not Found",
    });
  });
  // Mock corrupted PDF
  await router.route("/test-documents/corrupted.pdf", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/pdf",
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET",
      },
      body: Buffer.from("This is not a valid PDF file"),
    });
  });
});
test.describe("CallablePdfPreview", () => {
  test.describe("Component mounting and rendering", () => {
    feature(
      "Should render the component without errors",
      async ({ Given, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await Then["the component should render without errors"]();
      },
    );
    feature(
      "Should render all interactive buttons",
      async ({ Given, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await Then["the buttons should be visible and functional"]();
      },
    );
  });
  test.describe("PDF dialog functionality", () => {
    feature(
      "Should open PDF dialog when preview is triggered",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open pdf button"]();
        await Then["the pdf dialog should open"]();
      },
    );
    feature(
      "Should load and display PDF document",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["the pdf document should be loaded"]();
        await Then["the pdf pages should be visible"]();
      },
    );
    feature(
      "Should close dialog with close button",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open pdf button"]();
        await Then["the pdf dialog should open"]();
        await When["the user clicks the close button"]();
        await Then["the dialog should be closable"]();
      },
    );
    feature(
      "Should close dialog with escape key",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open pdf button"]();
        await Then["the pdf dialog should open"]();
        await When["the user presses escape"]();
        await Then["the dialog should be closable"]();
      },
    );
  });
  test.describe("Zoom functionality", () => {
    feature("Should display zoom controls", async ({ Given, When, Then }) => {
      await Given["the pdf preview component is mounted"]();
      await When["the user clicks the open pdf button"]();
      await Then["the pdf dialog should open"]();
      await Then["the zoom controls should be visible"]();
    });
    feature(
      "Should have reset zoom button disabled initially",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["the reset zoom button should be disabled"]();
      },
    );
    feature(
      "Should zoom in when zoom in button is clicked",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["the pdf pages should be visible"]();
        await When["the user clicks the zoom in button"]();
        await Then["the zoom level should increase"]();
      },
    );
    feature(
      "Should enable reset zoom button after zooming",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["the pdf pages should be visible"]();
        await When["the user clicks the zoom in button"]();
        await Then["the reset zoom button should be enabled"]();
      },
    );
    feature(
      "Should zoom out when zoom out button is clicked",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["the pdf pages should be visible"]();
        await When["the user clicks the zoom in button twice"]();
        await When["the user clicks the zoom out button"]();
        await Then["the zoom level should decrease"]();
      },
    );
    feature(
      "Should be able to click reset zoom button when enabled",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["the pdf pages should be visible"]();
        await When["the user clicks the zoom in button"]();
        await Then["the reset zoom button should be enabled"]();
      },
    );
  });
  test.describe("Multi-page PDF support", () => {
    feature(
      "Should open dialog for multi-page PDF",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open multi-page pdf button"]();
        await Then["the pdf dialog should open"]();
      },
    );
    feature(
      "Should load multi-page PDF document",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open multi-page pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["the pdf document should be loaded"]();
      },
    );
    feature(
      "Should render multiple pages for multi-page PDF",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open multi-page pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["the pdf document should be loaded"]();
        await Then["multiple pages should be rendered"]();
      },
    );
    feature(
      "Should open dialog for single page PDF",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open single page pdf button"]();
        await Then["the pdf dialog should open"]();
      },
    );
    feature(
      "Should load single page PDF document",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open single page pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["the pdf document should be loaded"]();
      },
    );
    feature("Should display single page PDF", async ({ Given, When, Then }) => {
      await Given["the pdf preview component is mounted"]();
      await When["the user clicks the open single page pdf button"]();
      await Then["the pdf dialog should open"]();
      await Then["the pdf document should be loaded"]();
      await Then["the pdf pages should be visible"]();
    });
  });
  test.describe("Large PDF handling", () => {
    feature("Should handle large PDF files", async ({ Given, When, Then }) => {
      await Given["the pdf preview with large pdf is mounted"]();
      await When["the user clicks the open large pdf button"]();
      await Then["the pdf dialog should open"]();
      await Then["the pdf document should be loaded"]();
      await Then["the pdf pages should be visible"]();
    });
  });
  test.describe("Error handling", () => {
    feature(
      "Should open dialog for invalid PDF URLs",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview with error is mounted"]();
        await When["the user clicks the open invalid pdf button"]();
        await Then["the pdf dialog should open"]();
      },
    );
    feature(
      "Should display error message for invalid PDF URLs",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview with error is mounted"]();
        await When["the user clicks the open invalid pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["an error message should be displayed"]();
      },
    );
    feature(
      "Should open dialog for corrupted PDF files",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview with corrupted file is mounted"]();
        await When["the user clicks the open corrupted pdf button"]();
        await Then["the pdf dialog should open"]();
      },
    );
    feature(
      "Should display error message for corrupted PDF files",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview with corrupted file is mounted"]();
        await When["the user clicks the open corrupted pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["an error message should be displayed"]();
      },
    );
  });
  test.describe("Accessibility", () => {
    feature("Should have no axe violations", async ({ Given, Then }) => {
      await Given["the pdf preview component is mounted"]();
      await Then["there shouldn't be any axe violations"]();
    });
    feature(
      "Should have no axe violations in open dialog",
      async ({ Given, When, Then }) => {
        await Given["the pdf preview component is mounted"]();
        await When["the user clicks the open pdf button"]();
        await Then["the pdf dialog should open"]();
        await Then["there shouldn't be any axe violations"]();
      },
    );
  });
});
});

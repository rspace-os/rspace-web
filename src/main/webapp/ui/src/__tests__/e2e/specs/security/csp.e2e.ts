import { expect, type Response } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";

type CapturedCspViolation = {
  blockedURI: string;
  effectiveDirective: string;
  sourceFile: string;
};

declare global {
  interface Window {
    __rspaceCspViolations?: CapturedCspViolation[];
  }
}

function isDevServerViolation(violation: CapturedCspViolation): boolean {
  return violation.effectiveDirective === "connect-src" && /^wss?:\/\/localhost:5316\//.test(violation.blockedURI);
}

test("workspace and document editor load under enforced CSP without violations", async ({ page, pageWorkspace }) => {
  const documentResponses: Response[] = [];
  page.on("response", (response) => {
    if (response.request().resourceType() === "document") {
      documentResponses.push(response);
    }
  });
  await page.addInitScript(() => {
    window.__rspaceCspViolations = [];
    document.addEventListener("securitypolicyviolation", (event) => {
      window.__rspaceCspViolations?.push({
        blockedURI: event.blockedURI,
        effectiveDirective: event.effectiveDirective,
        sourceFile: event.sourceFile,
      });
    });
  });

  await pageWorkspace.open();
  await pageWorkspace.createBasicDocument();

  const enforcedPolicies = documentResponses
    .map((response) => response.headers()["content-security-policy"])
    .filter((policy): policy is string => Boolean(policy));
  expect(enforcedPolicies.length).toBeGreaterThanOrEqual(2);
  expect(enforcedPolicies).toEqual(
    expect.arrayContaining([
      expect.stringContaining("script-src 'self' 'nonce-"),
      expect.stringContaining("style-src-elem 'self' 'nonce-"),
    ]),
  );

  await expect
    .poll(() =>
      page.evaluate(() => ({
        hasMetaNonce: Boolean(document.querySelector<HTMLMetaElement>('meta[property="csp-nonce"]')?.nonce),
        noncedScriptCount: document.querySelectorAll("script[nonce]").length,
        unnoncedStyles: Array.from(document.querySelectorAll("style:not([nonce])")).map((style) => ({
          dataEmotion: style.getAttribute("data-emotion"),
          text: style.textContent?.slice(0, 120),
        })),
      })),
    )
    .toMatchObject({ hasMetaNonce: true, unnoncedStyles: [] });
  const violations = await page.evaluate(() => window.__rspaceCspViolations ?? []);
  expect(violations.filter((violation) => !isDevServerViolation(violation))).toEqual([]);
});

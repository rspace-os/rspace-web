import type { Locator } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class OrcidProfilePage extends BasePage {
  readonly path = "/userform";

  get setOrcidIdLink(): Locator {
    return this.page.getByRole("link", { name: "Set ORCID ID" });
  }

  get connectedOrcidLink(): Locator {
    return this.page.locator("#userOrcidIdLink");
  }

  async connectOrcid(): Promise<void> {
    const [popup] = await Promise.all([this.page.waitForEvent("popup"), this.setOrcidIdLink.click()]);
    await popup.waitForEvent("close");
    await this.connectedOrcidLink.waitFor({ state: "visible" });
  }
}

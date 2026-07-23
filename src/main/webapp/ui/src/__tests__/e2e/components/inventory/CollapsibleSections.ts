import type { Locator } from "@playwright/test";

export class CollapsibleSections {
  constructor(private readonly root: Locator) {}

  section(name: string): Locator {
    return this.root.getByRole("region", { name, exact: true });
  }

  async isExpanded(name: string): Promise<boolean> {
    const section = this.section(name);
    await section.waitFor({ state: "visible" });
    return section.getByRole("button", { name: "Collapse section", exact: true }).isVisible();
  }

  async expand(name: string): Promise<void> {
    if (!(await this.isExpanded(name))) {
      await this.section(name).getByRole("button", { name: "Expand section", exact: true }).click();
    }
  }

  async collapse(name: string): Promise<void> {
    if (await this.isExpanded(name)) {
      await this.section(name).getByRole("button", { name: "Collapse section", exact: true }).click();
    }
  }
}

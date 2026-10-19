import type { Locator } from "@playwright/test";
import { BasePage } from "../BasePage";

/** `/export/report/{id}`, linked from the export-completed notification. */
export class ExportReportPage extends BasePage {
  readonly path = "/export/report";

  async openAt(href: string): Promise<void> {
    await this.page.goto(href);
    await this.page.getByRole("heading", { name: "Exported filestore links" }).waitFor();
  }

  addedToArchive(resourcePath: string): Locator {
    return this.linkCell(resourcePath, 3);
  }

  error(resourcePath: string): Locator {
    return this.linkCell(resourcePath, 4);
  }

  folderSummary(resourcePath: string): Locator {
    return this.linkCell(resourcePath, 6);
  }

  private linkCell(resourcePath: string, column: number): Locator {
    return this.page
      .getByRole("table")
      .filter({ has: this.page.getByRole("columnheader", { name: "Added to archive" }) })
      .getByRole("row")
      .filter({ has: this.page.getByRole("cell", { name: resourcePath, exact: true }) })
      .getByRole("cell")
      .nth(column);
  }
}

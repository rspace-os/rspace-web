import { type Locator, page } from "vitest/browser";

export class BookableItemPage {
  readonly heading: Locator = page.getByRole("heading", { level: 1, name: "Confocal microscope" });
  readonly globalId: Locator = page.getByText("IN123", { exact: true });
  readonly bookingsTab: Locator = page.getByRole("tab", { name: "Bookings" });
  readonly detailsTab: Locator = page.getByRole("tab", { name: "Details" });
  readonly auditTab: Locator = page.getByRole("tab", { name: "Audit log" });
  readonly accessTab: Locator = page.getByRole("tab", { name: "Access" });
  readonly bookingsPanel: Locator = page.getByRole("tabpanel", { name: "Bookings", includeHidden: true });
  readonly detailsPanel: Locator = page.getByRole("tabpanel", { name: "Details", includeHidden: true });
  readonly auditPanel: Locator = page.getByRole("tabpanel", { name: "Audit log", includeHidden: true });
  readonly accessPanel: Locator = page.getByRole("tabpanel", { name: "Access", includeHidden: true });

  get calendarTrigger(): Locator {
    return page.getByRole("button", { name: "Add to calendar" });
  }

  get calendarDialog(): Locator {
    return page.getByRole("dialog", { name: "Add to your calendar" });
  }

  get calendarUrl(): Locator {
    return page.getByRole("textbox", { name: "Or copy the calendar link below:" });
  }

  get auditFrom(): Locator {
    return page.getByLabelText("From date");
  }

  get auditTo(): Locator {
    return page.getByLabelText("To date");
  }

  get applyAuditRange(): Locator {
    return page.getByRole("button", { name: "Load audit events" });
  }

  get refreshAudit(): Locator {
    return page.getByRole("button", { name: "Refresh" });
  }

  get nextAuditPage(): Locator {
    return page.getByRole("button", { name: /^Next, page/ });
  }

  get previousAuditPage(): Locator {
    return page.getByRole("button", { name: /^Previous, page/ });
  }

  get auditResultsHeading(): Locator {
    return page.getByRole("heading", { name: "Audit events" });
  }

  get edit(): Locator {
    return page.getByRole("button", { name: "Edit configuration" });
  }

  get save(): Locator {
    return page.getByRole("button", { name: "Save changes" });
  }

  get cancel(): Locator {
    return page.getByRole("button", { name: "Cancel" });
  }

  get maximumDuration(): Locator {
    return page.getByRole("spinbutton").first();
  }

  async openEditor(): Promise<void> {
    await this.detailsTab.click();
    await this.edit.click();
  }
}

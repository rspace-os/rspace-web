import type { Locator, Page } from "@playwright/test";

export type FileSystemFormClientType = "Samba" | "SFTP" | "iRODS" | "S3";
export type FileSystemStatus = "Enabled" | "Disabled";
export type FileSystemTextField = "Name" | "URL" | "SFTP server public key" | "Samba Domain" | "Share Name";

export interface SftpFileSystemForm {
  clientType: "SFTP";
  name: string;
  url: string;
  serverPublicKey: string;
  status: FileSystemStatus;
}

export interface SambaFileSystemForm {
  clientType: "Samba";
  name: string;
  url: string;
  domain: string;
  shareName: string;
  status: FileSystemStatus;
}

export type FileSystemForm = SftpFileSystemForm | SambaFileSystemForm;

export class FileSystemFormComponent {
  readonly root: Locator;
  readonly addHeading: Locator;
  readonly detailsHeading: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    this.root = page.locator("#fileSystemDetailsForm");
    this.addHeading = this.root.getByRole("heading", { name: "Add File System:" });
    this.detailsHeading = this.root.getByRole("heading", { name: "File System Details:" });
    this.submitButton = this.root
      .getByRole("button", { name: "Add", exact: true })
      .or(this.root.getByRole("button", { name: "Update", exact: true }));
  }

  clientTypeOption(clientType: FileSystemFormClientType): Locator {
    return this.root.getByRole("radio", { name: clientType, exact: true });
  }

  statusOption(status: FileSystemStatus): Locator {
    return this.root.getByRole("radio", { name: status, exact: true });
  }

  field(name: FileSystemTextField): Locator {
    return this.root.getByRole("textbox", { name, exact: true });
  }

  /** Client type first: it decides which fields the form shows. */
  async fill(settings: FileSystemForm): Promise<void> {
    await this.clientTypeOption(settings.clientType).check();
    await this.field("Name").fill(settings.name);
    await this.field("URL").fill(settings.url);
    if (settings.clientType === "SFTP") {
      await this.field("SFTP server public key").fill(settings.serverPublicKey);
    } else {
      await this.root.getByRole("radio", { name: "SMBv2/3", exact: true }).check();
      await this.field("Samba Domain").fill(settings.domain);
      await this.field("Share Name").fill(settings.shareName);
    }
    await this.root.getByRole("radio", { name: "Username/Password", exact: true }).check();
    await this.statusOption(settings.status).check();
  }

  async setStatus(status: FileSystemStatus): Promise<void> {
    await this.statusOption(status).check();
  }

  async submit(): Promise<void> {
    await this.submitButton.click();
  }
}

import { expect } from "@playwright/test";
import type { NetFileSystemSettings } from "@/__tests__/e2e/api/clients/NetFileSystemsClient";
import type {
  FileSystemForm,
  FileSystemTextField,
} from "@/__tests__/e2e/components/system/config/FileSystemFormComponent";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";

// Nothing in these tests connects to the server, so any non-empty key is accepted.
const PLACEHOLDER_HOST_KEY = "AAAAB3NzaC1yc2EAAAADAQABAAABAQC";

const sftpUrl = (name: string) => `sftp://${name.toLowerCase()}.example.org`;

const enabledSftpFileSystem = (name: string): NetFileSystemSettings => ({
  name,
  url: sftpUrl(name),
  clientType: "SFTP",
  authType: "PASSWORD",
  clientOptions: `SFTP_SERVER_PUBLIC_KEY=${PLACEHOLDER_HOST_KEY}`,
  disabled: false,
});

interface NewFileSystem {
  form: FileSystemForm;
  /** As the file system list shows the client type. */
  listedClientType: string;
  savedFields: [FileSystemTextField, string][];
}

const NEW_FILE_SYSTEMS: { title: string; build: (name: string) => NewFileSystem }[] = [
  {
    title: "an SFTP",
    build: (name) => ({
      form: { clientType: "SFTP", name, url: sftpUrl(name), serverPublicKey: PLACEHOLDER_HOST_KEY, status: "Enabled" },
      listedClientType: "SFTP",
      savedFields: [
        ["URL", sftpUrl(name)],
        ["SFTP server public key", PLACEHOLDER_HOST_KEY],
      ],
    }),
  },
  {
    title: "a Samba",
    build: (name) => {
      const url = `smb://${name.toLowerCase()}.example.org`;
      return {
        form: { clientType: "Samba", name, url, domain: "WORKGROUP", shareName: "e2e-share", status: "Enabled" },
        listedClientType: "SMBv2/3",
        savedFields: [
          ["URL", url],
          ["Samba Domain", "WORKGROUP"],
          ["Share Name", "e2e-share"],
        ],
      };
    },
  },
];

test.describe("Institutional File Systems", { tag: tags.SYSTEM }, () => {
  for (const { title, build } of NEW_FILE_SYSTEMS) {
    test(`As a sysadmin, I can add ${title} file system, review its saved settings, and delete it`, async ({
      flowFileSystems,
    }) => {
      const { config, uniqueFileSystemName } = flowFileSystems;
      const name = uniqueFileSystemName();
      const { form, listedClientType, savedFields } = build(name);
      const fileSystems = await config.openFileSystems();

      await fileSystems.add(form);
      await expect(fileSystems.rowCells(name)).toHaveText([
        name,
        form.url,
        "true",
        listedClientType,
        "PASSWORD",
        "Details Delete",
      ]);

      const details = await fileSystems.openDetails(name);
      await expect(details.field("Name")).toHaveValue(name);
      for (const [field, value] of savedFields) {
        await expect(details.field(field)).toHaveValue(value);
      }
      await expect(details.clientTypeOption(form.clientType)).toBeChecked();
      await expect(details.statusOption("Enabled")).toBeChecked();

      await fileSystems.delete(name);
      await expect(fileSystems.row(name)).toHaveCount(0);
    });
  }

  test("As a sysadmin, I can't save a file system without a name or, for SFTP, the server's public key", async ({
    flowFileSystems,
  }) => {
    const { config, client, uniqueFileSystemName } = flowFileSystems;
    const name = uniqueFileSystemName();
    const url = sftpUrl(name);
    const fileSystems = await config.openFileSystems();
    const form = await fileSystems.openNewForm();

    await form.fill({ clientType: "SFTP", name: "", url, serverPublicKey: "", status: "Enabled" });
    await form.submit();
    await expect(form.field("Name")).toBeFocused();

    await form.field("Name").fill(name);
    await form.submit();
    await expect(form.field("SFTP server public key")).toBeFocused();
    await expect(form.addHeading).toBeVisible();

    expect((await client.list()).map((fileSystem) => fileSystem.url)).not.toContain(url);
  });

  test("As a user, I'm offered only enabled file systems when adding a filestore, so one the sysadmin disables disappears", async ({
    flowFileSystems,
    pageGallery,
  }) => {
    const { config, client, uniqueFileSystemName } = flowFileSystems;
    const stillEnabled = uniqueFileSystemName();
    const toDisable = uniqueFileSystemName();
    await client.save(enabledSftpFileSystem(stillEnabled));
    await client.save(enabledSftpFileSystem(toDisable));

    await pageGallery.open();
    const before = await pageGallery.openAddFilestoreDialog();
    await expect(before.fileSystemOption(stillEnabled)).toBeVisible();
    await expect(before.fileSystemOption(toDisable)).toBeVisible();
    await before.cancel();

    const fileSystems = await config.openFileSystems();
    const details = await fileSystems.openDetails(toDisable);
    await details.setStatus("Disabled");
    await fileSystems.saveForm();
    await expect(fileSystems.rowCells(toDisable)).toHaveText([
      toDisable,
      sftpUrl(toDisable),
      "false",
      "SFTP",
      "PASSWORD",
      "Details Delete",
    ]);

    await pageGallery.open();
    const after = await pageGallery.openAddFilestoreDialog();
    await expect(after.fileSystemOption(stillEnabled)).toBeVisible();
    await expect(after.fileSystemOption(toDisable)).toHaveCount(0);
  });
});

import { connect } from "node:net";
import { type GalleryFilestore, GalleryFilestoresClient } from "@/__tests__/e2e/api/clients/GalleryFilestoresClient";
import { type NetFileSystemSettings, NetFileSystemsClient } from "@/__tests__/e2e/api/clients/NetFileSystemsClient";
import type { FilestoreLinkDetail } from "@/__tests__/e2e/components/document/FilestoreLinkDetailsDialog";
import type { FilestoreCredentials } from "@/__tests__/e2e/components/gallery/FilestoreLoginDialog";
import { env } from "@/__tests__/e2e/env";
import { apiTest } from "@/__tests__/e2e/fixtures/api";
import { withSysadminPage } from "@/__tests__/e2e/fixtures/flows/sessions/sysadminSessions";
import { trackedUniqueNames } from "@/__tests__/e2e/testData";

export type FilestoreBackend = "s3" | "sftp" | "samba" | "irods";

/** Relative to the filestore; folders end in "/". */
type FilestoreLinkPath = `/${string}`;

interface LinkDetail {
  label: FilestoreLinkDetail;
  value: string;
}

/** The files in a filestore's folder, and what the export scenario expects of them. */
interface FilestoreDataset {
  /** Entries that must be listed when the folder is opened; the folder may hold others. */
  entries: string[];
  sampleFile: string;
  sampleFolders: string[];
  export: {
    /** Typed into the export's file-type filter. */
    excludedTypes: string;
    excludedFile: { path: FilestoreLinkPath; reason: string };
    /** `content` is checked in the archive when the file is text. */
    archivedFiles: { path: FilestoreLinkPath; content?: string }[];
    folders: { path: FilestoreLinkPath; skippedEntry: string }[];
  };
}

interface BackendConfig {
  url: string;
  /** Why the backend is skipped when it has no URL, if not the default reason. */
  skipReason?: string;
  /** Real hosts that may be unreachable from where the tests run: checked first, to fail fast. */
  remoteHost?: { host: string; port: number; hint: string };
  fileSystem: NetFileSystemSettings;
  wizardFolder: string;
  wizardFolderEntries: string[];
  seededFolderPath: string;
  dataset: FilestoreDataset;
  linkDetails: LinkDetail[];
  /** "Original path:" of a linked folder; Samba keeps the trailing slash, the others drop it. */
  linkedFolderPath: (folderName: string) => string;
  scanPath: (relativePath: FilestoreLinkPath) => string;
  credentials: FilestoreCredentials | undefined;
}

const TXT_EXCLUDED = "file extension 'txt' excluded";

// Seeded in every container by scripts/start-filestore-servers.sh.
const SEEDED: FilestoreDataset = {
  entries: ["hello.txt", "clip.flv", "sub"],
  sampleFile: "hello.txt",
  sampleFolders: ["sub"],
  export: {
    excludedTypes: "txt",
    excludedFile: { path: "/hello.txt", reason: TXT_EXCLUDED },
    archivedFiles: [{ path: "/clip.flv", content: "video" }],
    folders: [{ path: "/sub/", skippedEntry: "nested.txt" }],
  },
};

// The playwright-test folder other suites keep on the internal SFTP and Samba host. Read only.
const SHARED_PLAYWRIGHT_TEST: FilestoreDataset = {
  entries: ["test.txt", "other data", "playwright-subfolder"],
  sampleFile: "test.txt",
  sampleFolders: ["playwright-subfolder"],
  export: {
    excludedTypes: "txt",
    excludedFile: { path: "/test.txt", reason: TXT_EXCLUDED },
    archivedFiles: [],
    folders: [{ path: "/playwright-subfolder/", skippedEntry: "test3.txt" }],
  },
};

// The playwright-test folder other suites keep in the R2 test bucket. Read only.
const SHARED_R2_PLAYWRIGHT_TEST: FilestoreDataset = {
  entries: ["test1.txt", "playwright-test subfolder"],
  sampleFile: "test1.txt",
  sampleFolders: ["playwright-test subfolder"],
  export: {
    excludedTypes: "txt",
    excludedFile: { path: "/test1.txt", reason: TXT_EXCLUDED },
    archivedFiles: [],
    folders: [{ path: "/playwright-test subfolder/", skippedEntry: "test2.txt" }],
  },
};

// The playwright-test folder other suites keep in the real iRODS user's home. Read only. Other suites keep adding
// timestamped uploads to it, so only these two long-standing files are relied on; it has no subfolders.
const SHARED_IRODS_PLAYWRIGHT_TEST: FilestoreDataset = {
  entries: ["1464342543281_video.flv"],
  sampleFile: "1464342543281_video.flv",
  sampleFolders: [],
  export: {
    excludedTypes: "txt,png,jpg",
    excludedFile: { path: "/anaphase_1762430816333.jpg", reason: "file extension 'jpg' excluded" },
    archivedFiles: [{ path: "/1464342543281_video.flv" }],
    folders: [],
  },
};

const CONTAINER_LOGIN = { username: env.filestoreUsername, password: env.filestorePassword };
const CONTAINER_FOLDER = "playwright-test";

function withoutTrailingSlash(path: string): string {
  return path.endsWith("/") ? path.slice(0, -1) : path;
}

// Mock mode uses the containers from scripts/start-filestore-servers.sh. Real mode uses the real hosts: R2 and
// iRODS are public; SFTP and Samba are on an internal host that needs the VPN, so CI skips them.
function backendConfig(backend: FilestoreBackend): BackendConfig {
  const isMock = env.integrationMode === "mock";
  switch (backend) {
    case "s3":
      // RSpace's S3 credentials are global, so RSpace is started with MinIO's in mock mode and R2's in real mode.
      return isMock
        ? s3Config(
            "e2e-local-minio-s3",
            env.s3FilestoreUrl,
            "us-east-1",
            env.s3FilestoreBucket,
            CONTAINER_FOLDER,
            SEEDED,
          )
        : {
            ...s3Config(
              "e2e-real-r2-s3",
              env.realS3FilestoreUrl,
              env.realS3FilestoreRegion,
              env.realS3FilestoreBucket,
              "playwright-test",
              SHARED_R2_PLAYWRIGHT_TEST,
            ),
            remoteHost: {
              host: hostOf(env.realS3FilestoreUrl),
              port: 443,
              hint: "check the network connection to it",
            },
          };
    case "sftp":
      return isMock
        ? sftpConfig({
            name: "e2e-local-sftp",
            // Every login fails without the server's host key, so the backend counts as not set up.
            url: env.sftpFilestoreHostKey ? env.sftpFilestoreUrl : "",
            hostKey: env.sftpFilestoreHostKey,
            credentials: CONTAINER_LOGIN,
            // The SFTP container keeps the user inside their home folder, so the seed is under upload/.
            wizardFolder: "upload",
            wizardFolderEntries: [CONTAINER_FOLDER],
            seededFolderPath: `/upload/${CONTAINER_FOLDER}`,
            dataset: SEEDED,
          })
        : {
            ...sftpConfig({
              name: "e2e-real-sftp",
              url: env.realSftpUrl,
              hostKey: env.realSftpHostKey,
              credentials: { username: env.realSftpUsername, password: env.realSftpPassword },
              // RSpace roots the tree at the account's home, where playwright-test lives.
              wizardFolder: "playwright-test",
              wizardFolderEntries: SHARED_PLAYWRIGHT_TEST.entries,
              seededFolderPath: "/playwright-test",
              dataset: SHARED_PLAYWRIGHT_TEST,
            }),
            skipReason: "Real SFTP needs the VPN; set E2E_REAL_SFTP_* locally (CI can't reach the host).",
            remoteHost: { ...sftpHostAndPort(env.realSftpUrl), hint: "connect the VPN" },
          };
    case "samba":
      return isMock
        ? sambaConfig("e2e-local-samba", env.sambaFilestoreUrl, "share", CONTAINER_LOGIN, CONTAINER_FOLDER, SEEDED)
        : {
            ...sambaConfig(
              "e2e-real-samba",
              env.realSambaUrl,
              env.realSambaShare,
              { username: env.realSambaUsername, password: env.realSambaPassword },
              "playwright-test",
              SHARED_PLAYWRIGHT_TEST,
            ),
            skipReason: "Real Samba needs the VPN; set E2E_REAL_SAMBA_* locally (CI can't reach the host).",
            remoteHost: { host: hostOf(env.realSambaUrl), port: 445, hint: "connect the VPN" },
          };
    case "irods":
      return isMock
        ? irodsConfig("e2e-local-irods", env.irodsFilestoreUrl, CONTAINER_LOGIN, CONTAINER_FOLDER, SEEDED)
        : {
            ...irodsConfig(
              "e2e-real-irods",
              env.realIrodsPassword ? env.realIrodsUrl : "",
              { username: env.realIrodsUsername, password: env.realIrodsPassword },
              "playwright-test",
              SHARED_IRODS_PLAYWRIGHT_TEST,
            ),
            remoteHost: { host: env.realIrodsUrl, port: 1247, hint: "check the network connection to it" },
          };
  }
}

function sftpConfig(sftp: {
  name: string;
  url: string;
  hostKey: string;
  credentials: FilestoreCredentials;
  wizardFolder: string;
  wizardFolderEntries: string[];
  seededFolderPath: string;
  dataset: FilestoreDataset;
}): BackendConfig {
  const { seededFolderPath } = sftp;
  return {
    url: sftp.url,
    fileSystem: {
      name: sftp.name,
      url: sftp.url,
      clientType: "SFTP",
      authType: "PASSWORD",
      clientOptions: `SFTP_SERVER_PUBLIC_KEY=${sftp.hostKey}`,
      disabled: false,
    },
    wizardFolder: sftp.wizardFolder,
    wizardFolderEntries: sftp.wizardFolderEntries,
    seededFolderPath,
    dataset: sftp.dataset,
    linkDetails: [{ label: "URL:", value: sftp.url }],
    linkedFolderPath: (folderName) => `${seededFolderPath}/${folderName}`,
    scanPath: (path) => `${seededFolderPath}${path}`,
    credentials: sftp.credentials,
  };
}

function sambaConfig(
  name: string,
  url: string,
  share: string,
  credentials: FilestoreCredentials,
  folder: string,
  dataset: FilestoreDataset,
): BackendConfig {
  return {
    url,
    fileSystem: {
      name,
      url,
      clientType: "SMBJ",
      authType: "PASSWORD",
      clientOptions: `SAMBA_DOMAIN=WORKGROUP\nSAMBA_SHARE_NAME=${share}`,
      disabled: false,
    },
    wizardFolder: folder,
    wizardFolderEntries: dataset.entries,
    seededFolderPath: `/${folder}`,
    dataset,
    linkDetails: [{ label: "URL:", value: url }],
    linkedFolderPath: (folderName) => `/${folder}/${folderName}/`,
    scanPath: (path) => `/${folder}${path}`,
    credentials,
  };
}

function irodsConfig(
  name: string,
  url: string,
  credentials: FilestoreCredentials,
  folder: string,
  dataset: FilestoreDataset,
): BackendConfig {
  const homeFolder = `/tempZone/home/${credentials.username}`;
  return {
    url,
    fileSystem: {
      name,
      url,
      clientType: "IRODS",
      authType: "PASSWORD",
      clientOptions: `IRODS_ZONE=tempZone\nIRODS_HOME_DIR=${homeFolder}\nIRODS_PORT=1247\nIRODS_CSNEG=\nIRODS_AUTH=NATIVE\n`,
      disabled: false,
    },
    wizardFolder: folder,
    wizardFolderEntries: dataset.entries,
    seededFolderPath: `/${folder}`,
    dataset,
    linkDetails: [{ label: "URL:", value: url }],
    linkedFolderPath: (folderName) => `/${folder}/${folderName}`,
    // The scan shows full iRODS paths, and folders without a trailing slash.
    scanPath: (path) => `${homeFolder}/${folder}${withoutTrailingSlash(path)}`,
    credentials,
  };
}

function s3Config(
  name: string,
  url: string,
  region: string,
  bucket: string,
  folder: string,
  dataset: FilestoreDataset,
): BackendConfig {
  return {
    url,
    fileSystem: {
      name,
      url,
      clientType: "S3",
      authType: "NONE",
      clientOptions: `S3_REGION=${region}\nS3_BUCKET_NAME=${bucket}\nS3_PATH_STYLE_ACCESS_ENABLED=true`,
      disabled: false,
      readAllowlist: "*",
      writeAllowlist: "*",
    },
    wizardFolder: folder,
    wizardFolderEntries: dataset.entries,
    seededFolderPath: `/${folder}`,
    dataset,
    linkDetails: [
      { label: "URL:", value: url },
      { label: "Bucket:", value: bucket },
    ],
    linkedFolderPath: (folderName) => `/${folder}/${folderName}`,
    scanPath: (path) => `${folder}${withoutTrailingSlash(path)}`,
    credentials: undefined,
  };
}

function hostOf(url: string): string {
  return url ? new URL(url).hostname : "";
}

// The backend accepts "host", "host:port" or "sftp://host[:port]", so new URL() can't parse it.
function sftpHostAndPort(url: string): { host: string; port: number } {
  const [host, port] = url.replace("sftp://", "").split(":");
  return { host, port: port ? Number(port) : 22 };
}

async function assertReachable(backend: FilestoreBackend, host: string, port: number, hint: string): Promise<void> {
  const reachable = await new Promise<boolean>((resolve) => {
    const socket = connect({ host, port, timeout: 3_000 });
    socket.once("connect", () => {
      socket.destroy();
      resolve(true);
    });
    socket.once("timeout", () => {
      socket.destroy();
      resolve(false);
    });
    socket.once("error", () => resolve(false));
  });
  if (!reachable) {
    throw new Error(`Real ${backend} host ${host}:${port} is unreachable; ${hint}.`);
  }
}

async function ensureFileSystem(client: NetFileSystemsClient, settings: NetFileSystemSettings): Promise<number> {
  const existing = (await client.list()).find((fileSystem) => fileSystem.name === settings.name);
  return client.save(existing ? { ...settings, id: existing.id } : settings);
}

type FilestoreBackendFixtures = {
  filestoreBackend: FilestoreBackend;
  flowFilestore: Omit<BackendConfig, "url" | "fileSystem" | "skipReason" | "remoteHost"> & {
    fileSystemName: string;
    /** Teardown deletes only filestores named by this. */
    uniqueFilestoreName: () => string;
    createFilestore: (path: string) => Promise<GalleryFilestore>;
    filestoreLinksHtml: (filestore: GalleryFilestore, paths: FilestoreLinkPath[]) => string;
  };
};

function filestoreLinksHtml(nfsType: string, filestore: GalleryFilestore, paths: FilestoreLinkPath[]): string {
  const links = paths.map((path) => {
    const linkType = path.endsWith("/") ? "directory" : "file";
    const name = withoutTrailingSlash(path).split("/").at(-1);
    return (
      `<a class="nfs_file mceNonEditable" href="#" rel="${filestore.id}:${path}" ` +
      `data-linktype="${linkType}" data-nfsid="" data-nfstype="${nfsType}">${name}</a>`
    );
  });
  return `<p>${links.join(" ")}</p>`;
}

export const test = apiTest.extend<FilestoreBackendFixtures>({
  filestoreBackend: ["s3", { option: true }],

  flowFilestore: async ({ filestoreBackend, browser, browserContextOptions, apiContext, appUser }, use) => {
    const { url, fileSystem, skipReason, remoteHost, ...rest } = backendConfig(filestoreBackend);
    test.skip(
      !url,
      skipReason ?? `No ${filestoreBackend} filestore for ${env.integrationMode} mode; see the e2e README.`,
    );
    if (remoteHost) {
      await assertReachable(filestoreBackend, remoteHost.host, remoteHost.port, remoteHost.hint);
    }
    env.assertGlobalMutationsAllowed("flowFilestore");

    let fileSystemId = 0;
    await withSysadminPage(
      browser,
      browserContextOptions,
      async (page) => new NetFileSystemsClient(page.request),
      async (client) => {
        fileSystemId = await ensureFileSystem(client, fileSystem);
      },
    );

    const filestores = new GalleryFilestoresClient(apiContext, appUser.apiKey);
    // The server remembers a user's login between runs, so log out first to make the login prompt appear.
    if (rest.credentials) {
      await filestores.logoutFromFileSystem(fileSystemId);
    }
    const names = trackedUniqueNames(`e2e-${filestoreBackend}-filestore`);
    try {
      await use({
        ...rest,
        fileSystemName: fileSystem.name,
        uniqueFilestoreName: names.next,
        createFilestore: (path) => filestores.create({ fileSystemId, name: names.next(), path }),
        filestoreLinksHtml: (filestore, paths) => filestoreLinksHtml(fileSystem.clientType, filestore, paths),
      });
    } finally {
      for (const filestore of await filestores.list()) {
        if (names.created.has(filestore.name)) {
          await filestores.delete(filestore.id);
        }
      }
      if (rest.credentials) {
        await filestores.logoutFromFileSystem(fileSystemId);
      }
    }
  },
});

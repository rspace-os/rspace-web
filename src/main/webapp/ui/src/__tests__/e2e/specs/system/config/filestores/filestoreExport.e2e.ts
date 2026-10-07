import type { FilestoreBackend } from "@/__tests__/e2e/fixtures/flows/environment/filestoreBackends";
import { describeFilestoreExport } from "./filestoreExportScenario";

const BACKENDS: FilestoreBackend[] = ["s3", "sftp", "samba", "irods"];

for (const backend of BACKENDS) {
  describeFilestoreExport(backend);
}

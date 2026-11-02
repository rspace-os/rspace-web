import { appendFile, mkdir, readFile, readdir, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

export const REQUIRED_COVERAGE_JOBS = [
  "stack-verification-inputs",
  "changed-files",
  "fast-junit-tests",
  "frontend-checks",
  "backend-checks",
  "vitest-tests",
  "vitest-coverage-report",
  "browser-tests",
  "bundle-size-pr",
  "bundle-size-base",
  "bundle-size-stack-report",
  "spring-tests",
  "integration-tests",
  "chemistry-tests",
  "build",
  "mocked-e2e",
  "codeql",
];

const E2E_ARTIFACTS = [
  ...["chromium", "firefox", "webkit", "mobile"].flatMap((project) =>
    [1, 2].map((shard) => `e2e-report-mock-standard-${project}-${shard}`),
  ),
  "e2e-report-mock-standard-api-1",
  ...["chromium", "firefox", "webkit"].map(
    (project) => `e2e-report-mock-cloud-${project}-1`,
  ),
];

export const REQUIRED_COVERAGE_ARTIFACTS = [
  "fast-test-reports-17",
  "vitest-blob-report-1",
  "vitest-coverage-report",
  "browser-test-report-chromium",
  "browser-test-report-firefox",
  "browser-test-report-webkit",
  "spring-test-reports-10.11",
  "spring-test-reports-12.3",
  "integration-test-reports-10.11",
  "integration-test-reports-12.3",
  "chemistry-test-reports",
  "bundle-stats-pr",
  "bundle-stats-base",
  "bundle-comparison",
  "rspace-war",
  ...E2E_ARTIFACTS,
];

export function validateBaselineSha(sha, checkedOutSha) {
  if (typeof sha !== "string" || !/^[0-9a-f]{40}$/i.test(sha)) {
    throw new Error("bundle_baseline_sha must be a full 40-character commit SHA");
  }
  if (checkedOutSha !== undefined && checkedOutSha.toLowerCase() !== sha.toLowerCase()) {
    throw new Error("checked-out bundle baseline does not match bundle_baseline_sha");
  }
}

export async function auditCoverageJobs(needs, artifactRoot, artifactNames = REQUIRED_COVERAGE_ARTIFACTS) {
  const errors = [];
  for (const job of REQUIRED_COVERAGE_JOBS) {
    const result = needs?.[job]?.result;
    if (result !== "success") {
      errors.push(`required job ${job} finished as ${result ?? "missing"}`);
    }
  }

  for (const artifact of artifactNames) {
    if (!(await pathHasFile(path.join(artifactRoot, artifact)))) {
      errors.push(`required artifact ${artifact} is missing or empty`);
    }
  }
  return errors;
}

function statsToAssetMap(stats, label) {
  const assets = Array.isArray(stats) ? stats : stats?.assets ?? stats?.modules;
  if (!Array.isArray(assets) || assets.length === 0) {
    throw new Error(`${label} stats must contain a non-empty asset array`);
  }

  const result = new Map();
  for (const asset of assets) {
    if (
      typeof asset?.filename !== "string" ||
      asset.filename.length === 0 ||
      !Number.isFinite(asset.gzipSize) ||
      asset.gzipSize < 0
    ) {
      throw new Error(`${label} stats contain an asset without a valid filename or gzipSize`);
    }
    if (result.has(asset.filename)) {
      throw new Error(`${label} stats contain duplicate asset ${asset.filename}`);
    }
    result.set(asset.filename, asset.gzipSize);
  }
  return result;
}

export function compareBundleStats(currentStats, baselineStats) {
  const current = statsToAssetMap(currentStats, "current");
  const baseline = statsToAssetMap(baselineStats, "baseline");
  const assets = [...new Set([...current.keys(), ...baseline.keys()])]
    .sort()
    .map((filename) => {
      const baselineBytes = baseline.get(filename) ?? 0;
      const currentBytes = current.get(filename) ?? 0;
      const changeBytes = currentBytes - baselineBytes;
      return {
        filename,
        baselineBytes: baseline.has(filename) ? baselineBytes : null,
        currentBytes: current.has(filename) ? currentBytes : null,
        changeBytes,
        changePercent: baselineBytes === 0 ? null : (changeBytes / baselineBytes) * 100,
        status:
          !baseline.has(filename) ? "added" : !current.has(filename) ? "removed" : changeBytes === 0 ? "unchanged" : changeBytes > 0 ? "increased" : "reduced",
      };
    });
  const baselineTotalBytes = [...baseline.values()].reduce((total, size) => total + size, 0);
  const currentTotalBytes = [...current.values()].reduce((total, size) => total + size, 0);
  const changeBytes = currentTotalBytes - baselineTotalBytes;

  return {
    metric: "gzip bytes",
    baselineTotalBytes,
    currentTotalBytes,
    changeBytes,
    changePercent: baselineTotalBytes === 0 ? null : (changeBytes / baselineTotalBytes) * 100,
    assets,
  };
}

function formatBytes(bytes) {
  return `${bytes.toLocaleString("en-US")} B`;
}

function escapeMarkdown(value) {
  return value.replaceAll("|", "\\|").replaceAll("\n", " ");
}

export function renderBundleSummary(comparison) {
  const direction = comparison.changeBytes > 0 ? "larger" : comparison.changeBytes < 0 ? "smaller" : "unchanged";
  const changedAssets = comparison.assets.filter((asset) => asset.status !== "unchanged");
  const lines = [
    "### Bundle comparison",
    "",
    `Total gzip size: ${formatBytes(comparison.baselineTotalBytes)} → ${formatBytes(comparison.currentTotalBytes)} (${comparison.changeBytes > 0 ? "+" : ""}${formatBytes(comparison.changeBytes)}; ${direction}${comparison.changePercent === null ? "" : `, ${comparison.changePercent.toFixed(2)}%`}).`,
    "",
  ];
  if (changedAssets.length === 0) {
    lines.push("No asset sizes changed.");
  } else {
    lines.push("| Asset | Baseline gzip | Current gzip | Change |", "|---|---:|---:|---:|");
    for (const asset of changedAssets) {
      const oldSize = asset.baselineBytes === null ? "—" : formatBytes(asset.baselineBytes);
      const newSize = asset.currentBytes === null ? "—" : formatBytes(asset.currentBytes);
      lines.push(
        `| ${escapeMarkdown(asset.filename)} | ${oldSize} | ${newSize} | ${asset.changeBytes > 0 ? "+" : ""}${formatBytes(asset.changeBytes)} (${asset.status}) |`,
      );
    }
  }
  lines.push("", "Full asset-by-asset comparison: `comparison.json` (artifact).");
  return `${lines.join("\n")}\n`;
}

async function writeCoverageAudit(needs, artifactRoot) {
  const errors = await auditCoverageJobs(needs, artifactRoot);

  const summary = [
    "### Aggregate CI coverage audit",
    "",
    errors.length === 0
      ? `All ${REQUIRED_COVERAGE_JOBS.length} required jobs and ${REQUIRED_COVERAGE_ARTIFACTS.length} artifacts are present.`
      : `Coverage audit failed with ${errors.length} missing or unsuccessful requirement(s):`,
    ...(errors.length ? errors.map((error) => `- ${error}`) : []),
    "",
  ].join("\n");
  if (process.env.GITHUB_STEP_SUMMARY) {
    await appendFile(process.env.GITHUB_STEP_SUMMARY, summary);
  }
  console.log(summary);
  if (errors.length) process.exitCode = 1;
}

async function pathHasFile(directory) {
  try {
    const entries = await readdir(directory, { withFileTypes: true });
    for (const entry of entries) {
      const child = path.join(directory, entry.name);
      if (entry.isFile() && (await stat(child)).size > 0) return true;
      if (entry.isDirectory() && (await pathHasFile(child))) return true;
    }
  } catch {
    return false;
  }
  return false;
}

async function run() {
  const [command, ...args] = process.argv.slice(2);
  if (command === "validate-baseline") {
    const [mode, sha] = args;
    if (mode !== "true") {
      console.log("Stack verification is disabled; no bundle baseline is required.");
      return;
    }
    validateBaselineSha(sha);
    console.log(`Validated full bundle baseline SHA ${sha}.`);
    return;
  }
  if (command === "audit") {
    const [artifactRoot] = args;
    await writeCoverageAudit(JSON.parse(process.env.NEEDS_JSON ?? "null"), artifactRoot);
    return;
  }
  if (command === "compare") {
    const [currentFile, baselineFile, outputDirectory] = args;
    if (!currentFile || !baselineFile || !outputDirectory) {
      throw new Error("compare requires current stats, baseline stats, and an output directory");
    }
    const [current, baseline] = await Promise.all([
      readFile(currentFile, "utf8").then(JSON.parse),
      readFile(baselineFile, "utf8").then(JSON.parse),
    ]);
    const comparison = compareBundleStats(current, baseline);
    const markdown = renderBundleSummary(comparison);
    await mkdir(outputDirectory, { recursive: true });
    await Promise.all([
      writeFile(path.join(outputDirectory, "comparison.json"), `${JSON.stringify(comparison, null, 2)}\n`),
      writeFile(path.join(outputDirectory, "comparison.md"), markdown),
      process.env.GITHUB_STEP_SUMMARY
        ? appendFile(process.env.GITHUB_STEP_SUMMARY, markdown)
        : Promise.resolve(),
    ]);
    console.log(markdown);
    return;
  }
  throw new Error("usage: aggregate-ci.mjs <validate-baseline|audit|compare> ...");
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  run().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}

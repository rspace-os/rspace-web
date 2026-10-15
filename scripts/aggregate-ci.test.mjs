import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
  auditCoverageJobs,
  compareBundleStats,
  renderBundleSummary,
  REQUIRED_COVERAGE_ARTIFACTS,
  REQUIRED_COVERAGE_JOBS,
  validateBaselineSha,
} from "./aggregate-ci.mjs";

const workflow = readFileSync(new URL("../.github/workflows/lint-and-test.yml", import.meta.url), "utf8");
const codeqlWorkflow = readFileSync(new URL("../.github/workflows/codeql.yml", import.meta.url), "utf8");
const e2eWorkflow = readFileSync(new URL("../.github/workflows/e2e.yml", import.meta.url), "utf8");

function jobBlock(source, jobId) {
  const jobsStart = source.indexOf("\njobs:\n");
  const start = source.indexOf(`\n  ${jobId}:\n`, jobsStart);
  assert.notEqual(start, -1, `expected workflow job ${jobId}`);
  const rest = source.slice(start + 1);
  const nextJob = rest.slice(1).search(/\n  [a-z0-9_-]+:\n/);
  return nextJob === -1 ? rest : rest.slice(0, nextJob + 1);
}

function assertRepoCheckoutBefore(block, markers, job) {
  const checkoutIndex = block.indexOf("uses: actions/checkout@v7.0.0");
  const credentialIndex = block.indexOf("persist-credentials: false", checkoutIndex);
  assert.ok(checkoutIndex >= 0, `${job} must checkout the repository`);
  assert.ok(credentialIndex > checkoutIndex, `${job} must disable checkout credentials`);
  for (const marker of markers) {
    assert.ok(block.indexOf(marker) > credentialIndex, `${job} must checkout before ${marker}`);
  }
}

function analyzerStat(filename, parsedSize, gzipSize, brotliSize) {
  return {
    filename,
    label: filename,
    parsedSize,
    mapSize: parsedSize * 2,
    gzipSize,
    brotliSize,
    source: [
      {
        parsedSize,
        gzipSize,
        brotliSize,
        importedBy: [],
        label: `src/${filename}.tsx`,
        filename: `src/${filename}.tsx`,
      },
    ],
    isAsset: true,
    isEntry: false,
    imports: [],
  };
}

test("stack mode requires a full pinned baseline SHA and an exact checkout", () => {
  const sha = "4dbb3399f40b95618a82a7d646e05449b4ad4941";
  assert.doesNotThrow(() => validateBaselineSha(sha));
  assert.doesNotThrow(() => validateBaselineSha(sha, sha));
  assert.throws(() => validateBaselineSha(""), /full 40-character/);
  assert.throws(() => validateBaselineSha(sha.slice(0, 39)), /full 40-character/);
  assert.throws(() => validateBaselineSha(`${sha.slice(0, 39)}z`), /full 40-character/);
  assert.throws(() => validateBaselineSha(sha, "0".repeat(40)), /does not match/);

  assert.match(workflow, /stack_verification:[\s\S]*?default: false/);
  assert.match(workflow, /bundle_baseline_sha:[\s\S]*?default: ''/);
  assert.match(workflow, /ref: \$\{\{ inputs\.stack_verification && inputs\.bundle_baseline_sha \|\| github\.base_ref \}\}/);
  assert.match(workflow, /git cat-file -e "\$\{BUNDLE_BASELINE_SHA\}\^\{commit\}"/);
});

test("stack mode forces every existing coverage gate", () => {
  for (const job of [
    "fast-junit-tests",
    "frontend-checks",
    "backend-checks",
    "vitest-tests",
    "vitest-coverage-report",
    "browser-tests",
    "bundle-size-pr",
    "bundle-size-base",
    "spring-tests",
    "integration-tests",
    "chemistry-tests",
    "build",
  ]) {
    assert.match(jobBlock(workflow, job), /inputs\.stack_verification/, `${job} must run in stack mode`);
  }

  for (const [job, artifact] of [
    ["fast-junit-tests", "fast-test-reports-${{ matrix.java-version }}"],
    ["vitest-tests", "vitest-blob-report-${{ matrix.shard }}"],
    ["browser-tests", "browser-test-report-${{ matrix.browser }}"],
    ["spring-tests", "spring-test-reports-${{ matrix.mariadb-version }}"],
    ["integration-tests", "integration-test-reports-${{ matrix.mariadb-version }}"],
    ["chemistry-tests", "chemistry-test-reports"],
  ]) {
    assert.ok(jobBlock(workflow, job).includes(`name: ${artifact}`), `${job} must upload ${artifact}`);
  }

  const audit = jobBlock(workflow, "coverage-audit");
  for (const job of REQUIRED_COVERAGE_JOBS) assert.ok(audit.includes(job), `coverage audit must depend on ${job}`);
  assert.match(audit, /if: \$\{\{ always\(\) && inputs\.stack_verification \}\}/);
});

test("aggregate calls keep E2E and CodeQL inside the caller run", () => {
  const e2eCall = jobBlock(workflow, "mocked-e2e");
  assert.match(e2eCall, /uses: \.\/\.github\/workflows\/e2e\.yml/);
  assert.match(e2eCall, /mode: mock/);
  assert.doesNotMatch(e2eCall, /secrets:/);

  const codeqlCall = jobBlock(workflow, "codeql");
  assert.match(codeqlCall, /uses: \.\/\.github\/workflows\/codeql\.yml/);
  assert.match(codeqlCall, /contents: read[\s\S]*actions: read[\s\S]*security-events: write/);
  assert.match(codeqlWorkflow, /workflow_call:/);
  assert.match(codeqlWorkflow, /group: codeql-\$\{\{ github\.workflow/);
});

test("manual bundle comparison is pinned, complete, and has no comment action", () => {
  const manualReport = jobBlock(workflow, "bundle-size-stack-report");
  assert.match(manualReport, /node scripts\/aggregate-ci\.mjs compare/);
  assert.match(manualReport, /contents: read/);
  assert.doesNotMatch(manualReport, /pull-requests: write|vite-compare-bundle-size/);

  const commentJob = jobBlock(workflow, "bundle-size-report");
  assert.match(commentJob, /if:.*!inputs\.stack_verification/);
  assert.match(commentJob, /uses: wojtekmaj\/vite-compare-bundle-size@v1\.2\.1/);

  const matrixArtifacts = REQUIRED_COVERAGE_ARTIFACTS.filter((name) => name.startsWith("e2e-report-mock-"));
  assert.deepEqual(matrixArtifacts, [
    "e2e-report-mock-standard-chromium-1",
    "e2e-report-mock-standard-chromium-2",
    "e2e-report-mock-standard-firefox-1",
    "e2e-report-mock-standard-firefox-2",
    "e2e-report-mock-standard-webkit-1",
    "e2e-report-mock-standard-webkit-2",
    "e2e-report-mock-standard-mobile-1",
    "e2e-report-mock-standard-mobile-2",
    "e2e-report-mock-standard-api-1",
    "e2e-report-mock-cloud-chromium-1",
    "e2e-report-mock-cloud-firefox-1",
    "e2e-report-mock-cloud-webkit-1",
  ]);
  const e2eMatrix = jobBlock(e2eWorkflow, "e2e");
  assert.match(e2eMatrix, /project: \[chromium, firefox, webkit, mobile\]/);
  assert.match(e2eMatrix, /shard: \[1, 2\]/);
  assert.match(e2eMatrix, /cloud: \[false\]/);
  assert.match(e2eMatrix, /project: api\s+shard: 1\s+cloud: false/);
  for (const project of ["chromium", "firefox", "webkit"]) {
    assert.match(e2eMatrix, new RegExp(`project: ${project}\\s+shard: 1\\s+cloud: true`));
  }
  assert.doesNotMatch(e2eMatrix, /project: community/);
  assert.match(e2eWorkflow, /name: rspace-war/);
  assert.ok(REQUIRED_COVERAGE_JOBS.includes("build"));
  assert.ok(REQUIRED_COVERAGE_ARTIFACTS.includes("rspace-war"));
});

test("helper jobs checkout the repository without persisted credentials first", () => {
  assertRepoCheckoutBefore(
    jobBlock(workflow, "bundle-size-stack-report"),
    ["uses: actions/download-artifact@v8.0.1", "run: node scripts/aggregate-ci.mjs compare"],
    "bundle-size-stack-report",
  );
  assertRepoCheckoutBefore(
    jobBlock(workflow, "coverage-audit"),
    ["uses: actions/download-artifact@v8.0.1", "run: node scripts/aggregate-ci.mjs audit"],
    "coverage-audit",
  );
});

test("bundle comparison preserves gzip sizes and reports added, removed, and changed assets", () => {
  const comparison = compareBundleStats(
    [
      analyzerStat("assets/app.js", 300, 140, 120),
      analyzerStat("assets/new.js", 100, 30, 24),
      analyzerStat("assets/same.css", 20, 10, 8),
    ],
    [
      analyzerStat("assets/app.js", 220, 100, 82),
      analyzerStat("assets/old.js", 60, 20, 16),
      analyzerStat("assets/same.css", 20, 10, 8),
    ],
  );

  assert.equal(comparison.baselineTotalBytes, 130);
  assert.equal(comparison.currentTotalBytes, 180);
  assert.equal(comparison.changeBytes, 50);
  assert.deepEqual(comparison.assets.map(({ filename, status }) => [filename, status]), [
    ["assets/app.js", "increased"],
    ["assets/new.js", "added"],
    ["assets/old.js", "removed"],
    ["assets/same.css", "unchanged"],
  ]);
  const summary = renderBundleSummary(comparison);
  assert.match(summary, /Total gzip size/);
  assert.match(summary, /assets\/new\.js/);
  assert.doesNotMatch(summary, /assets\/same\.css/);
  assert.throws(() => compareBundleStats([], [analyzerStat("asset.js", 10, 5, 4)]), /non-empty asset array/);
});

test("coverage audit rejects missing, skipped, failed, cancelled jobs and empty artifacts", async () => {
  const artifactRoot = mkdtempSync(path.join(os.tmpdir(), "rspace-aggregate-ci-"));
  try {
    for (const artifact of REQUIRED_COVERAGE_ARTIFACTS) {
      const directory = path.join(artifactRoot, artifact);
      mkdirSync(directory, { recursive: true });
      writeFileSync(path.join(directory, "report.txt"), "present");
    }
    const allSuccess = Object.fromEntries(REQUIRED_COVERAGE_JOBS.map((job) => [job, { result: "success" }]));
    assert.deepEqual(await auditCoverageJobs(allSuccess, artifactRoot), []);

    const incomplete = {
      ...allSuccess,
      "mocked-e2e": { result: "skipped" },
      codeql: { result: "failure" },
      build: { result: "cancelled" },
    };
    delete incomplete["fast-junit-tests"];
    rmSync(path.join(artifactRoot, "rspace-war"), { recursive: true });
    rmSync(path.join(artifactRoot, "vitest-coverage-report"), { recursive: true });
    mkdirSync(path.join(artifactRoot, "vitest-coverage-report"));
    writeFileSync(path.join(artifactRoot, "vitest-coverage-report", "empty.txt"), "");
    const errors = await auditCoverageJobs(incomplete, artifactRoot);
    assert.ok(errors.some((error) => error.includes("fast-junit-tests") && error.includes("missing")));
    assert.ok(errors.some((error) => error.includes("mocked-e2e") && error.includes("skipped")));
    assert.ok(errors.some((error) => error.includes("codeql") && error.includes("failure")));
    assert.ok(errors.some((error) => error.includes("build") && error.includes("cancelled")));
    assert.ok(errors.some((error) => error.includes("rspace-war") && error.includes("missing or empty")));
    assert.ok(errors.some((error) => error.includes("vitest-coverage-report") && error.includes("missing or empty")));
  } finally {
    rmSync(artifactRoot, { recursive: true, force: true });
  }
});

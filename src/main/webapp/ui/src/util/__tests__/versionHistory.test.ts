import { describe, expect, test } from "vitest";
import { groupByVersion } from "../versionHistory";

const revision = (revisionId: number, version: number | null, label?: string) => ({
  revisionId,
  label,
  record: { version },
});

describe("groupByVersion", () => {
  test("returns one entry per version, newest version first", () => {
    const grouped = groupByVersion([revision(10, 1), revision(20, 2), revision(30, 3)]);

    expect(grouped.map((g) => g.version)).toEqual([3, 2, 1]);
  });

  test("collapses several revisions of one version into a single entry", () => {
    // a non-version-bumping edit adds a revision without bumping the counter
    const grouped = groupByVersion([revision(10, 1), revision(20, 1), revision(30, 2)]);

    expect(grouped.map((g) => g.version)).toEqual([2, 1]);
  });

  test("the newest revision of a version wins, so the version's final state is shown", () => {
    const grouped = groupByVersion([revision(10, 1, "first"), revision(20, 1, "last")]);

    expect(grouped).toHaveLength(1);
    expect(grouped[0].revision.label).toBe("last");
  });

  test("the newest revision still wins when the response is not ordered by revisionId", () => {
    const grouped = groupByVersion([revision(20, 1, "last"), revision(10, 1, "first")]);

    expect(grouped[0].revision.label).toBe("last");
  });

  test("returns the whole winning revision, so callers can read its revisionId", () => {
    // Aspose conversion addresses a revision, not a version, so this must survive grouping
    const grouped = groupByVersion([revision(10, 1), revision(99, 2)]);

    expect(grouped[0].revision.revisionId).toBe(99);
    expect(grouped[1].revision.revisionId).toBe(10);
  });

  test("skips revisions carrying no version, as there is nothing to label them with", () => {
    const grouped = groupByVersion([revision(10, null), revision(20, 1)]);

    expect(grouped.map((g) => g.version)).toEqual([1]);
  });

  test("an empty revisions list groups to nothing", () => {
    expect(groupByVersion([])).toEqual([]);
  });

  test("does not mutate the array it is given", () => {
    const revisions = [revision(30, 3), revision(10, 1)];

    groupByVersion(revisions);

    expect(revisions.map((r) => r.revisionId)).toEqual([30, 10]);
  });
});

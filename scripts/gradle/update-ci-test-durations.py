#!/usr/bin/env python3
"""Refresh class-duration estimates from full Gradle JUnit XML report directories."""

import argparse
import json
from collections import defaultdict
from pathlib import Path
from statistics import median
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", nargs="+", type=Path)
    parser.add_argument("--output", type=Path, default=Path("gradle/ci-test-durations.json"))
    args = parser.parse_args()
    samples = defaultdict(list)
    for directory in args.reports:
        totals = defaultdict(float)
        paths = sorted(directory.rglob("TEST-*.xml"))
        if not paths:
            parser.error(f"No JUnit XML reports in {directory}")
        for path in paths:
            suite = ET.parse(path).getroot()
            totals[suite.attrib["name"].split("$")[0]] += float(suite.attrib.get("time", "0"))
        for name, seconds in totals.items():
            samples[name].append(seconds)
    args.output.write_text(json.dumps({name: round(median(times), 3) for name, times in sorted(samples.items())}, indent=2) + "\n")
    print(f"Wrote {len(samples)} class estimates to {args.output}")


if __name__ == "__main__":
    main()

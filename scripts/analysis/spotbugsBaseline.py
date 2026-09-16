#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 XeniaCloud
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Write the committed Spotbugs baseline from a local gplayDebug report.

Keeps only the findings and category names that spotbugsSummary.py counts, dropping
the project block (absolute local paths) and the per-class statistics.

Usage: ./gradlew spotbugsGplayDebug && scripts/analysis/spotbugsBaseline.py
"""
import argparse

import defusedxml.ElementTree as ET

KEPT_TAGS = {"BugInstance", "BugCategory"}

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", help="Spotbugs report to read", default="app/build/reports/spotbugs/gplayDebug.xml")
    parser.add_argument("--output", help="baseline to write", default="scripts/analysis/spotbugs-baseline.xml")
    args = parser.parse_args()

    tree = ET.parse(args.file)
    root = tree.getroot()
    for child in list(root):
        if child.tag not in KEPT_TAGS:
            root.remove(child)
    tree.write(args.output, encoding="UTF-8", xml_declaration=True)

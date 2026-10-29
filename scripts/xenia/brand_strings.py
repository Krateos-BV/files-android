#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 XeniaCloud
# SPDX-License-Identifier: GPL-3.0-or-later
"""Keep the upstream brand out of user-visible Android string resources.

--check  fails when a string, plural or string-array item mentions the brand
         and its name is not in brand-allowlist.txt, or when a translation of a
         key in brand-inherit.txt exists.
--merged DIR  runs the same check on a Gradle merged-resources directory, which
         also holds the strings that libraries bring in.
--apply  rewrites the brand in translatable strings with the replacement map and
         deletes translations of brand-inherit.txt keys, so every locale shows
         the base value. Run it after an upstream sync brings translations back.
"""
import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ALLOWLIST = pathlib.Path(__file__).with_name("brand-allowlist.txt")
INHERIT = pathlib.Path(__file__).with_name("brand-inherit.txt")
SKIPPED_SOURCE_SETS = {"test", "androidTest", "dev", "qa", "versionDev"}

REPLACEMENTS = [
    ("https://nextcloud.com", "https://xeniacloud.eu"),
    ("Nextcloud Talk", "Xenia Talk"),
    ("Nextcloud Files", "Xenia Files"),
    ("Nextcloud Notes", "Xenia Notes"),
    ("Nextcloud Assistant", "Xenia Assistant"),
    ("Nextcloud", "XeniaCloud"),
]
TRANSLITERATIONS = re.compile(r"некст\s?клауд[а-яђјљњћџ]*|nekst\s?klaud|nexcloud|نكست كلاود|نکست\u200cکلود", re.I)
OTHER_SPELLINGS = re.compile(r"(?<![./@\w])nextcloud", re.I)

STRING = re.compile(r"(<string\b([^>]*)(?<!/)>)(.*?)(</string>)", re.S)
GROUP = re.compile(r"(<(plurals|string-array)\b([^>]*)>)(.*?)(</\2>)", re.S)
ITEM = re.compile(r"(<item\b[^>]*>)(.*?)(</item>)", re.S)
COMMENT = re.compile(r"<!--.*?-->", re.S)
NAME = re.compile(r'\bname="([^"]+)"')
BRAND = re.compile("nextcloud|" + TRANSLITERATIONS.pattern, re.I)


def resource_files():
    for path in sorted(ROOT.glob("app/src/*/res/values*/*.xml")):
        if path.parts[path.parts.index("src") + 1] not in SKIPPED_SOURCE_SETS:
            yield path


def merged_resource_files(merged_dir):
    files = sorted(pathlib.Path(merged_dir).resolve().glob("values*/values*.xml"))
    if not files:
        sys.exit(f"no merged values files in {merged_dir}; did the Gradle build run?")
    return files


def shown(path):
    return path.relative_to(ROOT) if path.is_relative_to(ROOT) else path


def names_in(listing):
    lines = listing.read_text(encoding="utf-8").splitlines()
    return {line.split("#")[0].strip() for line in lines if line.split("#")[0].strip()}


def is_translation(path):
    return path.parent.name.startswith("values-")


def inherited_string(name):
    return re.compile(r'[ \t]*<string name="' + re.escape(name) + r'"[^>]*>.*?</string>[ \t]*\n', re.S)


def is_translatable(attributes):
    return 'translatable="false"' not in attributes


def rebrand(text):
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    text = TRANSLITERATIONS.sub("XeniaCloud", text)
    return OTHER_SPELLINGS.sub("XeniaCloud", text)


def name_of(attributes):
    match = NAME.search(attributes)
    return match.group(1) if match else "?"


def strip_comments_keeping_offsets(text):
    return COMMENT.sub(lambda m: " " * len(m.group(0)), text)


def findings(path, allowlist, inherited):
    text = strip_comments_keeping_offsets(path.read_text(encoding="utf-8"))
    if is_translation(path):
        for name in sorted(inherited):
            if inherited_string(name).search(text):
                yield f"{name} (translation of a brand-inherit key)"
    for match in STRING.finditer(text):
        name = name_of(match.group(2))
        if name not in allowlist and BRAND.search(match.group(3)):
            yield name
    for group in GROUP.finditer(text):
        name = name_of(group.group(3))
        if name not in allowlist and any(BRAND.search(item.group(2)) for item in ITEM.finditer(group.group(4))):
            yield name


def without_inherited_translations(path, text, inherited):
    if not is_translation(path):
        return text
    for name in inherited:
        text = inherited_string(name).sub("", text)
    return text


def apply(path, allowlist, inherited):
    original = path.read_text(encoding="utf-8")
    updated = without_inherited_translations(path, original, inherited)
    masked = strip_comments_keeping_offsets(updated)
    edits = []
    for match in STRING.finditer(masked):
        if is_translatable(match.group(2)) and name_of(match.group(2)) not in allowlist:
            edits.append((match.start(3), match.end(3)))
    for group in GROUP.finditer(masked):
        if is_translatable(group.group(3)) and name_of(group.group(3)) not in allowlist:
            edits.append((group.start(4), group.end(4)))
    for start, end in sorted(edits, reverse=True):
        updated = updated[:start] + rebrand(updated[start:end]) + updated[end:]
    if updated != original:
        path.write_text(updated, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--apply", action="store_true")
    mode.add_argument("--merged", metavar="DIR")
    args = parser.parse_args()
    allowlist = names_in(ALLOWLIST)
    inherited = names_in(INHERIT)

    if args.apply:
        for path in resource_files():
            apply(path, allowlist, inherited)
        return 0

    paths = merged_resource_files(args.merged) if args.merged else resource_files()
    problems = [(path, name) for path in paths for name in findings(path, allowlist, inherited)]
    for path, name in problems:
        print(f"::error file={shown(path)}::string '{name}' mentions Nextcloud")
    if problems:
        print(f"{len(problems)} user-visible Nextcloud mention(s). Run scripts/xenia/brand_strings.py --apply, "
              "override a library string in res/values*/xenia_library_strings.xml, "
              "or add the key to scripts/xenia/brand-allowlist.txt with a reason.")
        return 1
    print("no user-visible Nextcloud mentions")
    return 0


if __name__ == "__main__":
    sys.exit(main())

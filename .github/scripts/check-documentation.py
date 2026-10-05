#!/usr/bin/env python3
"""Require adjacent KDoc on production Kotlin types and callable API declarations.

This is a small source-layout guard, not a Kotlin parser: it covers line-leading
class/object/enum/function declarations used in this repository. Private members,
Android overrides, local functions, anonymous objects and companion containers are excluded.
It does not validate link resolution or prose accuracy; review still owns those.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCES = [ROOT / f"app/src/{name}/java" for name in ("main", "googleHome", "noGoogleHome")]
DECLARATION = re.compile(
    r"^\s*(?P<modifiers>(?:(?:public|internal|private|protected|override|open|final|"
    r"abstract|sealed|inline|suspend|tailrec|operator|infix|external|expect|actual)\s+)*)"
    r"(?:data\s+class|enum\s+class|annotation\s+class|class|object|fun)\s+"
    r"(?:<[^>]+>\s*)?(?P<name>[A-Za-z_][A-Za-z0-9_.]*)"
)


def undocumented_declarations(source):
    """Yield one-based declaration lines lacking adjacent documentation.

    Single-line annotations can occur between KDoc and the declaration. If new
    syntax such as multiline annotations is introduced, extend this guard/test
    instead of treating its result as an authoritative Kotlin AST analysis.
    """
    lines = source.splitlines()
    companion_indent = None
    for index, line in enumerate(lines):
        indent = len(line) - len(line.lstrip())
        if line.strip().startswith("companion object"):
            companion_indent = indent
        elif companion_indent is not None and indent <= companion_indent and line.strip().startswith("}"):
            companion_indent = None
        match = DECLARATION.match(line)
        if not match or {"private", "protected", "override"} & set(match["modifiers"].split()):
            continue
        # Production types are top-level; API members use four-space indentation,
        # with one extra level inside companion objects. Deeper functions are local.
        if indent > 4 and not (companion_indent is not None and indent == companion_indent + 4):
            continue
        previous = index - 1
        while previous >= 0 and (
            not lines[previous].strip() or lines[previous].lstrip().startswith("@")
        ):
            previous -= 1
        if previous < 0 or not lines[previous].rstrip().endswith("*/"):
            yield index + 1, match["name"]
            continue
        start = previous
        while start >= 0 and "/*" not in lines[start]:
            start -= 1
        if start < 0 or "/**" not in lines[start]:
            yield index + 1, match["name"]


def self_test():
    """Check positives and exclusions so the guard cannot silently accept ordinary comments."""
    assert list(undocumented_declarations("class Missing\ninternal fun absent() = Unit")) == [
        (1, "Missing"), (2, "absent")
    ]
    assert not list(undocumented_declarations(
        "/** Type contract. */\nclass Documented\n/** Function contract. */\n"
        "@JvmStatic\ninternal inline fun <T> documented() = Unit\n"
        "private fun helper() = Unit\noverride fun onCreate() = Unit\ncompanion object {"
    ))
    assert list(undocumented_declarations("/* Not KDoc. */\nobject Missing")) == [(2, "Missing")]
    assert not list(undocumented_declarations("        fun localHelper() = Unit"))
    assert list(undocumented_declarations(
        "    companion object {\n        fun missingMember() = Unit\n    }"
    )) == [(2, "missingMember")]


def main():
    """Check every production Kotlin file and report filenames/lines, never source contents."""
    self_test()
    files = sorted(path for source in SOURCES for path in source.rglob("*.kt"))
    if not files:
        print("Documentation check failed: no production Kotlin sources found.", file=sys.stderr)
        return 1
    findings = []
    for path in files:
        findings.extend(
            f"{path.relative_to(ROOT).as_posix()}:{line}: missing KDoc for {name}"
            for line, name in undocumented_declarations(path.read_text(encoding="utf-8"))
        )
    if findings:
        print("\n".join(findings), file=sys.stderr)
        return 1
    print(f"Documentation check passed: {len(files)} production Kotlin files checked.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

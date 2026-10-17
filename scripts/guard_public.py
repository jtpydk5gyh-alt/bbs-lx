#!/usr/bin/env python3
"""Guard the public surface of the repository.

The repository is public. Anything written into a committed file or into a pull
request description stays published permanently and stays retrievable from the
git history, even if it is deleted afterwards.

This guard is the cheap, mechanical half of that rule. It blocks the mistakes
that are impossible to make on purpose but very easy to make by accident:
a path from the author's machine, a personal email address, a token, or a local
working directory being committed by accident.

It is not a substitute for judgement.

Usage:
    python scripts/guard_public.py            # staged content, for a pre-commit hook
    python scripts/guard_public.py --all      # every tracked file
    python scripts/guard_public.py --pr-body FILE   # a pull request description
    python scripts/guard_public.py --install  # register it as the pre-commit hook

Exit codes: 0 clean (warnings allowed), 1 blocked.
"""

import argparse
import os
import re
import subprocess
import sys

# --- what is blocked -------------------------------------------------------

# Anything that identifies the machine the code was written on.
MACHINE_PATHS = re.compile(
    r"(?:[A-Za-z]:[\\/](?:Users|Documentos|home)[\\/][^\\/\s\"']+)"
    r"|(?:/(?:home|Users)/[^/\s\"']+)",
    re.IGNORECASE,
)

# Anything that identifies a person rather than the public owner.
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
ALLOWED_EMAIL_HOSTS = (
    "users.noreply.github.com",
    "example.com",
    "example.invalid",
    "example.org",
)

# Credentials of any kind.
SECRETS = re.compile(
    r"(?:gh[pousr]_[A-Za-z0-9]{16,})"
    r"|(?:sk-[A-Za-z0-9]{20,})"
    r"|(?:AKIA[0-9A-Z]{16})"
    r"|(?:-----BEGIN [A-Z ]*PRIVATE KEY-----)"
    r"|(?:(?i:api[_-]?key|secret|password|token)\s*[:=]\s*[\"'][^\"'\s]{12,}[\"'])"
)

# Working directories that must never end up in a commit. .gitignore has to
# name them in order to exclude them, which is why it is exempt below.
INTERNAL_REFERENCES = re.compile(
    r"(?:_local/)"
)

# Worth knowing about, but not worth blocking a commit over.
# The lookbehind rejects "User-Agent" (an HTTP header); the lookahead rejects a
# filename spelled after the rule itself.
INTERNAL_VOCABULARY = re.compile(
    r"(?<![\w-])(?:agents?|agentes?)(?![\w-])(?!\.md)", re.IGNORECASE
)

# Files allowed to use the vocabulary above: the guard itself, which has to
# name every token it forbids.
VOCABULARY_EXEMPT = ("scripts/guard_public.py",)

# Rules that a file has to break in order to state them.
#   .gitignore              has to name the very patterns that are excluded
#   scripts/guard_public.py is the rule itself, so it necessarily contains
#                            every token it forbids
INTERNAL_REFERENCE_EXEMPT = (".gitignore", ".guardignore", "scripts/guard_public.py")
MACHINE_PATH_EXEMPT = ("scripts/guard_public.py",)

# Inline escape hatch: a line containing this marker excuses the whole file
# from the internal-reference rule. It must carry a reason.
INLINE_ALLOW_MARKER = re.compile(r"guard-allow\s*:", re.IGNORECASE)

# Never read as text: large, binary, or generated.
SKIP_SUFFIXES = (
    ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".jar", ".zip", ".class",
    ".bbmodel", ".nbt", ".dat", ".bin", ".woff", ".woff2", ".ttf", ".ogg",
)
SKIP_DIRS = (".git", "build", ".gradle", "run", "tools", "jdk", "gradle-portable", "_local")

# Game assets: translation strings, textures, models. They are data the game
# reads at runtime, not documentation. "Terreno" is the Spanish translation of
# the terrain setting, and blocking it would be wrong.
SKIP_PATH_PARTS = ("/resources/assets/",)

# Text we do not police even though they are text.
SKIP_PATHS = ("LICENSE.md", ".gitattributes")

MAX_BYTES = 2_000_000


def iter_staged():
    """Files about to be committed, plus deleted ones flagged separately."""
    out = subprocess.run(
        ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR"],
        capture_output=True, text=True, check=False,
    )
    for path in out.stdout.splitlines():
        if path.strip():
            yield path.strip()


def iter_tracked():
    out = subprocess.run(
        ["git", "ls-files"], capture_output=True, text=True, check=False,
    )
    for path in out.stdout.splitlines():
        if path.strip():
            yield path.strip()


def read_staged(path):
    out = subprocess.run(
        ["git", "show", ":" + path], capture_output=True, check=False,
    )
    if out.returncode != 0:
        return None
    try:
        return out.stdout.decode("utf-8")
    except UnicodeDecodeError:
        return None


def should_skip(path):
    normalized = path.replace("\\", "/")
    if any(normalized.startswith(d + "/") or normalized == d for d in SKIP_DIRS):
        return True
    if normalized.endswith(SKIP_SUFFIXES) or normalized in SKIP_PATHS:
        return True
    if any(part in "/" + normalized for part in SKIP_PATH_PARTS):
        return True
    return os.path.basename(normalized) == normalized and normalized.startswith("nul")


def load_guardignore():
    """Paths the owner has decided the guard must not read, one per line.
    A comment or a blank line is ignored. There is no such file yet: it exists
    so that a false positive has an answer that does not involve editing the
    rule."""
    ignored = set()
    try:
        with open(".guardignore", "r", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if line and not line.startswith("#"):
                    ignored.add(line.replace("\\", "/"))
    except OSError:
        pass
    return ignored


def check_text(path, content):
    """Returns (blocked, warnings) for one file's content."""
    blocked = []
    warnings = []
    base = path.replace("\\", "/")

    exempt_reference = any(
        base == e or base.endswith("/" + e) for e in INTERNAL_REFERENCE_EXEMPT
    )
    exempt_machine = any(base == e or base.endswith("/" + e) for e in MACHINE_PATH_EXEMPT)

    def scan(pattern, label, sink, limit=3, skip=False):
        if skip:
            return
        seen = 0
        for match in pattern.finditer(content):
            line = content.count("\n", 0, match.start()) + 1
            excerpt = match.group(0).strip()[:80]
            sink.append(f"{path}:{line}  {label}: {excerpt}")
            seen += 1
            if seen >= limit:
                sink.append(f"{path}  ...more matches suppressed")
                break

    scan(MACHINE_PATHS, "machine path", blocked, skip=exempt_machine)
    scan(SECRETS, "possible secret", blocked)

    inline_allow = bool(INLINE_ALLOW_MARKER.search(content))
    scan(
        INTERNAL_REFERENCES,
        "local working directory",
        blocked,
        skip=exempt_reference or inline_allow,
    )
    # The rules file documents the marker itself, so it would always warn.
    if inline_allow and base not in VOCABULARY_EXEMPT:
        warnings.append(f"{path}  carries a guard-allow marker; internal references skipped")

    for match in EMAIL.finditer(content):
        host = match.group(0).split("@", 1)[1].lower()
        if any(host == allowed or host.endswith("." + allowed) for allowed in ALLOWED_EMAIL_HOSTS):
            continue
        line = content.count("\n", 0, match.start()) + 1
        blocked.append(
            f"{path}:{line}  email address: {match.group(0)} "
            f"(use {os.environ.get('NOREPLY', 'your-account@users.noreply.github.com')})"
        )
        break

    if not any(base == exempt or base.endswith("/" + exempt) for exempt in VOCABULARY_EXEMPT):
        scan(INTERNAL_VOCABULARY, "internal vocabulary", warnings)

    return blocked, warnings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--all", action="store_true", help="scan every tracked file")
    group.add_argument("--staged", action="store_true", help="scan staged content (default)")
    group.add_argument("--pr-body", metavar="FILE", help="scan a pull request description")
    group.add_argument("--install", action="store_true", help="install as the pre-commit hook")
    args = parser.parse_args()

    if args.install:
        return install_hook()

    blocked, warnings = [], []

    if args.pr_body:
        with open(args.pr_body, "r", encoding="utf-8") as handle:
            content = handle.read()
        found, noted = check_text("PR description", content)
        blocked += found
        warnings += noted
    else:
        paths = list(iter_tracked()) if args.all else list(iter_staged())
        ignored = load_guardignore()
        for path in paths:
            if path.replace("\\", "/") in ignored:
                continue
            if should_skip(path):
                continue
            if args.all:
                try:
                    if os.path.getsize(path) > MAX_BYTES:
                        continue
                    with open(path, "rb") as handle:
                        content = handle.read().decode("utf-8")
                except (OSError, UnicodeDecodeError):
                    continue
            else:
                content = read_staged(path)
                if content is None or len(content) > MAX_BYTES:
                    continue
            found, noted = check_text(path, content)
            blocked += found
            warnings += noted

    if warnings:
        print("Guard warnings (not blocking):")
        for line in warnings:
            print("  " + line)
        print()

    if blocked:
        print("Guard BLOCKED this content:", file=sys.stderr)
        for line in blocked:
            print("  " + line, file=sys.stderr)
        print("", file=sys.stderr)
        print("Fix the files. Do not use --no-verify.", file=sys.stderr)
        print("If a finding is a false positive, teach the rule instead, with a", file=sys.stderr)
        print("comment saying why it is safe.", file=sys.stderr)
        return 1

    scope = "the pull request description" if args.pr_body else (
        "every tracked file" if args.all else "staged content")
    print(f"Guard: clean ({scope}).")
    return 0


def install_hook():
    hook = os.path.join(".git", "hooks", "pre-commit")
    script = "#!/bin/sh\npython scripts/guard_public.py --staged\n"
    if os.path.exists(hook):
        print(f"Refusing to overwrite the existing hook at {hook}.", file=sys.stderr)
        return 1
    with open(hook, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(script)
    os.chmod(hook, 0o755)
    print(f"Installed pre-commit hook at {hook}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
#!/usr/bin/env python3
"""Fails if the OAuth Worker ever gains somewhere to put a webhook URL.

The generator's privacy copy tells people the Worker cannot keep their webhook URL,
and the reason that is true is structural rather than behavioural: `wrangler.toml`
declares no storage bindings, so there is nowhere for the handler to write even by
accident. A promise like that has to be enforced, not remembered -- a single
`[[kv_namespaces]]` block added for some unrelated convenience would quietly make a
published privacy claim false.

Run from the repository root. CI runs it on any change under worker/.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

WRANGLER = Path("worker/wrangler.toml")
HANDLER = Path("worker/src/index.js")

# Every Cloudflare binding that can persist or forward data. Queues and services are
# here too: "we keep nothing" stops being true if the data is handed somewhere else.
FORBIDDEN_TABLES = [
    "kv_namespaces",
    "d1_databases",
    "r2_buckets",
    "durable_objects",
    "queues",
    "analytics_engine_datasets",
    "hyperdrive",
    "vectorize",
    "services",
    "mtls_certificates",
    "send_email",
]


def strip_comments(toml_text: str) -> str:
    """TOML comments only, which is enough here -- no string in this file contains '#'.

    Needed because the file names the forbidden bindings in a comment explaining why
    they are absent, so a plain substring search finds them and fails on the
    documentation rather than on a binding.
    """
    out = []
    for line in toml_text.splitlines():
        out.append(line.split("#", 1)[0])
    return "\n".join(out)


def main() -> int:
    problems: list[str] = []

    if not WRANGLER.exists():
        print(f"{WRANGLER} not found -- run from the repository root", file=sys.stderr)
        return 1

    body = strip_comments(WRANGLER.read_text(encoding="utf-8"))
    for name in FORBIDDEN_TABLES:
        # Matches [name], [[name]] and name = ... , ignoring comments.
        if re.search(rf"^\s*\[{{1,2}}\s*{re.escape(name)}\b", body, re.MULTILINE) \
                or re.search(rf"^\s*{re.escape(name)}\s*=", body, re.MULTILINE):
            problems.append(
                f"{WRANGLER}: declares '{name}'. The generator tells users this Worker "
                f"cannot keep their webhook URL, and that is only true while it has "
                f"nowhere to put one. Remove the binding, or change the privacy copy "
                f"and this check together -- not one without the other."
            )

    if HANDLER.exists():
        handler = HANDLER.read_text(encoding="utf-8")
        # caches.default would persist a response -- including, if misused, one
        # carrying a webhook URL -- in Cloudflare's edge cache.
        if "caches.default" in handler or "caches.open" in handler:
            problems.append(
                f"{HANDLER}: uses the Cache API. A cached response is a stored response."
            )

    for p in problems:
        print(f"ERROR: {p}", file=sys.stderr)
    if problems:
        print(f"\n{len(problems)} problem(s) found in the OAuth Worker.", file=sys.stderr)
        return 1

    print("OAuth Worker has no storage bindings.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

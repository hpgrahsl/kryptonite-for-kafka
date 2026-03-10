#!/usr/bin/env python3
"""Generate a root index.html that redirects to the latest version.

Usage:
  make-root-redirect.py <versions-file> [site-url]

The generated HTML is written to stdout.

Examples:
  make-root-redirect.py versions.json https://hpgrahsl.github.io/kryptonite-for-kafka/ > index.html
"""

import json
import sys


def find_latest(versions):
    """Return the version string marked with the 'latest' alias, or the first release entry."""
    for v in versions:
        if 'latest' in v.get('aliases', []):
            return v['version']
    for v in versions:
        if v['version'] not in ('dev',):
            return v['version']
    return versions[0]['version'] if versions else 'dev'


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    versions_file = sys.argv[1]
    site_url = sys.argv[2].rstrip('/') + '/' if len(sys.argv) > 2 else ''

    with open(versions_file) as f:
        versions = json.load(f)

    latest = find_latest(versions)
    canonical = f"{site_url}{latest}/" if site_url else f"./{latest}/"

    print(f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta http-equiv="refresh" content="0; url=./{latest}/">
  <link rel="canonical" href="{canonical}">
  <title>Redirecting to {latest}...</title>
</head>
<body>
  Redirecting to <a href="./{latest}/">{latest}</a>...
</body>
</html>""")


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Update versions.json with a new version entry.

Usage:
  update-versions.py <versions-file> <version> [alias] [--max-releases N]

Examples:
  update-versions.py versions.json dev
  update-versions.py versions.json v0.7 latest --max-releases 3
"""

import json
import os
import sys


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)

    versions_file = sys.argv[1]
    new_version = sys.argv[2]

    # Parse optional positional alias and --max-releases flag
    alias = None
    max_releases = 3
    i = 3
    while i < len(sys.argv):
        arg = sys.argv[i]
        if arg == '--max-releases' and i + 1 < len(sys.argv):
            max_releases = int(sys.argv[i + 1])
            i += 2
        elif not arg.startswith('--'):
            alias = arg
            i += 1
        else:
            i += 1

    # Load existing versions or start fresh
    versions = []
    if os.path.exists(versions_file):
        with open(versions_file) as f:
            versions = json.load(f)

    # Separate the floating 'dev' entry (tracks master) from pinned releases.
    # Also evict the legacy 'latest' slot (old master location before rename to 'dev').
    dev_entry = next((v for v in versions if v['version'] == 'dev'), None)
    release_entries = [v for v in versions if v['version'] not in ('dev', 'latest')]

    if new_version == 'dev':
        dev_entry = {'version': 'dev', 'title': 'dev'}
    else:
        # Remove 'latest' alias (and its title suffix) from all existing entries,
        # so only the newly-published release carries the alias.
        for v in release_entries:
            aliases = v.get('aliases', [])
            if 'latest' in aliases:
                aliases.remove('latest')
                v['title'] = v['version']  # strip " (latest)" suffix
            if not aliases:
                v.pop('aliases', None)

        # Remove existing entry for this version (will re-add at front)
        release_entries = [v for v in release_entries if v['version'] != new_version]

        # Build new entry
        entry = {'version': new_version, 'title': new_version}
        if alias:
            entry['aliases'] = [alias]
            entry['title'] = f'{new_version} ({alias})'

        release_entries.insert(0, entry)

        # Prune: keep only the most recent max_releases entries
        if len(release_entries) > max_releases:
            release_entries = release_entries[:max_releases]

    # Reassemble: 'dev' (master) first, then pinned releases newest-first
    new_versions = []
    if dev_entry:
        new_versions.append(dev_entry)
    new_versions.extend(release_entries)

    with open(versions_file, 'w') as f:
        json.dump(new_versions, f, indent=2)
        f.write('\n')

    print(f"Updated {versions_file}: {[v['version'] for v in new_versions]}")


if __name__ == '__main__':
    main()

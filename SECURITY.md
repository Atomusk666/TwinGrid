# Security

The public beta is the current development line. There is no long-term support
or security response-time guarantee.

Do not disclose a secret, private file, exploitable payload or personal data in
a public issue. If GitHub private vulnerability reporting is enabled, use the
repository's Security → Report a vulnerability entry. Otherwise open a minimal
issue asking the maintainer for a private reporting channel, without sensitive
details. Do not post a diagnostic archive as a substitute.

Useful non-sensitive context includes TwinGrid version/code, firmware, affected
feature and a general description of the impact. Review exports and screenshots
for paths, device IDs and account data before sharing. Never upload ROMs or saves.

Release signing material is intentionally absent. Check official APK hashes
against `release_manifest.json` and the matching GitHub Release. A differently
signed developer build cannot update the official application in place.

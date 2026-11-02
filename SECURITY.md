# Security policy

## Supported builds

Security fixes target the newest published Nocturne release for each supported Minecraft/loader pair listed in [SUPPORT.md](SUPPORT.md). Development adapters and historical releases are not maintained branches. A supported game version does not imply support for every loader or third-party modpack.

## Reporting a vulnerability

Use [GitHub's private vulnerability report form](https://github.com/Truwty/nocturne/security/advisories/new). If private reporting is unavailable, open a public issue asking for a private contact without posting exploit instructions, credentials or personal data. There is no guaranteed response time or bounty program.

Include the Nocturne version, exact Minecraft version and loader, operating system, expected impact and a minimal reproduction. Use Nocturne's filtered diagnostic export when useful. Do not attach authentication stores, full configuration directories, song history or unreviewed game logs.

## Release integrity

Download only artifacts linked from this repository or its GitHub Pages download selector. Match the exact Minecraft version and loader. Published assets include SHA-256 digests, sources and build evidence. A digest verifies agreement with the published file; it is not a cryptographic signature or proof that a third-party mirror is trustworthy.

Update checks notify and link to a release; they never install executable code. Optional authentication credentials stay in the local credential store. Compatibility and security fixes must preserve client-only behavior, bounded network/file inputs and privacy-filtered diagnostics.

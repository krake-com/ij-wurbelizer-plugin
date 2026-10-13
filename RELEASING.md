# Releasing

Releases are cut locally; there is no CI. The first publication to JetBrains Marketplace
must be uploaded by hand, everything after that can go through Gradle.

## One-time setup

### 1. Signing key

Plugins are signed twice: once by the author, once by JetBrains Marketplace on top.
An unsigned plugin makes the IDE show a warning dialog at install time, so sign every
release. Generate the key **outside** the repository and keep it with the rest of your
secrets:

```bash
openssl genpkey -aes-256-cbc -algorithm RSA \
  -out private.pem -pkeyopt rsa_keygen_bits:4096
openssl req -key private.pem -new -x509 -days 3650 -out chain.crt
```

Losing the key is recoverable — a later release may be signed with a new certificate.

### 2. Gradle properties

Secrets live in `~/.gradle/gradle.properties`, never in this repository:

```properties
signCertificateChainFile = /path/to/chain.crt
signPrivateKeyFile       = /path/to/private.pem
signPrivateKeyPassword   = <passphrase>

# available only after the plugin has been approved: Marketplace profile -> My Tokens
intellijPlatformPublishingToken = <token>
```

The build also accepts the `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`
and `PUBLISH_TOKEN` environment variables as a fallback.

## Cutting a release

`-PplatformLocalPath=` is important: it overrides any local IDE configured in
`~/.gradle/gradle.properties`, so the release is built against the downloaded platform
declared in `gradle.properties` rather than against whichever IDE happens to be
installed on this machine.

1. Update `CHANGELOG.md` and the `<change-notes>` block in
   `src/main/resources/META-INF/plugin.xml`.
2. Bump `pluginVersion` in `gradle.properties`.
3. Build, test and verify:
   ```bash
   ./gradlew -PplatformLocalPath= clean build verifyPlugin
   ```
   `verifyPlugin` must report `Compatible` for every IDE in the supported range
   (2025.1 through the current release). It downloads each IDE it checks against, so
   the first run takes roughly twenty minutes; later runs reuse the Gradle cache.
4. Sign:
   ```bash
   ./gradlew -PplatformLocalPath= signPlugin
   ```
   The signed artifact lands in `build/distributions/`.
5. Commit and tag (`<version>`), then push.
6. Publish — **first release only**: upload the signed zip by hand at
   <https://plugins.jetbrains.com> (Profile -> Add new plugin). Later releases:
   ```bash
   ./gradlew -PplatformLocalPath= publishPlugin
   ```
   `signPlugin` runs automatically before `publishPlugin`.

Every version is reviewed by JetBrains, not just the first. Reviews take 3–4 working
days; if nothing arrives, mail <marketplace@jetbrains.com>.

## First upload: listing fields

- **License**: LGPL 2.1 (a license must be selected; it is a mandatory field)
- **Tags**: at least one is required
- **Source code**: <https://github.com/krake-com/ij-wurbelizer-plugin> — the
  repository has to be public, since the Marketplace requires open-source plugins to
  link to their source
- **Documentation**: <https://wurbelizer.org>
- **Screenshots**: at least 1200x760, consistent aspect ratio

Note that the plugin id `org.wurbelizer.idea.wurblet` is **immutable** once published.

Before uploading, settle your **trader / non-trader** status. A trader is anyone acting
for purposes related to their trade, business or profession, and the requirement covers
free plugins too. Traders must complete verification (address, phone, ID, registration
number), and those details are displayed publicly on the listing. Unverified traders are
subject to suspension.

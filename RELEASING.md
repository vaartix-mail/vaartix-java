# Releasing

The SDK is published to Maven Central as `com.vaartix:vaartix-java`.

## One-time setup (done once by a maintainer)

1. Create an account at https://central.sonatype.com (sign in with GitHub, as vaartix-mail).
2. **Namespaces → Add namespace → `com.vaartix`**. Central shows a verification key: add it as a
   **TXT record on vaartix.com** (Cloudflare DNS), then click **Verify**.
3. Create a GPG key for signing releases and publish its public key to a key server. Store the
   private key and passphrase only in the GitHub `maven-central` environment as secrets, with a
   required reviewer and deployment tags `v*` only.
4. Generate a Central **user token** and store it in the same environment.

A publish workflow (`central-publishing-maven-plugin`, signed artifacts, sources and Javadoc jars)
is added once this setup exists.

## Each release

1. Update `<version>` in `pom.xml` and `VERSION` in `Vaartix.java`, and move the "Unreleased"
   notes in `CHANGELOG.md` under the new version. Merge that to `main` through a pull request.
2. Create a GitHub release with the tag `v<version>` on `main`, then approve the publish run.

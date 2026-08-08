# Publishing to Maven Central

Publishing is gated by a non-prerelease GitHub Release. Pushes and pull
requests only build and test.

## Release procedure

1. Replace the snapshot version in all three poms with the same release
   version and update versioned documentation.
2. Merge to `main` and wait for CI to pass.
3. Publish a GitHub Release tagged `v<version>` at that exact commit.
4. The release workflow validates the tag, rebuilds and tests all three native
   classifiers from that tag, and deploys one signed Central bundle.

Repository secrets required by the workflow are
`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`, and
`GPG_PASSPHRASE`.

For a local packaging check without publication:

```sh
mvn -Prelease -Dgpg.skip=true clean verify
```

Releases are immutable. Publish changed content under a new version rather
than rebuilding an existing version.


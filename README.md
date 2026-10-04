# Reddit NSFW Only

An experimental Morphe patch bundle for Reddit Android that deliberately does
the inverse of `warleysr/reddit-nsfw-blocker`.

## Behaviour

- Forces **Show mature content (I'm over 18)** on locally.
- If the account preference is off, attempts to turn it on through Reddit's own
  preference repository so the account stays consistent across clients.
- Includes mature content in search.
- Forces NSFW image blurring off.
- Forces Reddit safe search off.
- Legacy listings keep `Link` objects only when `getOver18()` is true.
- Compose feeds keep confirmed NSFW items, preserve structural and UNKNOWN feed
  objects, and remove fully scanned ordinary non-NSFW items.
- UNKNOWN items are logged instead of deleted, making Reddit model changes easier
  to investigate after app updates.

## Important first-build assumption

The upstream blocker proves that `com.reddit.domain.SafeSearch.On` exists in
Reddit `2026.39.0`. This derivative uses the expected counterpart
`com.reddit.domain.SafeSearch.Off`. That symbol still needs confirmation by a
successful Morphe build/runtime test against Reddit `2026.39.0`.

## Supported target

Reddit `2026.39.0` (`com.reddit.frontpage`), matching the upstream patch this
fork was derived from.

## Build

GitHub Actions is configured to install Gradle 9.8 itself, run the pure-Java
classifier/filter tests, then run:

```bash
gradle clean :patches:buildAndroid
```

Morphe dependencies are hosted on GitHub Packages, so the workflow supplies the
repository `GITHUB_TOKEN` automatically. The resulting `.mpp` is uploaded as the
`reddit-nsfw-only-mpp` workflow artifact.

## Origin / license

This is a modified derivative of:

- https://github.com/warleysr/reddit-nsfw-blocker
- https://github.com/MorpheApp/morphe-patches

It is intentionally marked as a different project and is not affiliated with
or endorsed by the upstream authors, Morphe, or Reddit. See `LICENSE` and
`NOTICE`.

# Agents — Android

- Read [README.md](README.md) for setup, architecture, and commands.
- Keep `core/protocol` independent of Android APIs.
- Keep dependency versions in `gradle/libs.versions.toml`.
- Run `./gradlew build ktlintCheck detekt test` before review.
- Follow the protocol contract in the monorepo `docs/protocol/` directory.
- Use the iOS app as the feature reference, not as a replacement for the protocol contract.
- Keep unfinished features clearly marked as under development.
- Use conventional commits and PR titles.
- Merge or enable auto-merge only after explicit human permission.
- Keep credentials, local SDK paths, build output, and signing keys outside Git.
- Any PR that ports or updates behavior from the iOS app must update `ios-parity.json` (commit + updatedAt + notes) in the same PR.

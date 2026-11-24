# Tunnel Golden Updater

`updateTunnelGoldens` is a standalone Gradle task and is not attached to `build`. It regenerates
candidate files from the committed scenario scripts, and its default mode is compare-only:

```powershell
.\gradlew.bat updateTunnelGoldens
```

The task passes `--generate`, which writes the three scenario pairs to
`build/tunnel-golden-candidates/` using the real fixed-step reducer, then compares the exact `.tape`
and `.trace` bytes with the committed files under `src/test/resources/tunnel/golden/`. A mismatch
exits nonzero and names the divergent scenario; `build` and `test` never write goldens.

The first goldens were created with the explicit bootstrap path, which is only accepted while the
golden directory has no `.tape`/`.trace` files:

```powershell
.\gradlew.bat updateTunnelGoldens -PtunnelGoldenArgs="--update --allow-golden-write --bootstrap"
```

That path depends only on compilation, not on the golden test it satisfies. After the goldens are
committed, any further regeneration additionally requires `--allow-golden-write`, a native
`aarch64` JVM, a clean Git worktree, and an evidence manifest matching checked-out `HEAD`:

```properties
arch=aarch64
runnerId=github-arm64-runner-123
commitSha=<full 40-character commit SHA>
hashVectors=pass
traceParity=pass
```

```powershell
.\gradlew.bat updateTunnelGoldens -PtunnelGoldenArgs="--update --allow-golden-write --evidence artifacts/arm64-evidence.properties"
```

The updater rejects unsupported candidate names and writes only the exact `.tape`/`.trace` paths for
`zero-spin-wall-bounce`, `constant-spin-curve`, and `known-sweep-paddle-contact`. The manifest is
provenance metadata, not a cryptographic signature; review the linked CI run and artifact before
using it. Unit tests use temporary candidate directories and an injected write recorder; they do not
write to the repository's golden directory.

## Physics Gate

`physics-gate.yaml` uses JSON syntax, which is a valid YAML subset, so the existing Jackson JSON
dependency can parse it without adding a YAML library. Run the machine check with:

```powershell
.\gradlew.bat determinismGate
```

Every prerequisite must have status `pass` and an evidence pointer that resolves to a repository
file or an HTTPS URL. The command prints each row and exits nonzero while any prerequisite is
pending, blocked, or missing evidence. The gate is open since the real reducer and its goldens landed
([ADR 0019](../../openspec/adr/0019-tunnel-physics-mechanics.md)).

ADR evidence belongs in `openspec/adr/`; keep the decision record with the referencing change.

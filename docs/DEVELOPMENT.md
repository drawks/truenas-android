# Development Guide

## Code structure

- `app/src/main/java/com/drawks/truenasandroid/app`: Android entry points + top-level Compose screen
- `core/*`: shared storage/network/model foundations
- `feature/connection`: user input and connection orchestration
- `feature/dashboard`: data retrieval and dashboard state
- `di`: dependency graph wiring with Hilt

## Branch and PR workflow

1. Branch from `main`
2. Keep PR scope focused
3. Run:
   - `./gradlew assembleDebug`
   - `./gradlew testDebugUnitTest lintDebug`
4. Open PR with requirement checklist and screenshots when UI changes

## Definition of done

- Feature behavior works for success/loading/error states
- Token/host persistence verified
- Unit tests added/updated for new logic paths
- CI is green
- Docs updated if setup, architecture, or workflows changed

## Testing strategy

- Unit tests for ViewModel + repository logic using mock/fake repositories
- UI preview coverage through mock mode
- Integration checks against a real TrueNAS host are manual for now

TODO: Add Compose UI tests and contract tests against a controlled TrueNAS API fixture.

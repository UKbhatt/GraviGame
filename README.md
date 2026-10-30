# StickMan

A small Jetpack Compose sandbox game. A chunky black blob falls in whichever direction the phone's
gravity sensor says is "down". Switch to build mode, draw terrain with your finger, switch back,
and watch the blob land on, slide along, and bounce off what you drew.

The launch menu offers two games:

- **Free Play**
  - **Play mode**: tilt the device to roll the blob around. It bounces off the screen edges and any drawn strokes.
  - **Build mode**: drag to draw strokes. Undo removes the last stroke, Clear removes them all.
  - Leaving build mode re-spawns the blob on the nearest clear spot so it never starts inside terrain.
- **Star Hunt**: a star appears at a random spot. Roll the blob into it to score; the next star appears
  immediately. There is no build mode here, and Back returns to the menu.

## Getting started

Requirements: Android Studio with an SDK that includes API 37, and JDK 21 (Gradle resolves the
toolchain automatically through `gradle/gradle-daemon-jvm.properties`).

```bash
./gradlew :app:assembleDebug        # build the debug APK
./gradlew :app:installDebug         # install on a connected device or emulator
./gradlew :app:testDebugUnitTest    # run the physics unit tests on the JVM
```

The minimum supported Android version is 8.0 (API 26). Orientation is locked to portrait.

## Folder structure

```
StickMan/
├── README.md                       this file
├── Guidelines.md                   the working contract for humans and AI assistants
├── build.gradle.kts                root plugins only
├── settings.gradle.kts             repositories and the single :app module
├── gradle/libs.versions.toml       every dependency version lives here
└── app/
    ├── build.gradle.kts            Android config: namespace, SDK levels, Compose
    └── src/
        ├── main/java/com/example/stickman/
        │   ├── MainActivity.kt     entry point: edge-to-edge, theme, menu ⇄ game switch
        │   ├── MenuScreen.kt       the launch menu: Free Play or Star Hunt
        │   ├── game/               the feature (see below)
        │   └── ui/theme/           Material 3 theme scaffolding
        ├── test/java/com/example/stickman/game/
        │   ├── GamePhysicsTest.kt  JVM tests for the physics domain
        │   └── StarHuntTest.kt     JVM tests for the star rule
        └── androidTest/            instrumented test stub
```

### The `game` package

Code is grouped by feature, not by layer. Everything the game needs sits in one package, and the
files inside it are split by *reason to change*:

| File | Responsibility | Depends on Android or Compose? |
|---|---|---|
| `GamePhysics.kt` | Domain model and the frame step: `Vec2`, `Stroke`, `World`, `GameState`, `tick()` | No |
| `StarHunt.kt` | Star Hunt rule: where the star is, how many were caught, `collectIfReached()` | No |
| `GameMode.kt` | The two games the menu can start: `FreePlay`, `StarHunt` | No |
| `GameScreen.kt` | Holds screen state, wires the pieces together, renders the HUD buttons | Yes |
| `GameLoop.kt` | Calls a callback once per display frame with the elapsed seconds | Yes |
| `DeviceGravity.kt` | Reads the gravity sensor into a Compose `State<Vec2>` | Yes |
| `TerrainInput.kt` | A `Modifier` that turns drag gestures into a list of points | Yes |
| `CharacterRenderer.kt` | Draws the blob and the terrain onto a `DrawScope` | Yes |
| `GamePalette.kt` | The handful of colours the game uses | Yes |

If a change touches the blob's appearance, it lives in the renderer. If it changes how the blob
moves, it lives in the physics file and gets a test. If it changes what the player can do, it lives
in the screen file.

## Architecture

The app has two layers with one deliberate seam between them.

### Pure domain

`GamePhysics.kt` imports nothing from Android or Compose. Game state is an immutable data class, and
advancing a frame is a pure function:

```kotlin
fun GameState.tick(gravity: Vec2, world: World, obstacles: List<Stroke>, dt: Float): GameState =
    integrate(gravity, dt)
        .resolveWalls(world)
        .resolveTerrain(obstacles)
        .clampInside(world)
        .withFacing()
```

Each stage is a private extension that returns a copy. Because the domain has no framework
dependencies, it is tested on the JVM in milliseconds with plain JUnit, with no emulator and no
Robolectric. All tunable numbers (gravity scale, blob radius, restitution, friction) are named
constants on the `Physics` object.

### Thin impure shell

Everything else is Compose code that owns side effects and feeds the domain:

```
sensor ──▶ gravity: State<Vec2> ──┐
                                   ├──▶ state = state.tick(gravity, world, strokes, dt) ──▶ Canvas
frame loop ──▶ dt ────────────────┘                        ▲
                                                           │
drag gestures ──▶ Mode.Build(currentStroke) ──▶ strokes ───┘
```

Screen state is three `remember`ed values, all keyed on the `World` so a resize resets them together:

- `mode`: a sealed type, one of `Mode.Play`, `Mode.Build(currentStroke)` or `Mode.Hunt(starHunt)`. A
  half-drawn stroke can only exist while building and a star only while hunting, so those illegal
  combinations are unrepresentable.
- `strokes`: the committed terrain, an immutable list that is replaced rather than mutated.
- `state`: the blob's `GameState`.

The frame loop only calls `tick` while in play or hunt mode. The canvas redraws from the current values each
frame. Buttons and gestures replace state; nothing mutates in place.

### Why this shape

- **Testability without an emulator.** The part most likely to have subtle bugs, the collision maths,
  is the part that can be tested in a tight loop.
- **One place per concern.** A sensor fix, a drawing tweak, and a physics change land as three
  separate small diffs that cannot step on each other.
- **Honest cost.** More files than a 300-line single screen, and `internal` visibility on a couple of
  helpers so tests can reach them. Both are cheap compared to debugging physics on a device.

## Contributing

The full working contract is in [`Guidelines.md`](Guidelines.md). The short version:

### Before you write code

- Read the file before claiming what it does.
- For a change that spans several files, or where more than one approach is plausible, write up the
  plan first and get agreement before editing.
- Ask a question only when the answer changes the implementation. Otherwise state the assumption
  and continue.

### While you write code

- **Keep the domain pure.** Nothing in `GamePhysics.kt` may import Android or Compose. If you need
  a type from the UI side, define a plain Kotlin equivalent (this is why `Vec2` exists instead of
  `Offset`).
- **Immutable state, explicit transitions.** Return a new `GameState`; never mutate one. Replace
  lists; never `add` to them.
- **Make illegal states unrepresentable.** Reach for a sealed type or enum before a boolean flag.
- **Name every number.** A literal in drawing or physics code gets a `const val` whose name says what
  it controls, not what its value is.
- **One responsibility per file and function.** If describing it needs the word "and", split it.
- **No comments in new code.** Make names and structure carry the meaning. Existing comments stay.
- **Minimal diffs.** Touch only what the task needs. Spotted a nearby cleanup? Mention it in the PR
  description and do it separately.
- **Use the newest idiom the toolchain supports** (Kotlin 2.2, Compose BOM 2026.02). Match the
  surrounding file's style when editing existing code.

### Tests

- Every change to `GamePhysics.kt` adds or updates a case in `GamePhysicsTest.kt`.
- A bug fix includes the test that would have caught it.
- Test names are sentences in backticks that state the expected behaviour, for example
  `` `blob hitting the right wall bounces back scaled by restitution` ``.
- Run `./gradlew :app:testDebugUnitTest` before opening a PR.

### Commits and pull requests

- Branch names: `<type>/<short-description>`, lowercase and hyphenated.
- Commit subjects: `<type>(<scope>): <description>`, where the scope is optional and type is one of
  `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- A PR description answers three questions: what the actual issue was, why this approach over the
  obvious alternative, and how to verify it. End by naming the part most worth a reviewer's attention.
- Keep PRs reviewable. A large change lands as a sequence of small, independently understandable steps.

### Dependencies

All versions live in `gradle/libs.versions.toml`. Add a new library there and reference it through
the `libs` catalog in `app/build.gradle.kts`. Do not inline version strings in build files.

## Known gaps

- Devices without a gravity sensor fall back to the raw accelerometer, which mixes in shake and
  movement. A low-pass filter would smooth that out.
- Release builds have code shrinking disabled (`optimization { enable = false }` in
  `app/build.gradle.kts`).
- The `ui/theme` package is Android Studio template code. The game draws with its own palette from
  `GamePalette.kt` and does not read `MaterialTheme`.
- Game state is not saved across process death. Terrain and the blob reset when the activity is
  recreated.

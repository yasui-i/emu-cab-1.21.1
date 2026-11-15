# Button and rotary animation verification

The release does not include this harness. `check-animation-visual.gradle` compiles
it into separate `build/animation-visual-server/mods` and
`build/animation-visual-client/mods` folders, using local server port 25575.
Normal `run/` saves and settings are untouched.

Run from the repository root after exporting and integrating the model:

```powershell
$env:JAVA_HOME='D:/Program Files (x86)'
$env:GRADLE_USER_HOME='D:/GradleCache'
.\gradlew.bat -I tools/check-controls.gradle checkCabControls build --offline --no-configuration-cache --console=plain
.\gradlew.bat -I tools/check-animation-visual.gradle runServer --offline --no-configuration-cache --console=plain
# In a second terminal, after the isolated server is ready:
.\gradlew.bat -I tools/check-animation-visual.gradle runClient --offline --no-configuration-cache --console=plain
```

The client exits after the last sample. Stop the isolated server afterwards.
Results are written to `build/animation-visual-client/animation-client-check.txt`
and `screenshots/`.

`CabControlAnimationStateCheck` checks timing, rapid repeat continuity,
independent controls, stale/duplicate packets, saved detents, spring return and
signal isolation. `CabControlAnimationCheck` checks moving-part ownership,
stationary silver rims and mounts, installation axes, generated metadata,
static/dynamic surface conservation, swept click bounds and four facing/tilted
parent transforms.

The actual client harness calls `CabDeskBlockEntity.activate` on the dedicated
server through a test-only operator command. It verifies that authoritative
states and transient animation packets reach the client, then captures key
on/off, direction forward, successful press/rebound and repeat actuation.
It also checks that an interlock-rejected door request produces no press event.
This tests the real server-to-client animation path; it does not simulate a
player's full mouse input or drive a moving Sable train.

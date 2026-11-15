# Speed display validation

This harness is never included in the release. `check-speed-visual.gradle` uses
separate `build/speed-visual-server` and `build/speed-visual-client` directories,
with server port 25578. Copy the ordinary runtime dependency jars into each
isolated `mods` directory, set the server EULA/properties, then run server and
client with this init script.

The fixed scene deliberately sends synthetic 200, 350 and 0 km/h values through
the production `CabSpeedPayload`, using a separate increasing test sequence.
The server's ordinary fixed-body speed samples are zero; they cannot overwrite
these newer samples. The client verifies stable values at high speed, monotonic
acceleration/deceleration, frame interpolation, stale/invalid rejection and saves
three screenshots. This checks the real network/display path; it does not claim
to reproduce a driven Sable train.

The dependency-free `CabSpeedometerCheck.java` covers stable 20–1000 km/h values,
sample ordering/validation, bounds, stop settling and partial-tick interpolation.

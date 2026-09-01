# Testing without an emulator

You do not need Android Studio or an emulator to verify most of this app. The
work is split across three places, only one of which needs your laptop's CPU.

| Layer | What it proves | Runs on |
|---|---|---|
| JVM unit tests | Protocol + geometry logic is correct | GitHub's runners (free) |
| Debug APK over `adb` | The app installs, the UI works, gestures fire | Your phone; laptop only relays USB |
| Two paired phones | The Bluetooth radio link | Two phones, no laptop |

---

## 1. JVM unit tests — zero local compute

Every push to any branch runs the **Android CI (Strict Iterative Verification)**
workflow, which executes `./gradlew testDebugUnitTest`. You read the result in
your browser; the runner does all the compiling.

These are plain JUnit tests with no Android framework involved, so they cover
the logic that used to be verifiable only with two phones:

- **`BluetoothOperationsTest`** — drives the wire protocol over in-memory
  streams. Round-trips every payload type, checks the raw-content length
  prefix, checks the stream stays in sync after a payload, and asserts a
  disconnect ends the read loop (enforced with a test timeout, so the old
  busy-loop bug would hang the test rather than pass).
- **`ClickPointLayoutTest`** — the button-to-point mapping (button "1" must tap
  the *first* point), the last point being reachable, taps landing on a point's
  centre, and points not stacking on one another.
- **`ClickerSessionTest`** — a whole controller-to-service session: one
  `BluetoothOperations` encodes commands, a second decodes them and drives a
  stand-in for the click points. Asserts exactly which point each button press
  taps.

To add coverage, drop a file in `app/src/test/java/...` and push. Keep new logic
free of Android imports where you can — that is what makes it testable here.

### Running them locally (optional)

A Celeron can usually manage the unit tests alone, since they skip resource
compilation and APK packaging:

```sh
./gradlew testDebugUnitTest --no-daemon --console=plain
```

Report: `app/build/reports/tests/testDebugUnitTest/index.html`.

If even that is too slow, skip it — CI covers you. Use
`org.gradle.jvmargs=-Xmx1g` in `gradle.properties` if you hit memory pressure.

---

## 2. Your phone over `adb` — no emulator

`adb` is a ~10 MB command line tool. It does not emulate anything; it just
talks to a real phone over USB, so it runs fine on low-end hardware.

1. Download **SDK Platform Tools** for Windows/Linux and unzip it.
2. On the phone: Settings → About phone → tap *Build number* seven times, then
   Developer options → enable **USB debugging**.
3. Plug the phone in and accept the RSA prompt.

```sh
adb devices                 # confirm the phone is listed
adb install -r app-debug.apk
adb logcat --pid=$(adb shell pidof damjay.floating.projects)
```

Get `app-debug.apk` from the **app-debug** artifact on any green CI run — you
never have to build it locally.

### What one phone alone can verify

- The app installs and launches; no startup crash.
- Nearby Devices permission flow, including **cancelling** the permission
  dialog (that path used to crash on an empty `grantResults`).
- The Bluetooth-off prompt and the "no paired devices" message on the guest
  screen.
- Enabling the accessibility service in Settings, and that the app correctly
  detects *its own* service rather than any enabled service (turn on TalkBack
  but leave Floating Clicker off — the app must still say it is not enabled).
- The floating clicker toolbar drawing over other apps.
- Stack traces for anything that does go wrong, via `logcat`.

`adb logcat` is the important half. Every fix in this area prints its
exceptions rather than swallowing them, so a bad path shows up in the log.

---

## 2b. One phone + your laptop as the second device

`tools/clicker_controller.py` stands in for whichever device you do not have.
It speaks the same byte protocol as `BluetoothOperations.java` and can play
either end. Standard library only — no pip install, no emulator.

Two things reverse independently:

| | `--role controller` (default) | `--role service` |
|---|---|---|
| **laptop does** | sends +, −, and button presses | receives them, draws the points |
| **phone does** | *As Service* — gets tapped | *As Controller* — drives the laptop |
| **tests** | the accessibility service, gesture dispatch | `ClickerActivity`, the controller UI |

| | default | `--listen` |
|---|---|---|
| **laptop is** | the client, dials out | the RFCOMM server |
| **phone is** | *Start as Host* | *Connect as Guest* |
| **tests** | `HostActivity`, `BluetoothServerThread` | `GuestActivity`, `BluetoothDeviceAdapter` |

### Try it with no hardware first

```sh
python3 tools/clicker_controller.py --demo                  # laptop controls a fake phone
python3 tools/clicker_controller.py --role service --demo   # a fake phone controls the laptop
python3 tools/clicker_controller.py --selftest              # headless, both directions
```

The `--role service --demo` run is the fun one: a scripted fake controller
presses +, +, +, then buttons 1, 2, 3, then −, and you watch the points appear
and flash on the simulated screen.

### Direction A — laptop controls the phone

1. Pair the laptop and the phone in your system Bluetooth settings.
2. Phone: app → Bluetooth clicker → **Start as Host**.
3. Laptop: `python3 tools/clicker_controller.py`, **Find paired devices**,
   pick the phone, **Start**.
4. Phone: **As Service**, and enable the accessibility service if prompted.
5. Laptop: press **+** twice, then **1**. The first point on the phone should
   be tapped. Keys `1`–`9` and `+`/`-` are shortcuts.

### Direction B — phone controls the laptop

1. Phone: **Start as Host**.
2. Laptop: `python3 tools/clicker_controller.py --role service`, connect the
   same way.
3. Phone: **As Controller**.
4. Phone: press **+** a few times, then a numbered button. The matching circle
   on the laptop's simulated screen flashes red.

This is the half that exercises `ClickerActivity` — its own point bookkeeping,
its button grid, and the null-socket guard.

### Making the phone the Guest

Add `--listen` to either of the above and start the script *first*; then on the
phone choose **Connect as Guest** and pick the laptop. This is the only way to
exercise `GuestActivity` and the device-list adapter without a second phone.

One caveat: Android resolves the service by UUID over SDP, so the laptop has to
publish a service record. That needs **PyBluez** (`pip install pybluez`, Linux).
Without it the script still listens but the phone almost certainly will not find
it, and it says so.

### Notes

- The *Traffic* pane decodes every frame both ways, so you can watch
  `-> BYTE -1 add point` leave and see what comes back.
- Android allocates the RFCOMM channel dynamically. The script asks SDP if
  PyBluez is installed, otherwise it probes channels 1–30. If it attaches to the
  wrong service, put the right channel in the Chan/Port box.
- If your laptop has no working Bluetooth, the *tcp* transport is there for a
  USB bridge over `adb reverse` — that needs a small debug hook in the app, so
  ask for it if you need to go that way.

## 3. Two paired phones — the radio link

Only the actual RFCOMM connection needs this, and your laptop is not involved.

1. Pair the two phones in system Bluetooth settings first. The guest screen
   lists **bonded** devices only; it does not run discovery.
2. Install the same debug APK on both.
3. Phone A: *Start as Host*. Phone B: *Connect as Guest*, tap phone A.
4. Phone A: *As Service*, then enable the accessibility service. Phone B:
   *As Controller*.
5. On B, press **+** twice, then press **1**. The first point on A must be
   tapped — not the second, and not nothing.

Worth deliberately trying: press Back mid-connect, cancel the waiting dialog,
and turn Bluetooth off while connected. Those are the paths that used to leak
sockets or crash.

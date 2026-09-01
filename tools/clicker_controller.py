#!/usr/bin/env python3
"""
Floating Clicker -- desktop controller.

Stands in for the second phone. Your phone runs the app as *Host* (an RFCOMM
server) and this script connects as the client, speaking the exact byte
protocol implemented by BluetoothOperations.java, so you can add, remove and
tap click points on the phone from your laptop.

    python3 clicker_controller.py                 # GUI, Bluetooth
    python3 clicker_controller.py --demo          # GUI against a fake phone, no hardware
    python3 clicker_controller.py --selftest      # headless protocol check, no GUI

Only the Python standard library is used. Tkinter is imported lazily, so
--selftest works on a machine without it.
"""

from __future__ import annotations

import argparse
import platform
import queue
import re
import socket
import struct
import subprocess
import sys
import threading
import time

# ---------------------------------------------------------------------------
# Wire protocol -- mirrors BluetoothOperations.BluetoothOperationsConstants
# ---------------------------------------------------------------------------

TYPE_TEXT = 0
TYPE_BYTE = 1
TYPE_SHORT = 2
TYPE_CHAR = 3
TYPE_INT = 4
TYPE_LONG = 5
TYPE_FLOAT = 6
TYPE_DOUBLE = 7
TYPE_RAW_CONTENT = 8
TYPE_SUCCESS = 9
TYPE_EXIT = 10

TYPE_NAMES = {
    TYPE_TEXT: "TEXT",
    TYPE_BYTE: "BYTE",
    TYPE_SHORT: "SHORT",
    TYPE_CHAR: "CHAR",
    TYPE_INT: "INT",
    TYPE_LONG: "LONG",
    TYPE_FLOAT: "FLOAT",
    TYPE_DOUBLE: "DOUBLE",
    TYPE_RAW_CONTENT: "RAW",
    TYPE_SUCCESS: "SUCCESS",
    TYPE_EXIT: "EXIT",
}

# ClickerActivity.CLICKER_ADD_POINT / CLICKER_DELETE_POINT
CLICKER_ADD_POINT = -1
CLICKER_DELETE_POINT = -2

# HostActivity registers its service record with this UUID (res/values/strings.xml).
CLICKER_UUID = "1cf64473-3259-4cb7-8f91-5ebb341506cd"

# Payload size in bytes for each fixed-width type, for the decoder.
_FIXED_WIDTH = {
    TYPE_BYTE: 1,
    TYPE_SHORT: 2,
    TYPE_CHAR: 2,
    TYPE_INT: 4,
    TYPE_LONG: 8,
    TYPE_FLOAT: 4,
    TYPE_DOUBLE: 8,
}

_STRUCT = {
    TYPE_BYTE: ">b",
    TYPE_SHORT: ">h",
    TYPE_INT: ">i",
    TYPE_LONG: ">q",
    TYPE_FLOAT: ">f",
    TYPE_DOUBLE: ">d",
}


def encode_byte(value: int) -> bytes:
    """One TYPE_BYTE frame: the type tag followed by a signed byte."""
    return bytes([TYPE_BYTE]) + struct.pack(">b", value)


def encode_text(value: str) -> bytes:
    """TYPE_TEXT frame using Java's DataOutputStream.writeUTF layout."""
    raw = value.encode("utf-8")
    return bytes([TYPE_TEXT]) + struct.pack(">H", len(raw)) + raw


def encode_raw(payload: bytes) -> bytes:
    """TYPE_RAW_CONTENT frame: type tag, two byte length, then the payload."""
    return bytes([TYPE_RAW_CONTENT]) + struct.pack(">H", len(payload)) + payload


def encode_exit() -> bytes:
    return bytes([TYPE_EXIT])


class ProtocolError(Exception):
    pass


def read_frame(read_exactly) -> tuple[int, object]:
    """
    Decode a single frame.

    `read_exactly(n)` must return exactly n bytes or raise EOFError. Returns
    (type, value); value is None for EXIT.
    """
    tag = read_exactly(1)[0]

    if tag == TYPE_EXIT:
        return tag, None
    if tag == TYPE_TEXT:
        (length,) = struct.unpack(">H", read_exactly(2))
        return tag, read_exactly(length).decode("utf-8", errors="replace")
    if tag == TYPE_RAW_CONTENT:
        (length,) = struct.unpack(">H", read_exactly(2))
        return tag, read_exactly(length)
    if tag == TYPE_CHAR:
        return tag, read_exactly(2).decode("utf-16-be", errors="replace")
    if tag in _FIXED_WIDTH:
        payload = read_exactly(_FIXED_WIDTH[tag])
        return tag, struct.unpack(_STRUCT[tag], payload)[0]

    raise ProtocolError(f"Unknown frame type {tag}")


def describe(tag: int, value: object) -> str:
    """Human readable rendering of a decoded frame."""
    name = TYPE_NAMES.get(tag, f"TYPE_{tag}")
    if tag == TYPE_EXIT:
        return "EXIT  (the phone closed the session)"
    if tag == TYPE_BYTE:
        if value == CLICKER_ADD_POINT:
            return "BYTE  -1  add point"
        if value == CLICKER_DELETE_POINT:
            return "BYTE  -2  remove point"
        return f"BYTE  {value}  tap point {value}"
    if tag == TYPE_RAW_CONTENT:
        return f"RAW   {len(value)} bytes"
    return f"{name}  {value!r}"


# ---------------------------------------------------------------------------
# Transports
# ---------------------------------------------------------------------------


class Transport:
    """A connected byte pipe."""

    def send(self, data: bytes) -> None:
        raise NotImplementedError

    def recv(self, size: int) -> bytes:
        raise NotImplementedError

    def close(self) -> None:
        raise NotImplementedError

    def read_exactly(self, count: int) -> bytes:
        chunks = []
        remaining = count
        while remaining:
            chunk = self.recv(remaining)
            if not chunk:
                raise EOFError("connection closed by the phone")
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks)


class SocketTransport(Transport):
    def __init__(self, sock: socket.socket):
        self._sock = sock

    def send(self, data: bytes) -> None:
        self._sock.sendall(data)

    def recv(self, size: int) -> bytes:
        return self._sock.recv(size)

    def close(self) -> None:
        try:
            self._sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        try:
            self._sock.close()
        except OSError:
            pass


def bluetooth_supported() -> bool:
    return hasattr(socket, "AF_BLUETOOTH") and hasattr(socket, "BTPROTO_RFCOMM")


def connect_bluetooth(address: str, channel: int | None = None, log=print) -> SocketTransport:
    """
    Connect to the phone's RFCOMM service.

    Android allocates the channel dynamically, so it is looked up over SDP when
    PyBluez is available and otherwise probed. Pass `channel` to skip all that.
    """
    if not bluetooth_supported():
        raise RuntimeError(
            "This Python build has no Bluetooth socket support "
            f"(platform: {platform.system()}). Use --demo, or the TCP transport."
        )

    candidates = [channel] if channel else _channel_candidates(address, log)

    last_error: Exception | None = None
    for candidate in candidates:
        try:
            log(f"trying RFCOMM channel {candidate} ...")
            sock = socket.socket(
                socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM
            )
            sock.settimeout(10)
            sock.connect((address, candidate))
            sock.settimeout(None)
            log(f"connected on channel {candidate}")
            return SocketTransport(sock)
        except Exception as error:  # noqa: BLE001 - report and keep probing
            last_error = error
            try:
                sock.close()
            except Exception:  # noqa: BLE001
                pass

    raise RuntimeError(f"could not connect to {address}: {last_error}")


def _channel_candidates(address: str, log) -> list[int]:
    """SDP lookup if we can, otherwise every plausible channel."""
    try:
        import bluetooth  # type: ignore  # PyBluez, optional

        services = bluetooth.find_service(uuid=CLICKER_UUID, address=address)
        if services:
            found = [s["port"] for s in services]
            log(f"SDP found the clicker service on channel(s) {found}")
            return found
        log("SDP lookup found no clicker service; is the phone on the Host screen?")
    except ImportError:
        log("PyBluez not installed, probing channels instead (this is fine)")
    except Exception as error:  # noqa: BLE001
        log(f"SDP lookup failed ({error}), probing channels instead")

    return list(range(1, 31))


def connect_tcp(host: str, port: int, log=print) -> SocketTransport:
    log(f"connecting to {host}:{port} ...")
    sock = socket.create_connection((host, port), timeout=10)
    sock.settimeout(None)
    log("connected")
    return SocketTransport(sock)


def list_paired_devices() -> list[tuple[str, str]]:
    """Best effort (address, name) list of paired devices. Never raises."""
    system = platform.system()
    try:
        if system == "Linux":
            out = subprocess.run(
                ["bluetoothctl", "devices", "Paired"],
                capture_output=True, text=True, timeout=10,
            ).stdout
            if not out.strip():
                out = subprocess.run(
                    ["bluetoothctl", "paired-devices"],
                    capture_output=True, text=True, timeout=10,
                ).stdout
            found = []
            for line in out.splitlines():
                match = re.match(r"Device\s+([0-9A-Fa-f:]{17})\s+(.*)", line.strip())
                if match:
                    found.append((match.group(1), match.group(2)))
            return found

        if system == "Windows":
            script = (
                "Get-PnpDevice -Class Bluetooth | "
                "Where-Object {$_.InstanceId -match 'DEV_'} | "
                "Select-Object -ExpandProperty InstanceId"
            )
            out = subprocess.run(
                ["powershell", "-NoProfile", "-Command", script],
                capture_output=True, text=True, timeout=20,
            ).stdout
            found = []
            for line in out.splitlines():
                match = re.search(r"DEV_([0-9A-Fa-f]{12})", line)
                if match:
                    raw = match.group(1).upper()
                    address = ":".join(raw[i:i + 2] for i in range(0, 12, 2))
                    if (address, "paired device") not in found:
                        found.append((address, "paired device"))
            return found
    except Exception:  # noqa: BLE001 - discovery is a convenience, not a requirement
        pass
    return []


# ---------------------------------------------------------------------------
# Controller session
# ---------------------------------------------------------------------------


class ControllerSession:
    """
    Sends commands and decodes whatever the phone sends back.

    Mirrors ClickerActivity: the point count goes up when we successfully send
    an add, and also when the phone tells us the user pressed + on its own
    floating toolbar.
    """

    def __init__(self, transport: Transport, on_event):
        self._transport = transport
        self._on_event = on_event
        self._lock = threading.Lock()
        self._running = True
        self.point_count = 0
        self._reader = threading.Thread(target=self._read_loop, daemon=True)
        self._reader.start()

    def _emit(self, kind: str, message: str) -> None:
        self._on_event(kind, message)

    def _send(self, data: bytes) -> bool:
        with self._lock:
            if not self._running:
                self._emit("error", "not connected")
                return False
            try:
                self._transport.send(data)
                return True
            except Exception as error:  # noqa: BLE001
                self._emit("error", f"send failed: {error}")
                self._running = False
                return False

    def add_point(self) -> None:
        if self._send(encode_byte(CLICKER_ADD_POINT)):
            self.point_count += 1
            self._emit("sent", f"add point  (now {self.point_count})")
            self._emit("points", "")

    def remove_point(self) -> None:
        if self._send(encode_byte(CLICKER_DELETE_POINT)):
            self.point_count = max(0, self.point_count - 1)
            self._emit("sent", f"remove point  (now {self.point_count})")
            self._emit("points", "")

    def tap(self, number: int) -> None:
        if not -128 <= number <= 127:
            self._emit("error", f"button {number} is out of range")
            return
        if self._send(encode_byte(number)):
            self._emit("sent", f"tap point {number}")

    def send_exit(self) -> None:
        if self._send(encode_exit()):
            self._emit("sent", "exit")

    def close(self) -> None:
        self._running = False
        self._transport.close()

    def _read_loop(self) -> None:
        try:
            while self._running:
                tag, value = read_frame(self._transport.read_exactly)
                self._emit("recv", describe(tag, value))
                # Keep our point count in step when the phone side drives it.
                if tag == TYPE_BYTE and value == CLICKER_ADD_POINT:
                    self.point_count += 1
                    self._emit("points", "")
                elif tag == TYPE_BYTE and value == CLICKER_DELETE_POINT:
                    self.point_count = max(0, self.point_count - 1)
                    self._emit("points", "")
                elif tag == TYPE_EXIT:
                    break
        except EOFError:
            if self._running:
                self._emit("error", "the phone disconnected")
        except Exception as error:  # noqa: BLE001
            if self._running:
                self._emit("error", f"read failed: {error}")
        finally:
            self._running = False
            self._emit("closed", "")


# ---------------------------------------------------------------------------
# Fake phone, for --demo
# ---------------------------------------------------------------------------


class FakePhone(threading.Thread):
    """
    A stand-in for the phone running the accessibility service. Decodes the
    same protocol and reports what the real service would have done, so the
    controller can be exercised with no hardware at all.
    """

    POINT_SIZE = 30
    SPACING = 16
    SCREEN = (1080, 1920)

    def __init__(self, on_log):
        super().__init__(daemon=True)
        self._on_log = on_log
        self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._server.bind(("127.0.0.1", 0))
        self._server.listen(1)
        self.port = self._server.getsockname()[1]
        self.points: list[tuple[int, int]] = []

    def _next_position(self) -> tuple[int, int]:
        """Same placement rule as ClickPointLayout.nextPosition."""
        if not self.points:
            return self.SPACING, self.SPACING
        last_x, last_y = self.points[-1]
        max_x = self.SCREEN[0] - self.POINT_SIZE
        next_x = last_x + self.POINT_SIZE + self.SPACING
        if next_x <= max_x:
            return next_x, last_y
        y = last_y + self.POINT_SIZE + self.SPACING
        if y > self.SCREEN[1] - self.POINT_SIZE:
            y = self.SPACING
        return self.SPACING, y

    def run(self) -> None:
        conn, _ = self._server.accept()
        transport = SocketTransport(conn)
        self._on_log("fake phone: controller connected")
        try:
            while True:
                tag, value = read_frame(transport.read_exactly)
                if tag == TYPE_EXIT:
                    self._on_log("fake phone: session closed by controller")
                    break
                if tag != TYPE_BYTE:
                    self._on_log(f"fake phone: ignoring {describe(tag, value)}")
                    continue
                self._handle(value)
        except EOFError:
            self._on_log("fake phone: controller went away")
        except Exception as error:  # noqa: BLE001
            self._on_log(f"fake phone: {error}")
        finally:
            transport.close()

    def _handle(self, command: int) -> None:
        if command == CLICKER_ADD_POINT:
            self.points.append(self._next_position())
            self._on_log(
                f"fake phone: added point {len(self.points)} at {self.points[-1]}"
            )
        elif command == CLICKER_DELETE_POINT:
            if self.points:
                self.points.pop()
                self._on_log(f"fake phone: removed a point ({len(self.points)} left)")
            else:
                self._on_log("fake phone: nothing to remove")
        else:
            index = command - 1  # ClickPointLayout.indexForButton
            if 0 <= index < len(self.points):
                x, y = self.points[index]
                centre = (x + self.POINT_SIZE // 2, y + self.POINT_SIZE // 2)
                self._on_log(f"fake phone: TAP point {command} at {centre}")
            else:
                self._on_log(f"fake phone: button {command} has no point, ignoring")


# ---------------------------------------------------------------------------
# GUI
# ---------------------------------------------------------------------------


def run_gui(args) -> int:
    try:
        import tkinter as tk
        from tkinter import ttk
    except ImportError:
        print(
            "Tkinter is missing.\n"
            "  Debian/Ubuntu : sudo apt install python3-tk\n"
            "  Fedora        : sudo dnf install python3-tkinter\n"
            "  Windows/macOS : reinstall Python with the Tcl/Tk option ticked\n"
            "Meanwhile, 'python3 clicker_controller.py --selftest' needs no GUI.",
            file=sys.stderr,
        )
        return 2

    events: queue.Queue = queue.Queue()
    state = {"session": None, "phone": None}

    root = tk.Tk()
    root.title("Floating Clicker -- desktop controller")
    root.minsize(620, 560)

    # -- connection bar ----------------------------------------------------
    top = ttk.LabelFrame(root, text="Connection", padding=8)
    top.pack(fill="x", padx=10, pady=(10, 6))

    transport_var = tk.StringVar(value="tcp" if args.demo else "bluetooth")
    address_var = tk.StringVar(value=args.address or "")
    channel_var = tk.StringVar(value=str(args.channel) if args.channel else "")
    status_var = tk.StringVar(value="not connected")

    row = ttk.Frame(top)
    row.pack(fill="x")
    ttk.Label(row, text="Transport").pack(side="left")
    transport_box = ttk.Combobox(
        row, textvariable=transport_var, values=["bluetooth", "tcp"],
        width=10, state="readonly",
    )
    transport_box.pack(side="left", padx=(6, 14))

    ttk.Label(row, text="Address").pack(side="left")
    address_entry = ttk.Entry(row, textvariable=address_var, width=26)
    address_entry.pack(side="left", padx=(6, 14))

    ttk.Label(row, text="Channel/Port").pack(side="left")
    ttk.Entry(row, textvariable=channel_var, width=8).pack(side="left", padx=(6, 0))

    row2 = ttk.Frame(top)
    row2.pack(fill="x", pady=(8, 0))
    scan_button = ttk.Button(row2, text="Find paired devices")
    scan_button.pack(side="left")
    connect_button = ttk.Button(row2, text="Connect")
    connect_button.pack(side="left", padx=6)
    disconnect_button = ttk.Button(row2, text="Disconnect", state="disabled")
    disconnect_button.pack(side="left")
    ttk.Label(row2, textvariable=status_var, foreground="#666").pack(side="right")

    # -- controls ----------------------------------------------------------
    controls = ttk.LabelFrame(root, text="Controls", padding=8)
    controls.pack(fill="x", padx=10, pady=6)

    buttons_row = ttk.Frame(controls)
    buttons_row.pack(fill="x")
    add_button = ttk.Button(buttons_row, text="+  Add point", width=16)
    add_button.pack(side="left")
    remove_button = ttk.Button(buttons_row, text="\u2212  Remove point", width=16)
    remove_button.pack(side="left", padx=6)
    exit_button = ttk.Button(buttons_row, text="Send exit", width=12)
    exit_button.pack(side="left")
    points_var = tk.StringVar(value="0 points")
    ttk.Label(buttons_row, textvariable=points_var).pack(side="right")

    ttk.Label(
        controls,
        text="Tap a numbered button to click that point on the phone. "
             "Keys 1-9 and +/- work too.",
        foreground="#666",
    ).pack(anchor="w", pady=(8, 4))

    pad = ttk.Frame(controls)
    pad.pack(fill="x")
    tap_buttons: list = []

    # -- log ---------------------------------------------------------------
    log_frame = ttk.LabelFrame(root, text="Traffic", padding=8)
    log_frame.pack(fill="both", expand=True, padx=10, pady=(6, 10))
    log_text = tk.Text(log_frame, height=14, wrap="word", state="disabled")
    scrollbar = ttk.Scrollbar(log_frame, command=log_text.yview)
    log_text.configure(yscrollcommand=scrollbar.set)
    log_text.pack(side="left", fill="both", expand=True)
    scrollbar.pack(side="right", fill="y")
    for tag, colour in (
        ("sent", "#0a6b0a"), ("recv", "#0a3f8b"),
        ("error", "#b00020"), ("info", "#555555"), ("phone", "#7a4a00"),
    ):
        log_text.tag_configure(tag, foreground=colour)

    def log(kind: str, message: str) -> None:
        if not message:
            return
        arrow = {"sent": "-> ", "recv": "<- ", "error": "!! ", "phone": " * "}.get(kind, "   ")
        log_text.configure(state="normal")
        log_text.insert("end", f"{time.strftime('%H:%M:%S')} {arrow}{message}\n", kind)
        log_text.see("end")
        log_text.configure(state="disabled")

    def post(kind: str, message: str) -> None:
        events.put((kind, message))

    def refresh_points() -> None:
        session = state["session"]
        count = session.point_count if session else 0
        points_var.set(f"{count} point{'' if count == 1 else 's'}")
        for widget in tap_buttons:
            widget.destroy()
        tap_buttons.clear()
        for number in range(1, count + 1):
            button = ttk.Button(
                pad, text=str(number), width=4,
                command=lambda n=number: state["session"] and state["session"].tap(n),
            )
            button.grid(row=(number - 1) // 10, column=(number - 1) % 10, padx=2, pady=2)
            tap_buttons.append(button)

    def set_connected(connected: bool) -> None:
        connect_button.configure(state="disabled" if connected else "normal")
        disconnect_button.configure(state="normal" if connected else "disabled")
        for widget in (add_button, remove_button, exit_button):
            widget.configure(state="normal" if connected else "disabled")
        status_var.set("connected" if connected else "not connected")

    def drain() -> None:
        try:
            while True:
                kind, message = events.get_nowait()
                if kind == "points":
                    refresh_points()
                elif kind == "closed":
                    set_connected(False)
                    log("info", "session closed")
                else:
                    log(kind, message)
        except queue.Empty:
            pass
        root.after(60, drain)

    def do_connect() -> None:
        set_connected(False)
        status_var.set("connecting ...")
        connect_button.configure(state="disabled")

        transport_kind = transport_var.get()
        address = address_var.get().strip()
        channel_text = channel_var.get().strip()

        def worker() -> None:
            try:
                if transport_kind == "bluetooth":
                    if not address:
                        raise RuntimeError("enter the phone's Bluetooth address first")
                    channel = int(channel_text) if channel_text else None
                    transport = connect_bluetooth(
                        address, channel, log=lambda m: post("info", m)
                    )
                else:
                    host = address or "127.0.0.1"
                    if not channel_text:
                        raise RuntimeError("enter a TCP port")
                    transport = connect_tcp(
                        host, int(channel_text), log=lambda m: post("info", m)
                    )
            except Exception as error:  # noqa: BLE001
                post("error", str(error))
                post("closed", "")
                return

            state["session"] = ControllerSession(transport, post)
            post("info", "ready -- add a point, then tap its number")
            post("points", "")
            root.after(0, lambda: set_connected(True))

        threading.Thread(target=worker, daemon=True).start()

    def do_disconnect() -> None:
        session = state["session"]
        if session:
            session.close()
            state["session"] = None
        set_connected(False)
        refresh_points()

    def do_scan() -> None:
        log("info", "looking for paired devices ...")

        def worker() -> None:
            devices = list_paired_devices()
            if not devices:
                post("info", "no paired devices found -- pair the phone in your "
                             "system Bluetooth settings, then try again")
                return
            for address, name in devices:
                post("info", f"paired: {address}  {name}")
            root.after(0, lambda: address_var.set(devices[0][0]))

        threading.Thread(target=worker, daemon=True).start()

    connect_button.configure(command=do_connect)
    disconnect_button.configure(command=do_disconnect)
    scan_button.configure(command=do_scan)
    add_button.configure(command=lambda: state["session"] and state["session"].add_point())
    remove_button.configure(command=lambda: state["session"] and state["session"].remove_point())
    exit_button.configure(command=lambda: state["session"] and state["session"].send_exit())

    def on_key(event) -> None:
        session = state["session"]
        if not session:
            return
        if event.char.isdigit() and event.char != "0":
            session.tap(int(event.char))
        elif event.char in "+=":
            session.add_point()
        elif event.char == "-":
            session.remove_point()

    root.bind("<Key>", on_key)

    def on_close() -> None:
        do_disconnect()
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close)

    set_connected(False)
    refresh_points()
    drain()

    log("info", "Phone: open the app, Bluetooth clicker, Start as Host, then "
                "As Service once connected.")

    if args.demo:
        phone = FakePhone(on_log=lambda m: post("phone", m))
        phone.start()
        state["phone"] = phone
        address_var.set("127.0.0.1")
        channel_var.set(str(phone.port))
        log("info", f"demo mode: fake phone listening on 127.0.0.1:{phone.port}")
        root.after(300, do_connect)

    root.mainloop()
    return 0


# ---------------------------------------------------------------------------
# Headless self-test
# ---------------------------------------------------------------------------


def run_selftest() -> int:
    """Drives the fake phone through the real encoder/decoder. No GUI, no radio."""
    messages: list[str] = []
    phone = FakePhone(on_log=messages.append)
    phone.start()

    transport = connect_tcp("127.0.0.1", phone.port, log=lambda m: None)
    events: list[tuple[str, str]] = []
    session = ControllerSession(transport, lambda kind, msg: events.append((kind, msg)))

    session.add_point()
    session.add_point()
    session.tap(1)
    session.tap(2)
    session.tap(5)          # no such point, must be ignored
    session.remove_point()
    session.tap(2)          # gone now, must be ignored
    session.send_exit()
    time.sleep(0.4)
    session.close()
    phone.join(timeout=2)

    print("--- fake phone ---")
    for line in messages:
        print(" ", line)

    taps = [m for m in messages if "TAP" in m]
    added = [m for m in messages if "added point" in m]
    ignored = [m for m in messages if "has no point" in m]

    problems = []
    if len(added) != 2:
        problems.append(f"expected 2 points added, saw {len(added)}")
    if len(taps) != 2:
        problems.append(f"expected 2 taps, saw {len(taps)}")
    if "point 1 at (16, 16)" not in " ".join(added):
        problems.append("first point was not placed at the expected inset")
    if taps and "TAP point 1 at (31, 31)" not in taps[0]:
        problems.append(f"button 1 tapped the wrong place: {taps[0]}")
    if len(ignored) != 2:
        problems.append(f"expected 2 ignored presses, saw {len(ignored)}")

    print("\n--- result ---")
    if problems:
        for problem in problems:
            print("  FAIL:", problem)
        return 1
    print("  OK: encoder, decoder, point placement and 1-based button mapping all agree")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--address", help="phone Bluetooth address, or TCP host")
    parser.add_argument("--channel", type=int, help="RFCOMM channel, or TCP port")
    parser.add_argument("--demo", action="store_true",
                        help="run against a built-in fake phone, no hardware needed")
    parser.add_argument("--selftest", action="store_true",
                        help="headless protocol check, no GUI needed")
    args = parser.parse_args()

    if args.selftest:
        return run_selftest()
    return run_gui(args)


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""
Floating Clicker -- desktop peer.

Stands in for whichever device you do not have. It speaks the exact byte
protocol implemented by BluetoothOperations.java, in either role:

  --role controller   the laptop sends commands; the phone runs "As Service"
                      and gets its screen tapped.
  --role service      the laptop receives commands and draws the click points;
                      the phone runs "As Controller" and drives them.

and in either connection direction:

  (default)           the laptop dials the phone; the phone is the Host.
  --listen            the laptop is the server; the phone connects as a Guest.

Examples:
  python3 clicker_controller.py --demo                    controller, fake phone
  python3 clicker_controller.py --role service --demo     service, fake phone
  python3 clicker_controller.py --role service            phone drives the laptop
  python3 clicker_controller.py --listen                  phone connects as Guest
  python3 clicker_controller.py --selftest                headless, no GUI

Standard library only. Tkinter is imported lazily so --selftest works without it.
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
    TYPE_TEXT: "TEXT", TYPE_BYTE: "BYTE", TYPE_SHORT: "SHORT", TYPE_CHAR: "CHAR",
    TYPE_INT: "INT", TYPE_LONG: "LONG", TYPE_FLOAT: "FLOAT", TYPE_DOUBLE: "DOUBLE",
    TYPE_RAW_CONTENT: "RAW", TYPE_SUCCESS: "SUCCESS", TYPE_EXIT: "EXIT",
}

# ClickerActivity.CLICKER_ADD_POINT / CLICKER_DELETE_POINT
CLICKER_ADD_POINT = -1
CLICKER_DELETE_POINT = -2

# HostActivity registers its service record with this UUID (res/values/strings.xml).
CLICKER_UUID = "1cf64473-3259-4cb7-8f91-5ebb341506cd"

_FIXED_WIDTH = {
    TYPE_BYTE: 1, TYPE_SHORT: 2, TYPE_CHAR: 2, TYPE_INT: 4,
    TYPE_LONG: 8, TYPE_FLOAT: 4, TYPE_DOUBLE: 8,
}
_STRUCT = {
    TYPE_BYTE: ">b", TYPE_SHORT: ">h", TYPE_INT: ">i",
    TYPE_LONG: ">q", TYPE_FLOAT: ">f", TYPE_DOUBLE: ">d",
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
    """Decode one frame. `read_exactly(n)` returns n bytes or raises EOFError."""
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
        return tag, struct.unpack(_STRUCT[tag], read_exactly(_FIXED_WIDTH[tag]))[0]
    raise ProtocolError(f"Unknown frame type {tag}")


def describe(tag: int, value: object) -> str:
    name = TYPE_NAMES.get(tag, f"TYPE_{tag}")
    if tag == TYPE_EXIT:
        return "EXIT  (peer closed the session)"
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
# Point placement -- a port of ClickPointLayout.java
# ---------------------------------------------------------------------------


class PointModel:
    """The click points, placed by the same rules the Android service uses."""

    POINT_SIZE = 30
    SPACING = 16

    def __init__(self, screen=(1080, 1920)):
        self.screen = screen
        self.points: list[tuple[int, int]] = []

    def next_position(self) -> tuple[int, int]:
        if not self.points:
            return self.SPACING, self.SPACING
        last_x, last_y = self.points[-1]
        max_x = self.screen[0] - self.POINT_SIZE
        next_x = last_x + self.POINT_SIZE + self.SPACING
        if next_x <= max_x:
            return next_x, last_y
        y = last_y + self.POINT_SIZE + self.SPACING
        if y > self.screen[1] - self.POINT_SIZE:
            y = self.SPACING
        return self.SPACING, y

    def add(self) -> tuple[int, int]:
        self.points.append(self.next_position())
        return self.points[-1]

    def remove_last(self) -> bool:
        if not self.points:
            return False
        self.points.pop()
        return True

    def centre_for_button(self, button: int) -> tuple[int, int] | None:
        """1-based button -> centre of the point it taps, or None."""
        index = button - 1                       # ClickPointLayout.indexForButton
        if not 0 <= index < len(self.points):
            return None
        x, y = self.points[index]
        half = self.POINT_SIZE // 2              # ClickPointLayout.centerOf
        return x + half, y + half

    def __len__(self) -> int:
        return len(self.points)


# ---------------------------------------------------------------------------
# Transports
# ---------------------------------------------------------------------------


class SocketTransport:
    def __init__(self, sock: socket.socket):
        self._sock = sock

    def send(self, data: bytes) -> None:
        self._sock.sendall(data)

    def recv(self, size: int) -> bytes:
        return self._sock.recv(size)

    def close(self) -> None:
        for action in (
            lambda: self._sock.shutdown(socket.SHUT_RDWR),
            self._sock.close,
        ):
            try:
                action()
            except OSError:
                pass

    def read_exactly(self, count: int) -> bytes:
        chunks = []
        remaining = count
        while remaining:
            chunk = self.recv(remaining)
            if not chunk:
                raise EOFError("connection closed by the peer")
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks)


def bluetooth_supported() -> bool:
    return hasattr(socket, "AF_BLUETOOTH") and hasattr(socket, "BTPROTO_RFCOMM")


def _require_bluetooth() -> None:
    if bluetooth_supported():
        return
    hint = "Use --demo, or the tcp transport."
    if platform.system() == "Windows":
        hint = ("Windows RFCOMM support landed in Python 3.9 (bpo-36590); "
                f"you are on {platform.python_version()}. Upgrade Python, or use --demo.")
    elif platform.system() == "Darwin":
        hint = "macOS CPython has no AF_BLUETOOTH at all. Use --demo, or the tcp transport."
    raise RuntimeError(
        f"This Python build has no Bluetooth socket support ({platform.system()}, "
        f"Python {platform.python_version()}). {hint}"
    )


def connect_bluetooth(address: str, channel: int | None = None, log=print) -> SocketTransport:
    """Dial the phone's RFCOMM service (phone must be on the Host screen)."""
    _require_bluetooth()
    probing = channel is None
    candidates = [channel] if channel else _channel_candidates(address, log)

    last_error: Exception | None = None
    for candidate in candidates:
        sock = None
        try:
            log(f"trying RFCOMM channel {candidate} ...")
            sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
            sock.settimeout(5 if probing else 15)
            sock.connect((address, candidate))
            sock.settimeout(None)
            log(f"connected on channel {candidate}")
            if probing:
                log("check the phone: if it still says 'Waiting for connection' we "
                    f"reached some other service -- Disconnect, put {candidate + 1} "
                    "in Chan/Port and Start again")
            return SocketTransport(sock)
        except Exception as error:  # noqa: BLE001 - report and keep probing
            last_error = error
            if sock is not None:
                try:
                    sock.close()
                except Exception:  # noqa: BLE001
                    pass
    raise RuntimeError(f"could not connect to {address}: {last_error}")


def _channel_candidates(address: str, log) -> list[int]:
    try:
        import bluetooth  # type: ignore  # PyBluez, optional

        services = bluetooth.find_service(uuid=CLICKER_UUID, address=address)
        if services:
            found = [s["port"] for s in services]
            log(f"SDP found the clicker service on channel(s) {found}")
            return found
        log("SDP found no clicker service; is the phone on the Host screen?")
    except ImportError:
        log("PyBluez not installed, probing channels instead (this is fine)")
    except Exception as error:  # noqa: BLE001
        log(f"SDP lookup failed ({error}), probing channels instead")
    return list(range(1, 31))


def listen_bluetooth(channel: int = 3, log=print) -> SocketTransport:
    """
    Accept an incoming RFCOMM connection, so the phone can be the Guest.

    The phone resolves the service by UUID over SDP, so the service record has
    to exist. PyBluez can publish one; without it we still listen, but most
    phones will not find us.
    """
    _require_bluetooth()

    advertised = False
    try:
        import bluetooth  # type: ignore

        server = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
        server.bind(("", bluetooth.PORT_ANY))
        server.listen(1)
        channel = server.getsockname()[1]
        bluetooth.advertise_service(
            server, "Floating Apps",
            service_id=CLICKER_UUID,
            service_classes=[CLICKER_UUID, bluetooth.SERIAL_PORT_CLASS],
            profiles=[bluetooth.SERIAL_PORT_PROFILE],
        )
        advertised = True
        log(f"advertising the clicker service on RFCOMM channel {channel}")
        log("on the phone: Connect as Guest, then pick this computer")
        client, info = server.accept()
        log(f"accepted a connection from {info}")
        server.close()
        return SocketTransport(client)
    except ImportError:
        log("PyBluez not installed -- listening without an SDP record")
    except Exception as error:  # noqa: BLE001
        if advertised:
            raise
        log(f"PyBluez listen failed ({error}), falling back to a raw socket")

    server = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    server.bind((getattr(socket, "BDADDR_ANY", ""), channel))
    server.listen(1)
    log(f"listening on RFCOMM channel {channel} with no SDP record")
    log("the phone will probably NOT find this; install PyBluez for the real thing")
    client, info = server.accept()
    log(f"accepted a connection from {info}")
    server.close()
    return SocketTransport(client)


def connect_tcp(host: str, port: int, log=print) -> SocketTransport:
    log(f"connecting to {host}:{port} ...")
    sock = socket.create_connection((host, port), timeout=10)
    sock.settimeout(None)
    log("connected")
    return SocketTransport(sock)


def listen_tcp(port: int, log=print) -> SocketTransport:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", port))
    server.listen(1)
    log(f"listening on TCP port {port} ...")
    client, info = server.accept()
    log(f"accepted a connection from {info}")
    server.close()
    return SocketTransport(client)


def list_paired_devices() -> list[tuple[str, str]]:
    """Best effort (address, name) list of paired devices. Never raises."""
    system = platform.system()
    try:
        if system == "Linux":
            out = subprocess.run(["bluetoothctl", "devices", "Paired"],
                                 capture_output=True, text=True, timeout=10).stdout
            if not out.strip():
                out = subprocess.run(["bluetoothctl", "paired-devices"],
                                     capture_output=True, text=True, timeout=10).stdout
            found = []
            for line in out.splitlines():
                match = re.match(r"Device\s+([0-9A-Fa-f:]{17})\s+(.*)", line.strip())
                if match:
                    found.append((match.group(1), match.group(2)))
            return found

        if system == "Windows":
            script = ("Get-PnpDevice -Class Bluetooth | "
                      "Where-Object {$_.InstanceId -match 'DEV_'} | "
                      "Select-Object -ExpandProperty InstanceId")
            out = subprocess.run(["powershell", "-NoProfile", "-Command", script],
                                 capture_output=True, text=True, timeout=20).stdout
            found = []
            for line in out.splitlines():
                match = re.search(r"DEV_([0-9A-Fa-f]{12})", line)
                if match:
                    raw = match.group(1).upper()
                    address = ":".join(raw[i:i + 2] for i in range(0, 12, 2))
                    if all(address != a for a, _ in found):
                        found.append((address, "paired device"))
            return found
    except Exception:  # noqa: BLE001 - discovery is a convenience, not a requirement
        pass
    return []


# ---------------------------------------------------------------------------
# Session -- shared by both roles
# ---------------------------------------------------------------------------


class Session:
    """
    Sends and decodes frames, keeping a point list in step with the peer.

    Both Android sides do the same bookkeeping: a point is added locally when
    our own write succeeds (ClickerActivity's pendingAddButton / TYPE_SUCCESS
    path) and also when the peer tells us it added one.
    """

    def __init__(self, transport: SocketTransport, on_event, model: PointModel | None = None):
        self._transport = transport
        self._on_event = on_event
        self._lock = threading.Lock()
        self._running = True
        self.model = model or PointModel()
        self._reader = threading.Thread(target=self._read_loop, daemon=True)
        self._reader.start()

    # -- outbound ----------------------------------------------------------

    def _emit(self, kind: str, message: str = "") -> None:
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
            self.model.add()
            self._emit("sent", f"add point  (now {len(self.model)})")
            self._emit("points")

    def remove_point(self) -> None:
        if self._send(encode_byte(CLICKER_DELETE_POINT)):
            self.model.remove_last()
            self._emit("sent", f"remove point  (now {len(self.model)})")
            self._emit("points")

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

    # -- inbound -----------------------------------------------------------

    def _read_loop(self) -> None:
        try:
            while self._running:
                tag, value = read_frame(self._transport.read_exactly)
                self._emit("recv", describe(tag, value))
                if tag == TYPE_EXIT:
                    break
                if tag != TYPE_BYTE:
                    continue
                if value == CLICKER_ADD_POINT:
                    self.model.add()
                    self._emit("points")
                elif value == CLICKER_DELETE_POINT:
                    self.model.remove_last()
                    self._emit("points")
                else:
                    centre = self.model.centre_for_button(value)
                    if centre is None:
                        self._emit("miss", f"button {value} has no point, ignoring")
                    else:
                        self._emit("tap", str(value))
        except EOFError:
            if self._running:
                self._emit("error", "the peer disconnected")
        except Exception as error:  # noqa: BLE001
            if self._running:
                self._emit("error", f"read failed: {error}")
        finally:
            self._running = False
            self._emit("closed")


# ---------------------------------------------------------------------------
# Fake peers, for --demo
# ---------------------------------------------------------------------------


class FakePeer(threading.Thread):
    """
    A stand-in for the phone, on a loopback TCP port.

    `role` is what the FAKE side plays, i.e. the opposite of the GUI's role.
    """

    def __init__(self, role: str, on_log):
        super().__init__(daemon=True)
        self.role = role
        self._on_log = on_log
        self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._server.bind(("127.0.0.1", 0))
        self._server.listen(1)
        self.port = self._server.getsockname()[1]
        self.model = PointModel()

    def run(self) -> None:
        conn, _ = self._server.accept()
        transport = SocketTransport(conn)
        self._on_log(f"fake phone ({self.role}): connected")
        try:
            if self.role == "controller":
                self._drive(transport)
            else:
                self._serve(transport)
        except EOFError:
            self._on_log("fake phone: peer went away")
        except Exception as error:  # noqa: BLE001
            self._on_log(f"fake phone: {error}")
        finally:
            transport.close()

    def _serve(self, transport: SocketTransport) -> None:
        """Play the accessibility service: receive commands, report the taps."""
        while True:
            tag, value = read_frame(transport.read_exactly)
            if tag == TYPE_EXIT:
                self._on_log("fake phone: session closed by the controller")
                return
            if tag != TYPE_BYTE:
                self._on_log(f"fake phone: ignoring {describe(tag, value)}")
                continue
            if value == CLICKER_ADD_POINT:
                self._on_log(f"fake phone: added point {len(self.model) + 1} at {self.model.add()}")
            elif value == CLICKER_DELETE_POINT:
                if self.model.remove_last():
                    self._on_log(f"fake phone: removed a point ({len(self.model)} left)")
                else:
                    self._on_log("fake phone: nothing to remove")
            else:
                centre = self.model.centre_for_button(value)
                if centre:
                    self._on_log(f"fake phone: TAP point {value} at {centre}")
                else:
                    self._on_log(f"fake phone: button {value} has no point, ignoring")

    def _drive(self, transport: SocketTransport) -> None:
        """Play the controller: run a little script so there is something to watch."""
        script = [
            (1.0, CLICKER_ADD_POINT, "press +"),
            (0.8, CLICKER_ADD_POINT, "press +"),
            (0.8, CLICKER_ADD_POINT, "press +"),
            (1.0, 1, "press button 1"),
            (1.0, 2, "press button 2"),
            (1.0, 3, "press button 3"),
            (1.0, CLICKER_DELETE_POINT, "press -"),
            (1.0, 3, "press button 3 (now out of range)"),
            (1.0, 2, "press button 2"),
        ]
        for delay, command, note in script:
            time.sleep(delay)
            self._on_log(f"fake phone: {note}")
            transport.send(encode_byte(command))
        self._on_log("fake phone: script finished, still connected")
        while True:                                  # stay up so the GUI can react
            if not transport.recv(1):
                raise EOFError


# ---------------------------------------------------------------------------
# GUI
# ---------------------------------------------------------------------------


def _import_tk():
    try:
        import tkinter as tk
        from tkinter import ttk
        return tk, ttk
    except ImportError:
        print(
            "Tkinter is missing.\n"
            "  Debian/Ubuntu : sudo apt install python3-tk\n"
            "  Fedora        : sudo dnf install python3-tkinter\n"
            "  Windows/macOS : reinstall Python with the Tcl/Tk option ticked\n"
            "Meanwhile, '--selftest' needs no GUI.",
            file=sys.stderr,
        )
        return None, None


def run_gui(args) -> int:
    tk, ttk = _import_tk()
    if tk is None:
        return 2

    role = args.role
    events: queue.Queue = queue.Queue()
    state: dict = {"session": None, "peer": None}

    root = tk.Tk()
    root.title(f"Floating Clicker -- desktop {role}")
    root.minsize(700, 640)

    # -- connection bar ----------------------------------------------------
    top = ttk.LabelFrame(root, text="Connection", padding=8)
    top.pack(fill="x", padx=10, pady=(10, 6))

    transport_var = tk.StringVar(value="tcp" if args.demo else "bluetooth")
    mode_var = tk.StringVar(value="listen" if args.listen else "connect")
    address_var = tk.StringVar(value=args.address or "")
    channel_var = tk.StringVar(value=str(args.channel) if args.channel else "")
    status_var = tk.StringVar(value="not connected")

    row = ttk.Frame(top)
    row.pack(fill="x")
    ttk.Label(row, text="Transport").pack(side="left")
    ttk.Combobox(row, textvariable=transport_var, values=["bluetooth", "tcp"],
                 width=10, state="readonly").pack(side="left", padx=(6, 12))
    ttk.Label(row, text="Mode").pack(side="left")
    ttk.Combobox(row, textvariable=mode_var, values=["connect", "listen"],
                 width=9, state="readonly").pack(side="left", padx=(6, 12))
    ttk.Label(row, text="Address").pack(side="left")
    ttk.Entry(row, textvariable=address_var, width=22).pack(side="left", padx=(6, 12))
    ttk.Label(row, text="Chan/Port").pack(side="left")
    ttk.Entry(row, textvariable=channel_var, width=8).pack(side="left", padx=(6, 0))

    row2 = ttk.Frame(top)
    row2.pack(fill="x", pady=(8, 0))
    scan_button = ttk.Button(row2, text="Find paired devices")
    scan_button.pack(side="left")
    connect_button = ttk.Button(row2, text="Start")
    connect_button.pack(side="left", padx=6)
    disconnect_button = ttk.Button(row2, text="Disconnect", state="disabled")
    disconnect_button.pack(side="left")
    ttk.Label(row2, textvariable=status_var, foreground="#666").pack(side="right")

    # -- role specific area ------------------------------------------------
    body = ttk.LabelFrame(
        root,
        text="Controls" if role == "controller" else "Phone screen (simulated)",
        padding=8,
    )
    body.pack(fill="both", expand=(role == "service"), padx=10, pady=6)

    points_var = tk.StringVar(value="0 points")
    widgets: dict = {}

    if role == "controller":
        buttons_row = ttk.Frame(body)
        buttons_row.pack(fill="x")
        widgets["add"] = ttk.Button(buttons_row, text="+  Add point", width=16)
        widgets["add"].pack(side="left")
        widgets["remove"] = ttk.Button(buttons_row, text="\u2212  Remove point", width=16)
        widgets["remove"].pack(side="left", padx=6)
        widgets["exit"] = ttk.Button(buttons_row, text="Send exit", width=12)
        widgets["exit"].pack(side="left")
        ttk.Label(buttons_row, textvariable=points_var).pack(side="right")
        ttk.Label(body, foreground="#666",
                  text="Tap a numbered button to click that point on the phone. "
                       "Keys 1-9 and +/- work too.").pack(anchor="w", pady=(8, 4))
        pad = ttk.Frame(body)
        pad.pack(fill="x")
        widgets["pad"] = pad
        widgets["taps"] = []
    else:
        info = ttk.Frame(body)
        info.pack(fill="x")
        ttk.Label(info, foreground="#666",
                  text="Whatever the phone's controller sends is drawn here. "
                       "The + and - buttons mirror the floating toolbar.").pack(side="left")
        ttk.Label(info, textvariable=points_var).pack(side="right")

        toolbar = ttk.Frame(body)
        toolbar.pack(fill="x", pady=(6, 6))
        widgets["add"] = ttk.Button(toolbar, text="+  Add point", width=16)
        widgets["add"].pack(side="left")
        widgets["remove"] = ttk.Button(toolbar, text="\u2212  Remove point", width=16)
        widgets["remove"].pack(side="left", padx=6)
        widgets["exit"] = ttk.Button(toolbar, text="Close session", width=14)
        widgets["exit"].pack(side="left")

        canvas = tk.Canvas(body, width=300, height=520, bg="#101418",
                           highlightthickness=1, highlightbackground="#333")
        canvas.pack(pady=(4, 0))
        widgets["canvas"] = canvas

    # -- log ---------------------------------------------------------------
    log_frame = ttk.LabelFrame(root, text="Traffic", padding=8)
    log_frame.pack(fill="both", expand=True, padx=10, pady=(6, 10))
    log_text = tk.Text(log_frame, height=10, wrap="word", state="disabled")
    scrollbar = ttk.Scrollbar(log_frame, command=log_text.yview)
    log_text.configure(yscrollcommand=scrollbar.set)
    log_text.pack(side="left", fill="both", expand=True)
    scrollbar.pack(side="right", fill="y")
    for tag, colour in (("sent", "#0a6b0a"), ("recv", "#0a3f8b"), ("error", "#b00020"),
                        ("info", "#555555"), ("phone", "#7a4a00"), ("tap", "#a0007a"),
                        ("miss", "#8a6d00")):
        log_text.tag_configure(tag, foreground=colour)

    def log(kind: str, message: str) -> None:
        if not message:
            return
        arrow = {"sent": "-> ", "recv": "<- ", "error": "!! ",
                 "phone": " * ", "tap": " # ", "miss": " ? "}.get(kind, "   ")
        log_text.configure(state="normal")
        log_text.insert("end", f"{time.strftime('%H:%M:%S')} {arrow}{message}\n", kind)
        log_text.see("end")
        log_text.configure(state="disabled")

    def post(kind: str, message: str = "") -> None:
        events.put((kind, message))

    # -- rendering ---------------------------------------------------------

    SCALE = 300 / 1080

    def redraw_canvas(flash: int | None = None) -> None:
        canvas = widgets["canvas"]
        canvas.delete("all")
        session = state["session"]
        model = session.model if session else PointModel()
        size = max(10, int(PointModel.POINT_SIZE * SCALE * 2.2))
        for number, (x, y) in enumerate(model.points, start=1):
            cx = x * SCALE + size / 2
            cy = y * SCALE + size / 2
            hot = (flash == number)
            canvas.create_oval(
                cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2,
                fill="#ff4d4d" if hot else "#2d6cdf",
                outline="#ffffff" if hot else "#8ab4ff", width=2 if hot else 1,
            )
            canvas.create_text(cx, cy, text=str(number), fill="white")
        if not model.points:
            canvas.create_text(150, 260, fill="#666",
                               text="no points yet\nsend + from the phone")

    def refresh_points() -> None:
        session = state["session"]
        count = len(session.model) if session else 0
        points_var.set(f"{count} point{'' if count == 1 else 's'}")
        if role == "service":
            redraw_canvas()
            return
        for widget in widgets["taps"]:
            widget.destroy()
        widgets["taps"].clear()
        for number in range(1, count + 1):
            button = ttk.Button(
                widgets["pad"], text=str(number), width=4,
                command=lambda n=number: state["session"] and state["session"].tap(n),
            )
            button.grid(row=(number - 1) // 10, column=(number - 1) % 10, padx=2, pady=2)
            widgets["taps"].append(button)

    def flash_point(number: int) -> None:
        if role != "service":
            return
        redraw_canvas(flash=number)
        root.after(220, lambda: redraw_canvas())

    def set_connected(connected: bool) -> None:
        connect_button.configure(state="disabled" if connected else "normal")
        disconnect_button.configure(state="normal" if connected else "disabled")
        for key in ("add", "remove", "exit"):
            widgets[key].configure(state="normal" if connected else "disabled")
        status_var.set("connected" if connected else "not connected")

    def drain() -> None:
        try:
            while True:
                kind, message = events.get_nowait()
                if kind == "points":
                    refresh_points()
                elif kind == "tap":
                    number = int(message)
                    log("tap", f"TAP point {number}")
                    flash_point(number)
                elif kind == "closed":
                    set_connected(False)
                    log("info", "session closed")
                elif kind == "connected":
                    set_connected(True)
                else:
                    log(kind, message)
        except queue.Empty:
            pass
        root.after(60, drain)

    # -- actions -----------------------------------------------------------

    def do_start() -> None:
        set_connected(False)
        status_var.set("waiting ..." if mode_var.get() == "listen" else "connecting ...")
        connect_button.configure(state="disabled")

        transport_kind = transport_var.get()
        listening = mode_var.get() == "listen"
        address = address_var.get().strip()
        channel_text = channel_var.get().strip()

        def worker() -> None:
            try:
                if transport_kind == "bluetooth":
                    if listening:
                        transport = listen_bluetooth(
                            int(channel_text) if channel_text else 3,
                            log=lambda m: post("info", m))
                    else:
                        if not address:
                            raise RuntimeError("enter the phone's Bluetooth address first")
                        transport = connect_bluetooth(
                            address, int(channel_text) if channel_text else None,
                            log=lambda m: post("info", m))
                else:
                    if not channel_text:
                        raise RuntimeError("enter a TCP port")
                    if listening:
                        transport = listen_tcp(int(channel_text), log=lambda m: post("info", m))
                    else:
                        transport = connect_tcp(address or "127.0.0.1", int(channel_text),
                                                log=lambda m: post("info", m))
            except Exception as error:  # noqa: BLE001
                post("error", str(error))
                post("closed")
                return

            state["session"] = Session(transport, post)
            post("info", "ready" if role == "service" else
                 "ready -- add a point, then tap its number")
            post("points")
            post("connected")

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

    connect_button.configure(command=do_start)
    disconnect_button.configure(command=do_disconnect)
    scan_button.configure(command=do_scan)
    widgets["add"].configure(command=lambda: state["session"] and state["session"].add_point())
    widgets["remove"].configure(command=lambda: state["session"] and state["session"].remove_point())
    widgets["exit"].configure(command=lambda: state["session"] and state["session"].send_exit())

    if role == "controller":
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

    if role == "controller":
        log("info", "Phone: app -> Bluetooth clicker -> Start as Host, "
                    "then As Service once connected.")
    else:
        log("info", "Phone: app -> Bluetooth clicker -> Start as Host, "
                    "then As Controller once connected.")

    if args.demo:
        peer = FakePeer("service" if role == "controller" else "controller",
                        on_log=lambda m: post("phone", m))
        peer.start()
        state["peer"] = peer
        transport_var.set("tcp")
        mode_var.set("connect")
        address_var.set("127.0.0.1")
        channel_var.set(str(peer.port))
        log("info", f"demo mode: fake phone listening on 127.0.0.1:{peer.port}")
        root.after(300, do_start)

    root.mainloop()
    return 0


# ---------------------------------------------------------------------------
# Headless self-test
# ---------------------------------------------------------------------------


def _run_direction(role: str) -> tuple[list[str], list[tuple[str, str]]]:
    """Wires the GUI-side Session to a fake peer playing the opposite role."""
    messages: list[str] = []
    peer = FakePeer("service" if role == "controller" else "controller",
                    on_log=messages.append)
    peer.start()

    transport = connect_tcp("127.0.0.1", peer.port, log=lambda m: None)
    events: list[tuple[str, str]] = []
    session = Session(transport, lambda kind, msg="": events.append((kind, msg)))

    if role == "controller":
        session.add_point()
        session.add_point()
        session.tap(1)
        session.tap(2)
        session.tap(5)           # no such point
        session.remove_point()
        session.tap(2)           # gone now
        session.send_exit()
        time.sleep(0.4)
    else:
        time.sleep(9.5)          # let the fake controller's script finish

    session.close()
    peer.join(timeout=2)
    return messages, events


def run_selftest() -> int:
    problems: list[str] = []

    print("=== laptop as CONTROLLER, fake phone as service ===")
    messages, _ = _run_direction("controller")
    for line in messages:
        print("  ", line)
    taps = [m for m in messages if "TAP" in m]
    added = [m for m in messages if "added point" in m]
    ignored = [m for m in messages if "has no point" in m]
    if len(added) != 2:
        problems.append(f"controller: expected 2 points added, saw {len(added)}")
    if len(taps) != 2:
        problems.append(f"controller: expected 2 taps, saw {len(taps)}")
    if "point 1 at (16, 16)" not in " ".join(added):
        problems.append("controller: first point was not placed at the expected inset")
    if taps and "TAP point 1 at (31, 31)" not in taps[0]:
        problems.append(f"controller: button 1 tapped the wrong place: {taps[0]}")
    if len(ignored) != 2:
        problems.append(f"controller: expected 2 ignored presses, saw {len(ignored)}")

    print("\n=== laptop as SERVICE, fake phone as controller ===")
    messages, events = _run_direction("service")
    for line in messages:
        print("  ", line)
    kinds = [k for k, _ in events]
    taps = [msg for kind, msg in events if kind == "tap"]
    misses = [msg for kind, msg in events if kind == "miss"]
    print("   received taps:", taps, " ignored:", len(misses))
    if taps != ["1", "2", "3", "2"]:
        problems.append(f"service: expected taps 1,2,3 then 2, saw {taps}")
    if len(misses) != 1:
        problems.append(f"service: expected 1 out-of-range press, saw {len(misses)}")
    if kinds.count("points") < 4:
        problems.append("service: point list did not track the peer's add/remove")

    print("\n--- result ---")
    if problems:
        for problem in problems:
            print("  FAIL:", problem)
        return 1
    print("  OK: both directions agree on framing, point placement and the "
          "1-based button mapping")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--role", choices=["controller", "service"], default="controller",
                        help="what the laptop plays (default: controller)")
    parser.add_argument("--listen", action="store_true",
                        help="accept an incoming connection instead of dialling out")
    parser.add_argument("--address", help="phone Bluetooth address, or TCP host")
    parser.add_argument("--channel", type=int, help="RFCOMM channel, or TCP port")
    parser.add_argument("--demo", action="store_true",
                        help="run against a built-in fake phone, no hardware needed")
    parser.add_argument("--selftest", action="store_true",
                        help="headless check of both directions, no GUI needed")
    args = parser.parse_args()

    if args.selftest:
        return run_selftest()
    return run_gui(args)


if __name__ == "__main__":
    sys.exit(main())

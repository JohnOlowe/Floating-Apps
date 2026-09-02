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
import json
import os
import subprocess
import sys
import threading
import time
import uuid as uuid_module

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

# Extended text commands, mirroring ClickerCommand.java.
#   @SIZE,w,h        service -> controller: the phone's real screen size in pixels
#   @MOVE,i,x,y      either side: move point i so its top-left is at (x, y)
#   @SWIPE,x1,y1,x2,y2,duration   controller -> service: swipe between two points
COMMAND_PREFIX = "@"
CMD_SIZE = "SIZE"
CMD_MOVE = "MOVE"
CMD_SWIPE = "SWIPE"
DEFAULT_SWIPE_MS = 300

# HostActivity registers its service record with this UUID (res/values/strings.xml).
CLICKER_UUID = "1cf64473-3259-4cb7-8f91-5ebb341506cd"

# How long a freshly connected channel must stay silent to be believed.
PROBE_SETTLE_SECONDS = 1.2

# How long to wait for a probed channel to accept before moving on.
PROBE_CONNECT_SECONDS = 4

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


# --- extended text commands (see ClickerCommand.java) -----------------------


def cmd_size(width: int, height: int) -> str:
    return f"{COMMAND_PREFIX}{CMD_SIZE},{width},{height}"


def cmd_move(index: int, x: int, y: int) -> str:
    return f"{COMMAND_PREFIX}{CMD_MOVE},{index},{x},{y}"


def cmd_swipe(x1: int, y1: int, x2: int, y2: int, duration_ms: int) -> str:
    return f"{COMMAND_PREFIX}{CMD_SWIPE},{x1},{y1},{x2},{y2},{duration_ms}"


def parse_command(text: str) -> tuple[str, tuple[int, ...]]:
    """Split an extended command into (kind, ints); raises for ordinary text."""
    if not is_command(text):
        raise ProtocolError(f"not a clicker command: {text!r}")
    parts = text[1:].split(",")
    kind = parts[0]
    if kind not in (CMD_SIZE, CMD_MOVE, CMD_SWIPE):
        raise ProtocolError(f"unknown clicker command {kind!r}")
    try:
        args = tuple(int(p.strip()) for p in parts[1:])
    except ValueError as error:
        raise ProtocolError(f"non-numeric argument in {text!r}") from error
    expected = {CMD_SIZE: 2, CMD_MOVE: 3, CMD_SWIPE: 5}[kind]
    if len(args) != expected:
        raise ProtocolError(f"{kind} needs {expected} numbers, got {text!r}")
    return kind, args


def is_command(text: object) -> bool:
    return isinstance(text, str) and len(text) > 1 and text[0] == COMMAND_PREFIX


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
    if tag == TYPE_TEXT:
        try:
            if is_command(value):
                kind, args = parse_command(value)
                if kind == CMD_MOVE:
                    index, x, y = args
                    return f"CMD   move point {index + 1} to ({x}, {y})"
                if kind == CMD_SWIPE:
                    x1, y1, x2, y2, duration = args
                    return f"CMD   swipe ({x1},{y1}) -> ({x2},{y2}) over {duration}ms"
                if kind == CMD_SIZE:
                    return f"CMD   phone screen is {args[0]}x{args[1]}"
        except ProtocolError:
            pass
        return f"TEXT  {value!r}"
    if tag == TYPE_RAW_CONTENT:
        return f"RAW   {len(value)} bytes"
    return f"{name}  {value!r}"


# ---------------------------------------------------------------------------
# Point placement -- a port of ClickPointLayout.java
# ---------------------------------------------------------------------------


class PointModel:
    """The click points, placed by the same rules the Android service uses.

    Positions are top-left corners in the PHONE's absolute screen pixels, which
    is the coordinate space dispatchGesture and the @MOVE/@SWIPE commands use.
    """

    POINT_SIZE = 30
    SPACING = 16

    def __init__(self, screen=(1080, 1920)):
        # The phone announces its real size with @SIZE when a session starts.
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

    def move(self, index: int, x: int, y: int) -> bool:
        """Put point `index` (0-based) at the absolute top-left (x, y)."""
        if not 0 <= index < len(self.points):
            return False
        half = self.POINT_SIZE // 2
        x = max(0, min(int(x), self.screen[0] - self.POINT_SIZE))
        y = max(0, min(int(y), self.screen[1] - self.POINT_SIZE))
        self.points[index] = (x, y)
        return True

    def set_screen(self, width: int, height: int) -> None:
        self.screen = (max(1, int(width)), max(1, int(height)))

    def centre_for_button(self, button: int) -> tuple[int, int] | None:
        """1-based button -> centre of the point it taps, or None."""
        return self.centre_for_index(button - 1)

    def centre_for_index(self, index: int) -> tuple[int, int] | None:
        """0-based point -> its absolute centre, or None."""
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



# ---------------------------------------------------------------------------
# Finding the phone's RFCOMM channel
# ---------------------------------------------------------------------------

CHANNEL_CACHE = os.path.join(
    os.path.expanduser("~"), ".floating_clicker_channels.json"
)


def _load_cached_channel(address: str) -> int | None:
    try:
        with open(CHANNEL_CACHE, "r", encoding="utf-8") as handle:
            return json.load(handle).get(address.upper())
    except Exception:  # noqa: BLE001 - the cache is a convenience
        return None


def _save_cached_channel(address: str, channel: int) -> None:
    data = {}
    try:
        with open(CHANNEL_CACHE, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    except Exception:  # noqa: BLE001
        pass
    data[address.upper()] = channel
    try:
        with open(CHANNEL_CACHE, "w", encoding="utf-8") as handle:
            json.dump(data, handle)
    except Exception:  # noqa: BLE001
        pass


def _sdp_lookup_windows(address: str, log) -> list[int]:
    """
    Ask Windows for the RFCOMM channel of our service UUID on a remote device.

    This is the same WSALookupService* SDP query PyBluez performs, reached through
    ctypes so that nothing has to be compiled or installed.
    """
    import ctypes
    from ctypes import wintypes

    class GUID(ctypes.Structure):
        _fields_ = [
            ("Data1", wintypes.DWORD),
            ("Data2", wintypes.WORD),
            ("Data3", wintypes.WORD),
            ("Data4", ctypes.c_ubyte * 8),
        ]

    class SOCKADDR_BTH(ctypes.Structure):
        _fields_ = [
            ("addressFamily", wintypes.USHORT),
            ("btAddr", ctypes.c_ulonglong),
            ("serviceClassId", GUID),
            ("port", wintypes.ULONG),
        ]

    class SOCKET_ADDRESS(ctypes.Structure):
        _fields_ = [("lpSockaddr", ctypes.c_void_p), ("iSockaddrLength", ctypes.c_int)]

    class CSADDR_INFO(ctypes.Structure):
        _fields_ = [
            ("LocalAddr", SOCKET_ADDRESS),
            ("RemoteAddr", SOCKET_ADDRESS),
            ("iSocketType", ctypes.c_int),
            ("iProtocol", ctypes.c_int),
        ]

    class BLOB(ctypes.Structure):
        _fields_ = [("cbSize", wintypes.ULONG), ("pBlobData", ctypes.c_void_p)]

    class WSAQUERYSETW(ctypes.Structure):
        _fields_ = [
            ("dwSize", wintypes.DWORD),
            ("lpszServiceInstanceName", wintypes.LPWSTR),
            ("lpServiceClassId", ctypes.POINTER(GUID)),
            ("lpVersion", ctypes.c_void_p),
            ("lpszComment", wintypes.LPWSTR),
            ("dwNameSpace", wintypes.DWORD),
            ("lpNSProviderId", ctypes.POINTER(GUID)),
            ("lpszContext", wintypes.LPWSTR),
            ("dwNumberOfProtocols", wintypes.DWORD),
            ("lpafpProtocols", ctypes.c_void_p),
            ("lpszQueryString", wintypes.LPWSTR),
            ("dwNumberOfCsAddrs", wintypes.DWORD),
            ("lpcsaBuffer", ctypes.POINTER(CSADDR_INFO)),
            ("dwOutputFlags", wintypes.DWORD),
            ("lpBlob", ctypes.POINTER(BLOB)),
        ]

    NS_BTH = 16
    LUP_RETURN_ADDR = 0x0100
    LUP_FLUSHCACHE = 0x1000
    SOCKET_ERROR = -1

    ws2 = ctypes.WinDLL("ws2_32", use_last_error=True)

    fields = uuid_module.UUID(CLICKER_UUID).fields
    guid = GUID()
    guid.Data1, guid.Data2, guid.Data3 = fields[0], fields[1], fields[2]
    tail = uuid_module.UUID(CLICKER_UUID).bytes[8:]
    guid.Data4 = (ctypes.c_ubyte * 8)(*tail)

    query = WSAQUERYSETW()
    ctypes.memset(ctypes.byref(query), 0, ctypes.sizeof(query))
    query.dwSize = ctypes.sizeof(WSAQUERYSETW)
    query.dwNameSpace = NS_BTH
    query.lpServiceClassId = ctypes.pointer(guid)
    query.lpszContext = f"({address.upper()})"

    handle = wintypes.HANDLE()
    if ws2.WSALookupServiceBeginW(
        ctypes.byref(query), LUP_RETURN_ADDR | LUP_FLUSHCACHE, ctypes.byref(handle)
    ) == SOCKET_ERROR:
        raise OSError(ctypes.get_last_error(), "WSALookupServiceBegin failed")

    channels: list[int] = []
    try:
        size = wintypes.DWORD(8192)
        buffer = ctypes.create_string_buffer(size.value)
        while True:
            size.value = ctypes.sizeof(buffer)
            result = ws2.WSALookupServiceNextW(
                handle, LUP_RETURN_ADDR, ctypes.byref(size), buffer
            )
            if result == SOCKET_ERROR:
                break
            found = ctypes.cast(buffer, ctypes.POINTER(WSAQUERYSETW)).contents
            for index in range(found.dwNumberOfCsAddrs):
                remote = found.lpcsaBuffer[index].RemoteAddr
                if not remote.lpSockaddr:
                    continue
                bth = ctypes.cast(
                    remote.lpSockaddr, ctypes.POINTER(SOCKADDR_BTH)
                ).contents
                if bth.port and bth.port not in channels:
                    channels.append(int(bth.port))
    finally:
        ws2.WSALookupServiceEnd(handle)

    return channels


def _sdp_lookup(address: str, log) -> list[int]:
    """Resolve the clicker service's RFCOMM channel. Returns [] if it cannot."""
    try:
        import bluetooth  # type: ignore  # PyBluez, optional

        services = bluetooth.find_service(uuid=CLICKER_UUID, address=address)
        if services:
            found = [s["port"] for s in services]
            log(f"SDP: clicker service is on channel(s) {found}")
            return found
        log("SDP: the phone is not advertising the clicker service right now")
        return []
    except ImportError:
        pass
    except Exception as error:  # noqa: BLE001
        log(f"SDP lookup via PyBluez failed ({error})")

    if platform.system() == "Windows":
        try:
            channels = _sdp_lookup_windows(address, log)
            if channels:
                log(f"SDP: clicker service is on channel(s) {channels}")
            else:
                log("SDP: Windows found no clicker service on that device "
                    "(is the phone on the Host screen?)")
            return channels
        except Exception as error:  # noqa: BLE001
            log(f"SDP lookup failed ({error}); falling back to probing")
    return []


def _speaks_first(sock: socket.socket, settle: float, log) -> bool:
    """
    True when the peer sends something unprompted, which our app never does.

    The clicker protocol is silent until a button is pressed, so any traffic
    arriving right after connecting means we reached a different Bluetooth
    profile (OBEX, phonebook, handsfree and friends all greet you).
    """
    previous = sock.gettimeout()
    sock.settimeout(settle)
    try:
        data = sock.recv(64)
    except socket.timeout:
        sock.settimeout(previous)
        return False
    except OSError:
        return True
    finally:
        try:
            sock.settimeout(previous)
        except OSError:
            pass
    if not data:
        return True                     # closed on us
    log(f"    channel spoke first ({data[:16]!r}) -- not the clicker")
    return True


def parse_channels(text: str) -> list[int]:
    """
    Turn a Chan/Port entry into a list of channels.

    Accepts a single number, a comma separated list, ranges, or any mixture of
    those: "5", "5,9", "5-12", "5, 8-11, 20". Blank means "work it out".
    """
    channels: list[int] = []
    for chunk in text.replace(" ", "").split(","):
        if not chunk:
            continue
        if "-" in chunk[1:]:
            first, _, last = chunk.partition("-")
            step = 1 if int(last) >= int(first) else -1
            for value in range(int(first), int(last) + step, step):
                if value not in channels:
                    channels.append(value)
        elif chunk.isdigit():
            if int(chunk) not in channels:
                channels.append(int(chunk))
        else:
            raise ValueError(f"'{chunk}' is not a channel number or range")
    return channels


class ChannelSearch:
    """
    A resumable walk through the channels the phone might be listening on.

    The point of this being an object rather than a loop is that a connection
    can be rejected after the fact. If a channel accepts us but turns out to be
    the wrong service, ``next_connection`` picks up at the following candidate
    instead of starting over.
    """

    PROBE_RANGE = range(1, 31)

    def __init__(self, address: str, explicit: list[int] | None = None, log=print):
        self.address = address
        self.log = log
        self.cancelled = False
        self.tried: list[int] = []
        self._current_sock: socket.socket | None = None
        self._extended = False
        # (channel, verify). Channels typed by hand are taken at face value.
        self._queue: list[tuple[int, bool]] = [(c, False) for c in (explicit or [])]

    # -- candidate list ----------------------------------------------------

    def _extend(self) -> None:
        """Append the automatic candidates: SDP first, then cache, then a sweep."""
        if self._extended:
            return
        self._extended = True
        known = {channel for channel, _ in self._queue} | set(self.tried)

        for channel in _sdp_lookup(self.address, self.log):
            if channel not in known:
                self._queue.append((channel, False))
                known.add(channel)

        remembered = _load_cached_channel(self.address)
        if remembered and remembered not in known:
            self.log(f"channel {remembered} worked last time, trying it early")
            self._queue.append((remembered, True))
            known.add(remembered)

        for channel in self.PROBE_RANGE:
            if channel not in known:
                self._queue.append((channel, True))

    @property
    def remaining(self) -> int:
        return len(self._queue)

    # -- control -----------------------------------------------------------

    def cancel(self) -> None:
        """Stop the search, interrupting a connect that is already in flight."""
        self.cancelled = True
        sock = self._current_sock
        if sock is not None:
            try:
                sock.close()
            except OSError:
                pass

    # -- the search itself -------------------------------------------------

    def next_connection(self) -> tuple[SocketTransport, int]:
        """Connect to the next plausible channel, or raise if there are none left."""
        _require_bluetooth()
        while True:
            if self.cancelled:
                raise RuntimeError("search stopped")
            if not self._queue:
                self._extend()
            if not self._queue:
                raise RuntimeError(
                    f"tried every channel on {self.address} without finding the "
                    "clicker. Check the phone is showing 'Waiting for connection'."
                )
            channel, verify = self._queue.pop(0)
            self.tried.append(channel)
            result = self._attempt(channel, verify)
            if result is not None:
                _save_cached_channel(self.address, channel)
                return result, channel

    def _attempt(self, channel: int, verify: bool) -> SocketTransport | None:
        sock = None
        try:
            self.log(f"trying RFCOMM channel {channel} ...")
            sock = socket.socket(
                socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM
            )
            self._current_sock = sock
            sock.settimeout(PROBE_CONNECT_SECONDS if verify else 15)
            sock.connect((self.address, channel))
            if verify and _speaks_first(sock, PROBE_SETTLE_SECONDS, self.log):
                sock.close()
                self._current_sock = None
                return None
            sock.settimeout(None)
            self._current_sock = None
            self.log(f"connected on channel {channel}")
            return SocketTransport(sock)
        except Exception as error:  # noqa: BLE001 - probing fails often by design
            self._current_sock = None
            if sock is not None:
                try:
                    sock.close()
                except OSError:
                    pass
            if not self.cancelled:
                self.log(f"    channel {channel}: {error}")
            return None


def connect_bluetooth(address: str, channel: int | None = None, log=print) -> SocketTransport:
    """Dial the phone's RFCOMM service (the phone must be on the Host screen)."""
    search = ChannelSearch(address, [channel] if channel else None, log=log)
    transport, _ = search.next_connection()
    return transport


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

    def move_point(self, index: int, x: int, y: int) -> bool:
        """
        Move a point on the peer's screen to absolute coordinates and keep our
        own copy in the same place. Used when the user drags a point here.
        """
        if not self.model.move(index, x, y):
            return False
        if self._send(encode_text(cmd_move(index, int(x), int(y)))):
            self._emit("sent", f"move point {index + 1} to ({int(x)}, {int(y)})")
            self._emit("points")
        return True

    def swipe(self, from_index: int, to_index: int,
              duration_ms: int = DEFAULT_SWIPE_MS) -> None:
        """Swipe from the centre of one point to the centre of another."""
        start = self.model.centre_for_index(from_index)
        end = self.model.centre_for_index(to_index)
        if start is None or end is None:
            self._emit("error", "swipe needs two existing points")
            return
        self.swipe_coords(start[0], start[1], end[0], end[1], duration_ms)

    def swipe_coords(self, x1: int, y1: int, x2: int, y2: int,
                     duration_ms: int = DEFAULT_SWIPE_MS) -> None:
        if self._send(encode_text(cmd_swipe(x1, y1, x2, y2, int(duration_ms)))):
            self._emit("sent",
                       f"swipe ({x1},{y1}) -> ({x2},{y2}) over {duration_ms}ms")
            self._emit("swipe", f"{x1},{y1},{x2},{y2}")

    def send_exit(self) -> None:
        if self._send(encode_exit()):
            self._emit("sent", "exit")

    def close(self) -> None:
        self._running = False
        self._transport.close()

    # -- inbound -----------------------------------------------------------

    def _handle_command(self, text: str) -> None:
        """Apply an extended command the peer sent, and echo it to the log."""
        try:
            kind, args = parse_command(text)
        except ProtocolError as error:
            self._emit("error", f"bad command from peer: {error}")
            return
        self._emit("recv", describe(TYPE_TEXT, text))
        if kind == CMD_SIZE:
            width, height = args
            self.model.set_screen(width, height)
            self._emit("screen", f"{width},{height}")
        elif kind == CMD_MOVE:
            index, x, y = args
            # A MOVE can arrive for a point our optimistic bookkeeping has not
            # grown into yet (or after a late @SIZE); grow to cover it first.
            if index >= 0:
                while len(self.model) < index + 1:
                    self.model.add()
                if self.model.move(index, x, y):
                    self._emit("points")
        elif kind == CMD_SWIPE:
            x1, y1, x2, y2, _duration = args
            self._emit("swipe", f"{x1},{y1},{x2},{y2}")

    def _read_loop(self) -> None:
        try:
            while self._running:
                tag, value = read_frame(self._transport.read_exactly)
                if not (tag == TYPE_TEXT and is_command(value)):
                    self._emit("recv", describe(tag, value))
                if tag == TYPE_EXIT:
                    break
                if tag == TYPE_TEXT and is_command(value):
                    self._handle_command(value)
                    continue
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
        # A real service announces its screen size as soon as a session starts.
        transport.send(encode_text(cmd_size(*self.model.screen)))
        while True:
            tag, value = read_frame(transport.read_exactly)
            if tag == TYPE_EXIT:
                self._on_log("fake phone: session closed by the controller")
                return
            if tag == TYPE_TEXT and is_command(value):
                kind, args = parse_command(value)
                if kind == CMD_MOVE:
                    index, x, y = args
                    if self.model.move(index, x, y):
                        self._on_log(f"fake phone: MOVED point {index + 1} to ({x}, {y})")
                elif kind == CMD_SWIPE:
                    x1, y1, x2, y2, duration = args
                    self._on_log(
                        f"fake phone: SWIPE ({x1},{y1}) -> ({x2},{y2}) over {duration}ms")
                continue
            if tag != TYPE_BYTE:
                self._on_log(f"fake phone: ignoring {describe(tag, value)}")
                continue
            if value == CLICKER_ADD_POINT:
                pos = self.model.add()
                self._on_log(f"fake phone: added point {len(self.model)} at {pos}")
                # The service reports where the point really landed.
                transport.send(encode_text(cmd_move(len(self.model) - 1, pos[0], pos[1])))
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
        # Delays are kept short: this also runs inside --selftest.
        script = [
            (0.6, encode_byte(CLICKER_ADD_POINT), "press +"),
            (0.4, encode_byte(CLICKER_ADD_POINT), "press +"),
            (0.4, encode_text(cmd_size(1080, 1920)), "announce screen size"),
            (0.4, encode_text(cmd_move(0, 400, 700)), "reposition point 1"),
            (0.4, encode_text(cmd_move(1, 100, 1200)), "reposition point 2"),
            (0.4, encode_text(cmd_swipe(415, 715, 115, 1215, 300)), "swipe 1 -> 2"),
            (0.4, encode_byte(1), "press button 1"),
            (0.4, encode_byte(2), "press button 2"),
            (0.4, encode_byte(CLICKER_DELETE_POINT), "press -"),
            (0.4, encode_byte(2), "press button 2 (the old point 3)"),
        ]
        for delay, payload, note in script:
            time.sleep(delay)
            self._on_log(f"fake phone: {note}")
            transport.send(payload)
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
    state: dict = {"session": None, "peer": None, "search": None, "searching": False}

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
    channel_entry = ttk.Entry(row, textvariable=channel_var, width=12)
    channel_entry.pack(side="left", padx=(6, 0))
    ttk.Label(row, text="(blank = auto; 5,8-12 also fine)",
              foreground="#888").pack(side="left", padx=(6, 0))

    row2 = ttk.Frame(top)
    row2.pack(fill="x", pady=(8, 0))
    scan_button = ttk.Button(row2, text="Find paired devices")
    scan_button.pack(side="left")
    connect_button = ttk.Button(row2, text="Start")
    connect_button.pack(side="left", padx=6)
    disconnect_button = ttk.Button(row2, text="Disconnect", state="disabled")
    disconnect_button.pack(side="left")
    next_button = ttk.Button(row2, text="Wrong one \u2014 try next", state="disabled")
    next_button.pack(side="left", padx=6)
    ttk.Label(row2, textvariable=status_var, foreground="#666").pack(side="right")

    # -- role specific area ------------------------------------------------
    body = ttk.LabelFrame(
        root,
        text="Controls" if role == "controller" else "Phone screen (simulated)",
        padding=8,
    )
    body.pack(fill="both", expand=True, padx=10, pady=6)

    points_var = tk.StringVar(value="0 points")
    hint_var = tk.StringVar(value="")
    widgets: dict = {}
    canvas_state = {"drag": None, "swipe_from": None, "swipe_line": None}

    CANVAS_W = 300
    CANVAS_H = 520

    def phone_scale() -> float:
        session = state["session"]
        screen = session.model.screen if session else (1080, 1920)
        return min(CANVAS_W / screen[0], CANVAS_H / screen[1])

    def to_canvas(x: float, y: float) -> tuple[float, float]:
        scale = phone_scale()
        return x * scale, y * scale

    def to_phone(cx: float, cy: float) -> tuple[int, int]:
        scale = phone_scale()
        return int(cx / scale), int(cy / scale)

    def point_radius() -> float:
        return max(9, PointModel.POINT_SIZE * phone_scale() / 2)

    buttons_row = ttk.Frame(body)
    buttons_row.pack(fill="x")
    widgets["add"] = ttk.Button(buttons_row, text="+  Add point", width=14)
    widgets["add"].pack(side="left")
    widgets["remove"] = ttk.Button(buttons_row, text="\u2212  Remove point", width=14)
    widgets["remove"].pack(side="left", padx=6)
    if role == "controller":
        widgets["swipe"] = ttk.Button(buttons_row, text="Swipe between two...", width=18)
        widgets["swipe"].pack(side="left")
    widgets["exit"] = ttk.Button(
        buttons_row, text="Send exit" if role == "controller" else "Close session",
        width=12)
    widgets["exit"].pack(side="left", padx=6)
    ttk.Label(buttons_row, textvariable=points_var).pack(side="right")

    canvas = tk.Canvas(body, width=CANVAS_W, height=CANVAS_H, bg="#101418",
                       highlightthickness=1, highlightbackground="#333")
    canvas.pack(pady=(8, 4))
    widgets["canvas"] = canvas

    ttk.Label(body, textvariable=hint_var, foreground="#666",
              wraplength=CANVAS_W + 60, justify="left").pack(anchor="w")

    if role == "controller":
        ttk.Label(body, foreground="#666",
                  text="Drag the blue points to place them exactly where you want "
                       "on the phone. Click a point to tap it there. "
                       "Keys 1-9 and +/- work too.").pack(anchor="w", pady=(4, 0))
        pad = ttk.Frame(body)
        pad.pack(fill="x", pady=(4, 0))
        widgets["pad"] = pad
        widgets["taps"] = []
    else:
        ttk.Label(body, foreground="#666",
                  text="Whatever the phone's controller sends is drawn here.").pack(
                anchor="w", pady=(4, 0))

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

    def redraw_canvas(flash: int | None = None) -> None:
        canvas = widgets["canvas"]
        canvas.delete("all")
        session = state["session"]
        model = session.model if session else PointModel()
        radius = point_radius()
        swipe_from = canvas_state["swipe_from"]

        # Phone screen outline, letterboxed inside the canvas.
        scale = phone_scale()
        sw, sh = to_canvas(model.screen[0], model.screen[1])
        canvas.create_rectangle(1, 1, sw + 1, sh + 1, outline="#3a4150")

        if canvas_state["swipe_line"]:
            x1, y1, x2, y2 = canvas_state["swipe_line"]
            canvas.create_line(x1, y1, x2, y2, fill="#ffb020", width=3, arrow="last")

        for number, (x, y) in enumerate(model.points, start=1):
            left, top = to_canvas(x, y)
            cx = left + radius
            cy = top + radius
            hot = (flash == number)
            is_from = (swipe_from == number - 1)
            fill = "#ff4d4d" if hot else ("#ffb020" if is_from else "#2d6cdf")
            outline = "#ffffff" if (hot or is_from) else "#8ab4ff"
            canvas.create_oval(
                cx - radius, cy - radius, cx + radius, cy + radius,
                fill=fill, outline=outline, width=2 if (hot or is_from) else 1,
                tags=f"point:{number - 1}",
            )
            canvas.create_text(cx, cy, text=str(number), fill="white",
                               tags=f"point:{number - 1}")
        if not model.points:
            canvas.create_text(CANVAS_W / 2, CANVAS_H / 2, fill="#666",
                               text="no points yet\npress + or Add point")

    def refresh_points() -> None:
        session = state["session"]
        count = len(session.model) if session else 0
        points_var.set(f"{count} point{'' if count == 1 else 's'}")
        redraw_canvas()
        if role != "controller":
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
        redraw_canvas(flash=number)
        root.after(220, lambda: redraw_canvas())

    # -- canvas interaction (controller role) ------------------------------

    def point_at(cx: float, cy: float) -> int | None:
        """0-based index of the point under a canvas coordinate, or None."""
        session = state["session"]
        if not session:
            return None
        radius = point_radius()
        for index, (x, y) in reversed(list(enumerate(session.model.points))):
            left, top = to_canvas(x, y)
            centre = (left + radius, top + radius)
            if (cx - centre[0]) ** 2 + (cy - centre[1]) ** 2 <= (radius + 4) ** 2:
                return index
        return None

    def on_canvas_press(event) -> None:
        if role != "controller" or not state["session"]:
            return
        index = point_at(event.x, event.y)
        if index is None:
            return
        if canvas_state.get("swipe_active"):
            # Picking endpoints for a swipe: first press sets the start, second fires it.
            if canvas_state["swipe_from"] is None:
                canvas_state["swipe_from"] = index
                hint_var.set(f"Now click the point to swipe to (started at point {index + 1}).")
            else:
                start = canvas_state["swipe_from"]
                if start != index:
                    state["session"].swipe(start, index)
                canvas_state["swipe_from"] = None
                canvas_state["swipe_active"] = False
                hint_var.set("")
                widgets["swipe"].configure(text="Swipe between two...")
            redraw_canvas()
            return
        canvas_state["drag"] = {"index": index, "moved": False}

    def on_canvas_drag(event) -> None:
        drag = canvas_state["drag"]
        if drag is None or role != "controller":
            return
        index = drag["index"]
        px, py = to_phone(event.x, event.y)
        half = PointModel.POINT_SIZE // 2
        target = (max(0, px - half), max(0, py - half))
        # MOVE events fire many times per second; only push when the point would
        # land on a different phone pixel, to avoid flooding the link.
        if drag.get("last_sent") != target:
            drag["last_sent"] = target
            state["session"].move_point(index, target[0], target[1])
        drag["moved"] = True

    def on_canvas_release(event) -> None:
        drag = canvas_state["drag"]
        if drag is None:
            return
        canvas_state["drag"] = None
        if not drag["moved"]:
            # A plain click on a point taps it on the phone.
            state["session"].tap(drag["index"] + 1)

    def toggle_swipe_mode() -> None:
        if canvas_state.get("swipe_active"):
            canvas_state["swipe_from"] = None
            canvas_state["swipe_active"] = False
            hint_var.set("")
            widgets["swipe"].configure(text="Swipe between two...")
        else:
            canvas_state["swipe_active"] = True
            canvas_state["swipe_from"] = None
            hint_var.set("Swipe mode: click the point to start at, then the one to swipe to.")
            widgets["swipe"].configure(text="Cancel swipe")
        redraw_canvas()

    canvas.bind("<ButtonPress-1>", on_canvas_press)
    canvas.bind("<B1-Motion>", on_canvas_drag)
    canvas.bind("<ButtonRelease-1>", on_canvas_release)

    def set_connected(connected: bool) -> None:
        state["searching"] = False
        connect_button.configure(text="Start",
                                 state="disabled" if connected else "normal")
        disconnect_button.configure(state="normal" if connected else "disabled")
        can_advance = connected and state.get("search") is not None
        next_button.configure(state="normal" if can_advance else "disabled")
        for key in ("add", "remove", "exit"):
            widgets[key].configure(state="normal" if connected else "disabled")
        if role == "controller":
            widgets["swipe"].configure(state="normal" if connected else "disabled")
        status_var.set("connected" if connected else "not connected")

    def set_searching() -> None:
        """Start doubles as Stop while a channel search is running."""
        state["searching"] = True
        connect_button.configure(text="Stop", state="normal")
        disconnect_button.configure(state="disabled")
        next_button.configure(state="disabled")
        for key in ("add", "remove", "exit"):
            widgets[key].configure(state="disabled")
        if role == "controller":
            widgets["swipe"].configure(state="disabled")

    def flash_swipe(line: str) -> None:
        """Draw a swipe briefly on the simulated screen when one is dispatched."""
        parts = [int(v) for v in line.split(",")]
        x1, y1, x2, y2 = parts[:4]
        c1 = to_canvas(x1, y1)
        c2 = to_canvas(x2, y2)
        canvas_state["swipe_line"] = (c1[0], c1[1], c2[0], c2[1])
        redraw_canvas()
        root.after(700, lambda: (canvas_state.__setitem__("swipe_line", None),
                                 redraw_canvas()))

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
                elif kind == "swipe":
                    log("tap", f"SWIPE {message}")
                    flash_swipe(message)
                elif kind == "screen":
                    w, h = message.split(",")
                    log("info", f"phone screen is {w}x{h} pixels")
                    redraw_canvas()
                elif kind == "closed":
                    set_connected(False)
                    log("info", "session closed")
                elif kind == "connected":
                    set_connected(True)
                elif kind == "status":
                    status_var.set(message)
                else:
                    log(kind, message)
        except queue.Empty:
            pass
        root.after(60, drain)

    # -- actions -----------------------------------------------------------

    def do_start() -> None:
        if state.get("searching"):
            search = state.get("search")
            if search is not None:
                search.cancel()
            log("info", "stopping the search ...")
            status_var.set("stopping ...")
            return

        transport_kind = transport_var.get()
        listening = mode_var.get() == "listen"
        address = address_var.get().strip()
        channel_text = channel_var.get().strip()

        try:
            channels = parse_channels(channel_text)
        except ValueError as error:
            log("error", str(error))
            return

        state["search"] = None
        set_searching()
        status_var.set("waiting ..." if listening else "connecting ...")

        def worker() -> None:
            try:
                if transport_kind == "bluetooth":
                    if listening:
                        transport = listen_bluetooth(
                            channels[0] if channels else 3,
                            log=lambda m: post("info", m))
                    else:
                        if not address:
                            raise RuntimeError("enter the phone's Bluetooth address first")
                        search = ChannelSearch(address, channels, log=_search_log)
                        state["search"] = search
                        transport, channel = search.next_connection()
                        post("info", f"using channel {channel}")
                else:
                    if not channels:
                        raise RuntimeError("enter a TCP port")
                    if listening:
                        transport = listen_tcp(channels[0], log=lambda m: post("info", m))
                    else:
                        transport = connect_tcp(address or "127.0.0.1", channels[0],
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

    def _search_log(message: str) -> None:
        """Search chatter goes to the log, and channel attempts also to the status."""
        post("info", message)
        if message.startswith("trying RFCOMM channel"):
            search = state.get("search")
            left = f", {search.remaining} left" if search else ""
            post("status", message.replace("trying RFCOMM ", "").rstrip(" .") + left)

    def do_try_next() -> None:
        """
        Reject the channel we landed on and resume the search at the next one.

        Nothing has to be retyped and the sweep does not start over -- the
        channels already ruled out stay ruled out.
        """
        search = state.get("search")
        if search is None:
            return
        session = state["session"]
        if session is not None:
            session.close()
            state["session"] = None
        log("info", f"rejected; {search.remaining} channel(s) still to try")
        set_searching()

        def worker() -> None:
            try:
                transport, channel = search.next_connection()
            except Exception as error:  # noqa: BLE001
                post("error", str(error))
                post("closed")
                return
            state["session"] = Session(transport, post)
            post("info", f"using channel {channel}")
            post("points")
            post("connected")

        threading.Thread(target=worker, daemon=True).start()

    def do_disconnect() -> None:
        state["search"] = None
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
    next_button.configure(command=do_try_next)
    disconnect_button.configure(command=do_disconnect)
    scan_button.configure(command=do_scan)
    widgets["add"].configure(command=lambda: state["session"] and state["session"].add_point())
    widgets["remove"].configure(command=lambda: state["session"] and state["session"].remove_point())
    widgets["exit"].configure(command=lambda: state["session"] and state["session"].send_exit())
    if role == "controller":
        widgets["swipe"].configure(command=toggle_swipe_mode)

    if role == "controller":
        def on_key(event) -> None:
            session = state["session"]
            if not session:
                return
            if event.char.isdigit() and event.char != "0":
                if canvas_state.get("swipe_active"):
                    # Keys can pick the swipe endpoints too.
                    number = int(event.char)
                    index = number - 1
                    if canvas_state["swipe_from"] is None:
                        if 0 <= index < len(session.model):
                            canvas_state["swipe_from"] = index
                            hint_var.set(f"Now press the point to swipe to (started at {number}).")
                    else:
                        start = canvas_state["swipe_from"]
                        if start != index:
                            session.swipe(start, index)
                        canvas_state["swipe_from"] = None
                        canvas_state["swipe_active"] = False
                        hint_var.set("")
                        widgets["swipe"].configure(text="Swipe between two...")
                    redraw_canvas()
                else:
                    session.tap(int(event.char))
            elif event.char in "+=":
                session.add_point()
            elif event.char == "-":
                session.remove_point()
            elif event.char.lower() == "s":
                toggle_swipe_mode()

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
        session.move_point(0, 500, 600)   # reposition point 1 from the laptop
        session.move_point(1, 200, 900)   # and point 2
        session.swipe(0, 1)               # swipe between them
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
    moves = [m for m in messages if "MOVED" in m]
    swipes = [m for m in messages if "SWIPE" in m]
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
    if len(moves) != 2:
        problems.append(f"controller: expected 2 remote moves, saw {len(moves)}: {moves}")
    elif "point 1 to (500, 600)" not in moves[0]:
        problems.append(f"controller: point 1 did not move to the requested spot: {moves[0]}")
    if len(swipes) != 1:
        problems.append(f"controller: expected 1 swipe, saw {len(swipes)}: {swipes}")
    elif swipes and "(515,615) -> (215,915)" not in swipes[0]:
        # Centres = moved top-left + 15 (half of the 30px point).
        problems.append(f"controller: swipe endpoints were wrong: {swipes[0]}")

    print("\n=== laptop as SERVICE, fake phone as controller ===")
    messages, events = _run_direction("service")
    for line in messages:
        print("  ", line)
    kinds = [k for k, _ in events]
    taps = [msg for kind, msg in events if kind == "tap"]
    misses = [msg for kind, msg in events if kind == "miss"]
    swipes = [msg for kind, msg in events if kind == "swipe"]
    screens = [msg for kind, msg in events if kind == "screen"]
    print("   received taps:", taps, " ignored:", len(misses),
          " swipes:", len(swipes), " screens:", len(screens))
    if taps != ["1", "2"]:
        problems.append(f"service: expected taps 1,2, saw {taps}")
    if len(misses) != 1:
        problems.append(f"service: expected 1 out-of-range press, saw {len(misses)}")
    if kinds.count("points") < 4:
        problems.append("service: point list did not track the peer's add/remove/move")
    if len(swipes) != 1:
        problems.append(f"service: expected 1 swipe from the controller, saw {len(swipes)}")
    if len(screens) != 1:
        problems.append(f"service: expected the phone's screen size once, saw {len(screens)}")

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

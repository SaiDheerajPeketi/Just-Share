#!/usr/bin/env python3
"""AlterSend TCP relay.

Pairs one sender and one receiver by relay session id, then pipes encrypted
AlterSend frames in both directions. The relay only sees the Java-UTF
registration header and opaque encrypted bytes after pairing.
"""

import argparse
import logging
import signal
import socket
import struct
import threading
import time
from dataclasses import dataclass, field
from typing import Dict, Optional


DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 41404
DEFAULT_BUFFER_SIZE = 64 * 1024
DEFAULT_PENDING_TIMEOUT_SECONDS = 120
DEFAULT_SOCKET_TIMEOUT_SECONDS = 45
DEFAULT_MAX_PENDING_SESSIONS = 4096


@dataclass
class PendingSession:
    created_at: float
    sender: Optional[socket.socket] = None
    receiver: Optional[socket.socket] = None

    def set_peer(self, role: str, conn: socket.socket) -> None:
        old = getattr(self, role)
        if old is not None:
            close_quietly(old)
        setattr(self, role, conn)

    def ready(self) -> bool:
        return self.sender is not None and self.receiver is not None


@dataclass
class RelayState:
    pending: Dict[str, PendingSession] = field(default_factory=dict)
    lock: threading.Lock = field(default_factory=threading.Lock)
    stopping: threading.Event = field(default_factory=threading.Event)


def read_exact(conn: socket.socket, size: int) -> bytes:
    data = bytearray()
    while len(data) < size:
        chunk = conn.recv(size - len(data))
        if not chunk:
            raise OSError("connection closed while reading registration")
        data.extend(chunk)
    return bytes(data)


def read_utf(conn: socket.socket) -> str:
    raw = read_exact(conn, 2)
    size = struct.unpack(">H", raw)[0]
    return read_exact(conn, size).decode("utf-8")


def close_quietly(conn: Optional[socket.socket]) -> None:
    if conn is None:
        return
    try:
        conn.shutdown(socket.SHUT_RDWR)
    except OSError:
        pass
    try:
        conn.close()
    except OSError:
        pass


def pipe(src: socket.socket, dst: socket.socket, buffer_size: int, label: str) -> None:
    try:
        while True:
            data = src.recv(buffer_size)
            if not data:
                break
            dst.sendall(data)
    except OSError as exc:
        logging.debug("pipe %s closed: %s", label, exc)
    finally:
        try:
            dst.shutdown(socket.SHUT_WR)
        except OSError:
            pass


def bridge(session: str, sender: socket.socket, receiver: socket.socket, buffer_size: int) -> None:
    logging.info("bridging session=%s", session[:12])
    threads = [
        threading.Thread(
            target=pipe,
            args=(sender, receiver, buffer_size, f"{session[:12]} sender->receiver"),
            daemon=True,
        ),
        threading.Thread(
            target=pipe,
            args=(receiver, sender, buffer_size, f"{session[:12]} receiver->sender"),
            daemon=True,
        ),
    ]
    for thread in threads:
        thread.start()


def cleanup_pending(state: RelayState, max_age_seconds: int) -> None:
    cutoff = time.monotonic() - max_age_seconds
    expired = []
    with state.lock:
        for session, entry in list(state.pending.items()):
            if entry.created_at < cutoff:
                expired.append((session, entry))
                del state.pending[session]
    for session, entry in expired:
        logging.info("expiring pending session=%s", session[:12])
        close_quietly(entry.sender)
        close_quietly(entry.receiver)


def cleanup_loop(state: RelayState, max_age_seconds: int) -> None:
    while not state.stopping.wait(max(5, min(30, max_age_seconds // 4))):
        cleanup_pending(state, max_age_seconds)


def register_connection(
    state: RelayState,
    conn: socket.socket,
    addr: tuple,
    max_pending_sessions: int,
    buffer_size: int,
) -> None:
    try:
        magic = read_utf(conn)
        session = read_utf(conn)
        role = read_utf(conn)
        if magic != "JSASR1" or role not in ("sender", "receiver") or not session:
            raise ValueError("invalid relay registration")

        with state.lock:
            if session not in state.pending and len(state.pending) >= max_pending_sessions:
                raise RuntimeError("relay pending-session limit reached")
            entry = state.pending.setdefault(session, PendingSession(created_at=time.monotonic()))
            entry.set_peer(role, conn)
            if not entry.ready():
                logging.info("registered %s session=%s from=%s", role, session[:12], addr)
                return

            sender = entry.sender
            receiver = entry.receiver
            del state.pending[session]

        if sender is None or receiver is None:
            close_quietly(conn)
            return
        bridge(session, sender, receiver, buffer_size)
    except Exception as exc:
        logging.warning("registration failed from=%s error=%s", addr, exc)
        close_quietly(conn)


def serve(args: argparse.Namespace) -> None:
    state = RelayState()
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )

    cleanup_thread = threading.Thread(
        target=cleanup_loop,
        args=(state, args.pending_timeout),
        daemon=True,
    )
    cleanup_thread.start()

    def stop(_signum, _frame) -> None:
        state.stopping.set()

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((args.host, args.port))
        server.listen(args.backlog)
        server.settimeout(1.0)
        logging.info("AlterSend relay listening on %s:%s", args.host, args.port)

        while not state.stopping.is_set():
            try:
                conn, addr = server.accept()
            except socket.timeout:
                continue
            conn.settimeout(args.socket_timeout)
            threading.Thread(
                target=register_connection,
                args=(state, conn, addr, args.max_pending_sessions, args.buffer_size),
                daemon=True,
            ).start()

    cleanup_pending(state, 0)
    logging.info("AlterSend relay stopped")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Host a Just-Share AlterSend relay")
    parser.add_argument("--host", default=DEFAULT_HOST, help="listen address")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="listen port")
    parser.add_argument("--backlog", type=int, default=256, help="TCP listen backlog")
    parser.add_argument("--buffer-size", type=int, default=DEFAULT_BUFFER_SIZE, help="pipe buffer size")
    parser.add_argument(
        "--pending-timeout",
        type=int,
        default=DEFAULT_PENDING_TIMEOUT_SECONDS,
        help="seconds before an unmatched sender/receiver registration is closed",
    )
    parser.add_argument(
        "--socket-timeout",
        type=int,
        default=DEFAULT_SOCKET_TIMEOUT_SECONDS,
        help="seconds before idle registration reads time out",
    )
    parser.add_argument(
        "--max-pending-sessions",
        type=int,
        default=DEFAULT_MAX_PENDING_SESSIONS,
        help="maximum unmatched sessions held in memory",
    )
    parser.add_argument("--verbose", action="store_true", help="enable debug logs")
    return parser.parse_args()


if __name__ == "__main__":
    serve(parse_args())

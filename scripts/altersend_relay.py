#!/usr/bin/env python3
import socket
import struct
import threading

HOST = "0.0.0.0"
PORT = 41404


def read_utf(conn):
    raw = conn.recv(2)
    if len(raw) != 2:
        raise OSError("short utf length")
    size = struct.unpack(">H", raw)[0]
    data = bytearray()
    while len(data) < size:
        chunk = conn.recv(size - len(data))
        if not chunk:
            raise OSError("short utf payload")
        data.extend(chunk)
    return data.decode("utf-8")


def pipe(src, dst):
    try:
        while True:
            data = src.recv(64 * 1024)
            if not data:
                break
            dst.sendall(data)
    finally:
        try:
            dst.shutdown(socket.SHUT_WR)
        except OSError:
            pass


def bridge(sender, receiver):
    threading.Thread(target=pipe, args=(sender, receiver), daemon=True).start()
    threading.Thread(target=pipe, args=(receiver, sender), daemon=True).start()


def main():
    pending = {}
    lock = threading.Lock()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen()
    print(f"AlterSend relay listening on {HOST}:{PORT}")

    while True:
        conn, addr = server.accept()
        try:
            magic = read_utf(conn)
            session = read_utf(conn)
            role = read_utf(conn)
            if magic != "JSASR1" or role not in ("sender", "receiver"):
                conn.close()
                continue
            with lock:
                entry = pending.setdefault(session, {})
                entry[role] = conn
                if "sender" in entry and "receiver" in entry:
                    sender = entry["sender"]
                    receiver = entry["receiver"]
                    del pending[session]
                    print(f"bridging session {session[:8]} from {addr}")
                    bridge(sender, receiver)
        except Exception as exc:
            print(f"relay registration failed from {addr}: {exc}")
            try:
                conn.close()
            except OSError:
                pass


if __name__ == "__main__":
    main()

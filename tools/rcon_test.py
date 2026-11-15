import socket
import struct
import sys
import time


def make_packet(pid, ptype, body):
    payload = struct.pack('<ii', pid, ptype) + body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(payload)) + payload


def read_packet(sock):
    raw = b''
    while len(raw) < 4:
        chunk = sock.recv(4 - len(raw))
        if not chunk:
            raise EOFError("closed")
        raw += chunk
    (length,) = struct.unpack('<i', raw)
    body = b''
    while len(body) < length:
        chunk = sock.recv(length - len(body))
        if not chunk:
            raise EOFError("closed")
        body += chunk
    pid, ptype = struct.unpack('<ii', body[:8])
    return pid, ptype, body[8:-2].decode('utf-8', 'replace')


def main():
    deadline = time.time() + 180
    sock = None
    while time.time() < deadline:
        try:
            sock = socket.create_connection(('127.0.0.1', 25575), timeout=5)
            break
        except OSError:
            time.sleep(3)
    if sock is None:
        print("RCON port never opened")
        return 1

    sock.sendall(make_packet(1, 3, 'cabtest'))
    pid, ptype, body = read_packet(sock)
    if pid == -1:
        print("RCON auth failed")
        return 1
    print("auth ok")

    commands = [
        'gamerule sendCommandFeedback true',
        'setblock 8 -60 8 emucab:cab_desk[part=center,facing=north]',
        'setblock 7 -60 8 emucab:cab_desk[part=left,facing=north]',
        'setblock 9 -60 8 emucab:cab_desk[part=right,facing=north]',
        'data get block 8 -60 8',
        'data get block 7 -60 8',
        'setblock 8 -60 8 air',
        'setblock 7 -60 8 air',
        'setblock 9 -60 8 air',
        'data get block 8 -60 8',
    ]
    for i, command in enumerate(commands, start=2):
        sock.sendall(make_packet(i, 2, command))
        pid, ptype, body = read_packet(sock)
        print(f"> {command}\n  {body.strip()}")
    sock.close()
    return 0


if __name__ == '__main__':
    sys.exit(main())

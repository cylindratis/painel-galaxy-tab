"""Windows LAN -> localhost WSL forwarding, only private LAN clients.
No payload logging or persistence. Run with pythonw for a windowless relay.
"""
import argparse
import asyncio
import ipaddress
import subprocess
import signal
import socket

async def copy(source, target):
    try:
        while data := await asyncio.wait_for(source.read(65536), 45):
            target.write(data)
            await target.drain()
    finally:
        target.close()

async def accept(reader, writer):
    peer = writer.get_extra_info('peername')[0]
    if not ipaddress.ip_address(peer).is_private:
        writer.close()
        return
    try:
        upstream, output = await asyncio.open_connection('127.0.0.1', 8765)
        tasks = [asyncio.create_task(copy(reader, output)), asyncio.create_task(copy(upstream, writer))]
        done, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
        for task in pending:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
    except (OSError, asyncio.TimeoutError):
        writer.close()

async def main():
    server = await asyncio.start_server(accept, '0.0.0.0', 8766)
    # systemd services alone do not keep a WSL distribution alive.
    keep = subprocess.Popen(['wsl.exe', '-d', 'Ubuntu', '--exec', 'sleep', 'infinity'],
                            creationflags=subprocess.CREATE_NO_WINDOW,
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        async with server:
            await server.serve_forever()
    finally:
        keep.terminate()

if __name__ == '__main__':
    asyncio.run(main())

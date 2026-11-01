# FocusLock companion (Fedora laptop)

Two listeners, started by one command:

| Listener | Address | Purpose |
|---|---|---|
| Dashboard | `127.0.0.1:5000` | Stop button, offline token, pairing JSON. Loopback only. |
| Phone link | `0.0.0.0:5001` | Single signed `POST /poll` endpoint. Nothing else is exposed. |

```bash
pip install -r requirements.txt
python server.py                  # keep running while you study
python cli.py pair                # prints the JSON to paste into the phone app (Pair laptop)
python cli.py stop                # arms a stop; the phone picks it up within ~5 s
python cli.py cancel              # withdraw an armed stop
python cli.py token --code a1b2c3d4   # offline fallback; code is shown on the phone
python cli.py status
```

How the stop works: the phone sends an HMAC-signed poll every 5 s. If you armed a stop, the reply is a
signed stop command bound to that session code. The phone re-verifies signature, session, freshness and
nonce before ending the lock. Allow TCP 5001 in firewalld if needed:
`sudo firewall-cmd --add-port=5001/tcp` (add `--permanent` to keep it).

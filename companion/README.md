# FocusLock Companion (Fedora Linux)

Lightweight companion CLI and Flask dashboard for your FocusLock Android study enforcement system.

## Setup on Fedora 40/41

1. Open terminal and install Python 3 & pip:
   ```bash
   sudo dnf install -y python3 python3-pip
   cd companion
   pip install -r requirements.txt
   chmod +x cli.py server.py
   ```

2. Make `focuslock` available system-wide:
   ```bash
   sudo ln -sf $(pwd)/cli.py /usr/local/bin/focuslock
   ```

## CLI Usage

- **Pair Phone with Laptop (Generates shared HMAC-SHA256 secret)**:
  ```bash
  focuslock pair
  ```
  *(Displays your shared secret and pairing payload to scan on your phone)*

- **Set Phone IP on your local Wi-Fi**:
  ```bash
  focuslock config --ip 192.168.1.105
  ```

- **Stop Active Session (Cryptographically Signed via Wi-Fi)**:
  ```bash
  focuslock stop
  ```
  *(Dispatches signed JSON to `http://<phone_ip>:8765/api/stop`. If Wi-Fi is down or phone is offline, automatically prints the one-time offline signed token!)*

- **Generate Offline Stop Token Directly**:
  ```bash
  focuslock token
  ```

- **Check Study Stats & Streaks**:
  ```bash
  focuslock status
  ```

## Web Dashboard

Run the local web dashboard:
```bash
python3 server.py
```
Open **http://127.0.0.1:5000** in your browser.

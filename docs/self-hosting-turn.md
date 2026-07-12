# Self-Hosting a TURN Server for Jami (coturn)

A practical guide to running your own TURN relay so Jami's relayed calls go
through infrastructure you control instead of the public `turn.jami.net`.

> Reminder: Jami calls are **end-to-end encrypted** either way. A self-hosted
> TURN improves *metadata privacy* and independence — the relay only ever sees
> encrypted packets. It does **not** speed up direct calls (TURN is only a
> fallback when a direct peer-to-peer link can't be formed).

---

## 0. What you need

- A small VPS/server with a **public, static IP** (1 vCPU / 1 GB RAM handles
  several concurrent relayed calls; bandwidth is the real constraint — see §7).
- A DNS **A record**, e.g. `turn.example.com → <public IP>`.
- Ports openable on the firewall (see §4).
- Optional but recommended: a TLS certificate (Let's Encrypt) for `turns:`.

---

## 1. Minimal `turnserver.conf`

Save as `/etc/turnserver.conf`:

```ini
# --- Network ---
listening-port=3478
tls-listening-port=5349
# Bind to all interfaces (or set your private IP explicitly):
listening-ip=0.0.0.0
# Public IP clients reach. If the box is behind 1:1 NAT (AWS/GCP/Azure),
# use  external-ip=PUBLIC_IP/PRIVATE_IP  instead of just PUBLIC_IP:
external-ip=YOUR_PUBLIC_IP

# --- Relay port range (UDP). Wider = more simultaneous relays. ---
min-port=49152
max-port=49252

# --- Authentication (long-term credentials — what Jami uses) ---
lt-cred-mech
realm=turn.example.com
user=jami:CHANGE_ME_TO_A_STRONG_SECRET

# --- TLS (optional; needed only if you use turns:5349) ---
cert=/etc/letsencrypt/live/turn.example.com/fullchain.pem
pkey=/etc/letsencrypt/live/turn.example.com/privkey.pem

# --- Hardening ---
fingerprint
no-multicast-peers
no-cli
# Stop your relay from being abused to reach private/internal networks:
denied-peer-ip=10.0.0.0-10.255.255.255
denied-peer-ip=172.16.0.0-172.31.255.255
denied-peer-ip=192.168.0.0-192.168.255.255
denied-peer-ip=169.254.0.0-169.254.255.255
denied-peer-ip=127.0.0.0-127.255.255.255
# Optional abuse limits:
user-quota=12
total-quota=1200
# bps-capacity=0    # 0 = unlimited; set to cap total throughput

# --- Logging ---
log-file=/var/log/turnserver/turnserver.log
simple-log
```

Replace `turn.example.com`, `YOUR_PUBLIC_IP`, and the password.

---

## 2. Exact fields to enter in the Jami app

**Account → Advanced settings → TURN**

| Field (app)   | Value                                     |
|---------------|-------------------------------------------|
| Enable TURN   | **On**                                    |
| TURN server   | `turn.example.com`  (or `turn.example.com:3478`) |
| TURN username | `jami`                                    |
| TURN password | the secret from `user=jami:...`           |

Notes:
- The **realm** is provided by the server automatically; the app has no realm
  field, so you don't enter it.
- The app's TURN field takes a **host** (optionally `host:port`), not a
  `turn:`/`turns:` URI. Use plain `3478` for best compatibility.
- Do this on **every device/account** that should use your relay.

---

## 3. Deploy — bare metal (Debian/Ubuntu)

```bash
# 1. Install
sudo apt update && sudo apt install -y coturn

# 2. Enable the service
echo 'TURNSERVER_ENABLED=1' | sudo tee /etc/default/coturn

# 3. Write /etc/turnserver.conf  (from §1)
sudo install -d -o turnserver -g turnserver /var/log/turnserver

# 4. (Optional) TLS cert
sudo apt install -y certbot
sudo certbot certonly --standalone -d turn.example.com
# allow coturn to read the certs:
sudo usermod -aG ssl-cert turnserver 2>/dev/null || true

# 5. Start
sudo systemctl enable --now coturn
sudo systemctl status coturn --no-pager
```

## 3b. Deploy — Docker (alternative)

`docker-compose.yml`:

```yaml
services:
  coturn:
    image: coturn/coturn:latest
    # host networking is simplest for the wide relay port range:
    network_mode: host
    restart: unless-stopped
    volumes:
      - ./turnserver.conf:/etc/turnserver.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
```

```bash
docker compose up -d
docker compose logs -f coturn
```

If you can't use host networking, publish `3478/udp`, `3478/tcp`,
`5349/tcp`, and the `min-port–max-port` UDP range explicitly.

---

## 4. Firewall / security-group ports

| Port                | Proto    | Purpose                     |
|---------------------|----------|-----------------------------|
| 3478                | UDP+TCP  | STUN/TURN                   |
| 5349                | UDP+TCP  | TURN over TLS (if using)    |
| 49152–49252         | UDP      | Relay range (match §1)      |

Example (ufw):

```bash
sudo ufw allow 3478/udp
sudo ufw allow 3478/tcp
sudo ufw allow 5349/tcp
sudo ufw allow 49152:49252/udp
```

Cloud providers: open the same in the instance's security group.

---

## 5. Verify it works

**Server-side self-test** (coturn utils):

```bash
turnutils_uclient -v -u jami -w 'CHANGE_ME_TO_A_STRONG_SECRET' \
  -y turn.example.com
# Success prints allocated relay addresses and "success" test lines.
```

**Client-side (browser) sanity check:**
Open a WebRTC "Trickle ICE" test page, add
`turn:turn.example.com:3478` with your username/password, and click gather.
You should see candidates of **type `relay`** appear. If only `host`/`srflx`
appear, the TURN auth/reachability is wrong.

**In Jami:** place a call with a contact who is on a *different* network. To
be sure the relay is exercised, watch the coturn log:

```bash
sudo tail -f /var/log/turnserver/turnserver.log   # look for "allocation" lines
```

---

## 6. Security hardening checklist

- [ ] **Strong, unique password**; rotate it periodically.
- [ ] Keep the **`denied-peer-ip`** private-range blocks (prevents your relay
      being used to probe internal networks — a real abuse vector).
- [ ] Set **`user-quota` / `total-quota`** (and optionally `bps-capacity`) so a
      leaked credential can't run up unlimited bandwidth.
- [ ] Prefer **TLS (`turns:5349`)** if your clients support it end-to-end.
- [ ] Keep coturn patched (`apt upgrade` / repull the image).
- [ ] Don't expose the coturn CLI (`no-cli`, or set `cli-password`).
- [ ] Advanced: coturn also supports **ephemeral REST credentials**
      (`use-auth-secret` + `static-auth-secret`) for time-limited tokens. Jami's
      account settings use **static** long-term credentials, so stick with
      `lt-cred-mech` + `user=` unless you build token issuance yourself.

---

## 7. Sizing / bandwidth

TURN relays media, so plan **bandwidth**, not CPU:

- Audio call: ~50–100 kbps each direction.
- Video call: ~1–2 Mbps each direction.
- A relayed call uses **both** directions **through your server**, so budget
  ~2–4 Mbps of egress per concurrent relayed video call.

Only the calls that *can't* go direct hit the relay, so real usage is usually
a fraction of your total calls. A modest VPS with a few TB/month of transfer
covers personal/small-group use comfortably.

---

## 8. Going further (fuller independence from SFL infra)

TURN is one piece. To reduce reliance on Savoir-faire Linux servers entirely,
you can also self-host:

- **OpenDHT bootstrap** node (replaces `bootstrap.jami.net`) — peer discovery.
- **Name server** (replaces `ns.jami.net`) — your own username registry.
- **DHT proxy + push server** — mobile push notifications without SFL's proxy.

Each is set per-account in **Advanced settings**. TURN alone already moves your
relayed-call metadata onto your own box, which is the highest-impact first step.

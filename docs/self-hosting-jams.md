# Self-Hosting JAMS (Jami Account Management Server)

JAMS lets you run Jami as a **managed, self-contained deployment**: your own
user directory, your own account issuance, and a single set of network settings
(bootstrap / TURN / DHT proxy) pushed to every client. For a mixed **iOS +
Android** group it is the clean way to get:

- **Consistent cross-platform discovery** — every client queries *your* one
  directory, so iOS and Android users reliably find each other by username.
- **More stable connections** — clients receive a consistent bootstrap + TURN +
  proxy config from the server.

> ⚠️ Accuracy note: the official Jami docs (`docs.jami.net`,
> `review.jami.net/jami-jams`, `jami.biz/jams-user-guide`) were unreachable from
> the environment where this guide was written, so the **server-side** commands
> below are the known essentials and should be confirmed against the current
> official documentation. The **client-side** configuration is stable and
> correct. Official sources are listed at the bottom.

---

## 1. What JAMS is

- A server that **enrolls Jami clients into an organization** and issues their
  identities. Clients sign in with a username/password instead of creating a
  classic distributed account.
- **Authentication sources:** LDAP, Active Directory, or an **embedded
  database** (built-in — simplest for a small group).
- It acts as a small **certificate authority** (issues X.509 identities to
  managed accounts) and hosts your **name directory**.
- It can hand clients their network config (bootstrap, TURN, DHT proxy).

**Trade-off:** JAMS accounts are *managed* — different from the classic
"anonymous" distributed Jami account. Existing classic accounts are not
migrated; users enroll fresh against your JAMS.

---

## 2. Prerequisites

- A server with a **public domain name**, e.g. `jams.example.com`, and a static
  public IP.
- A **TLS certificate** for that domain (`server.pem` + `server.key`; use
  Let's Encrypt).
- **Java 11+** on the host (if running the launcher jar directly), or Docker.
- Firewall open for the JAMS port (default **8443/tcp**) and, if you also run
  the bundled name server / DHT services, their ports.
- Recommended: put JAMS behind a reverse proxy (nginx/Caddy) terminating TLS on
  443 and forwarding to JAMS.

---

## 3. Deploy

JAMS is distributed as source from the official Jami Gerrit
(`review.jami.net/jami-jams`) and as a launcher jar. There is no official image
on Docker Hub under a verified Jami account, so build from source or use the
provided Dockerfile.

### Option A — launcher jar (documented path)

```bash
# One-time guided setup (creates config, admin user, DB, etc.)
./setup_jams.sh

# Launch: <port> <tls-cert> <tls-key>
java -jar jams-launcher.jar 8443 server.pem server.key
```

Then open `https://jams.example.com:8443` to reach the **admin web UI**, create
your organization, choose the auth source (embedded DB / LDAP / AD), and add
users.

### Option B — Docker (build from the repo)

```bash
git clone https://review.jami.net/jami-jams
cd jami-jams
# A dev image target exists in the repo Dockerfile:
docker build -f Dockerfile -t jams .

# Run it, mounting your TLS cert/key and persisting data.
# (Confirm the exact entrypoint/args/volumes against the repo's README —
#  the server expects: port, server.pem, server.key.)
docker run -d --name jams \
  -p 8443:8443 \
  -v "$PWD/certs:/certs:ro" \
  -v "$PWD/jams-data:/data" \
  jams 8443 /certs/server.pem /certs/server.key
```

### Reverse proxy (recommended)

Terminate TLS on 443 and forward to JAMS so clients use a clean
`https://jams.example.com` URL. Example nginx:

```nginx
server {
    listen 443 ssl;
    server_name jams.example.com;
    ssl_certificate     /etc/letsencrypt/live/jams.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/jams.example.com/privkey.pem;
    location / {
        proxy_pass https://127.0.0.1:8443;
        proxy_set_header Host $host;
    }
}
```

---

## 4. Connect the apps (this is the part that fixes cross-platform discovery)

On **both Android and iOS**, in the account creation wizard choose
**"Connect to management server"** and enter:

| Field | Value |
|-------|-------|
| Server URL | `https://jams.example.com` (or `:8443` if not behind a proxy) |
| Username | the user's JAMS username |
| Password | the user's JAMS password |

- The **official App Store iOS app supports JAMS accounts**, so iOS joins your
  deployment without rebuilding anything — this is the key: you cannot recompile
  iOS Jami, but you *can* point it at your JAMS.
- Once enrolled, every user is in **your** directory, so searching a colleague's
  username resolves the same way on iOS and Android.

---

## 5. Stability: the network config JAMS distributes

For more stable connections (especially on phones), make sure your JAMS hands
clients a good network config, and/or set these per account under
**Account → Advanced settings**:

- **OpenDHT → Use DHT proxy** + your **DHT proxy address** — biggest lever for
  mobile reliability (phones sleep; the proxy keeps your presence reachable).
- **TURN** — your coturn relay for calls behind strict NATs
  (see `self-hosting-turn.md`).
- **Bootstrap** — your own OpenDHT bootstrap node for DHT entry.

Running the DHT proxy + TURN alongside JAMS gives you the full self-contained,
stable, cross-platform stack.

---

## 6. Decision guide

| Your goal | Recommended |
|-----------|-------------|
| Just fix "iOS/Android can't find each other" on default servers | Register public usernames or add by QR (see `contacts-cross-platform-troubleshooting.md`) — no server needed |
| Private, org-controlled directory + consistent discovery across iOS/Android | **JAMS** |
| Better mobile call stability only | Self-host **DHT proxy** + **TURN** (`self-hosting-turn.md`) |
| Keep classic distributed accounts but run your own infra | Self-host bootstrap + DHT proxy + TURN + name server (no JAMS) |

---

## Official sources (confirm server-side specifics here)

- JAMS source & README: `https://review.jami.net/plugins/gitiles/jami-jams/`
- Jami documentation: `https://docs.jami.net/`
- JAMS user guide: `https://jami.biz/jams-user-guide`

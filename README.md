# Chargenix — Telecom Prepaid Charging System

A prepaid voice-call billing platform built in two parallel tracks:

1. **Simulated track** — custom `mobile-app` / `msc-app` pair that emulates a
   phone and a mobile switching center over raw TCP/UDP sockets (no real
   telephony stack involved). Useful for testing billing/CDR logic without
   any SIP infrastructure.
2. **Real SIP track** — `fastagi-app`, a FastAGI server that plugs into
   **Asterisk**, so real SIP softphones (e.g. Zoiper) can register and place
   calls that get charged against the same prepaid balance in real time.

Both tracks share the same PostgreSQL `users` table, and both feed the same
CDR format so the `web-app` can report on either one.

---

## 1. Project Layout

```
Chargenix-Telecom-Charging-System/
├── mobile-app/    -> Simulated "Mobile Phone" client (java mobile <MSISDN>)
├── msc-app/       -> Simulated "MSC" server        (java msc / java -jar msc.jar [--rtp])
├── fastagi-app/   -> FastAGI server used by Asterisk to charge real SIP calls
├── web-app/       -> Web platform for USERS (Servlet + JSP + CSS, on Tomcat 10)
├── sql/schema.sql -> PostgreSQL schema + seed data
├── lib/           -> place postgresql JDBC driver jar here
└── run_all.sh     -> convenience script to start the pieces together
```

Each of `mobile-app`, `msc-app`, `fastagi-app`, `web-app` is a separate
NetBeans/Maven project (`pom.xml` included in each).

---

## 2. Database Setup (PostgreSQL)

```bash
sudo -u postgres psql
```

```sql
CREATE DATABASE prepaid_charging;
\c prepaid_charging
```

```bash
psql -U postgres -d prepaid_charging -f sql/schema.sql
```

This creates the `users` table (id, msisdn, balance) and seeds test users,
including MSISDN `01223456789` with balance `50.00`.

**Update credentials** if needed in each module's `DBUtil.java`:
- `msc-app/src/main/java/com/telecom/msc/util/DBUtil.java`
- `fastagi-app/src/main/java/com/mycompany/fastagi/app/DBUtil.java`
- `web-app/src/main/java/com/telecom/web/dao/DBUtil.java`

Default: `jdbc:postgresql://localhost:5432/prepaid_charging`.

---

## 3. PostgreSQL JDBC Driver

Download `postgresql-42.7.3.jar` (or a recent 42.x) from
https://jdbc.postgresql.org/download/ and add it to each project's libraries,
or let Maven pull it automatically via the declared `pom.xml` dependency.

---

## 4. The Two Call Tracks

### 4.1 Simulated Track (mobile-app ⇄ msc-app)

No telephony stack required — good for isolated billing/CDR testing.

**Start MSC first, then Mobile:**

```bash
java msc                 # or: java -jar msc.jar [--rtp]
java mobile 01223456789  # in a second terminal
```

Flow:
1. Mobile connects to MSC via **TCP port 5000** and sends `Start Call <MSISDN>`.
2. MSC looks the MSISDN up in `users`. Not found → CDR with
   `User not found on DB`, session ends. Found → MSC opens a random UDP port
   and replies `UDP_PORT <port>`.
3. Mobile streams 8kHz/16-bit/mono PCM audio to that UDP port in 1024-byte
   chunks; MSC plays it back on the speaker (or records to `.wav` if more
   than one call is active concurrently).
4. A `Timer` deducts **1 L.E per minute** from `users.balance` on the DB
   directly, every 60 seconds, while the call is active.
5. Ctrl+C on Mobile → shutdown hook sends `End Call` over the same TCP
   connection → MSC stops billing, stops the audio thread, and appends one
   CSV line to `/tmp/calls_CDR_yyyy_MM_dd_HH.cdr`.

Optional `--rtp` flag on both sides wraps/unwraps a 12-byte RTP header
around each audio chunk.

**Flow diagram — call setup (signaling):**

```
Mobile app
   │  TCP connect (port 5000)
   ▼
MSC  ◀── "Start Call <MSISDN>"
   │
   ▼
MSISDN in users table?
   │
   ├── No  ──▶ Reject call ──▶ write CDR "User not found on DB" ──▶ end
   │
   └── Yes ──▶ Open UDP socket ──▶ reply "UDP_PORT <port>"
```

**Flow diagram — streaming, billing, and call end:**

```
Mobile app
   │  streams 8kHz/16-bit/mono PCM
   │  in 1024-byte UDP chunks
   ▼
MSC (CallSession, one thread per call)
   │
   ├── single call active   ──▶ play live on speaker
   └── 2+ calls concurrent  ──▶ record to .wav instead
   │
   ▼
Timer (every 60s while call is active)
   │  UPDATE users SET balance = balance - 1 ... RETURNING balance
   ▼
Mobile app: Ctrl+C
   │  shutdown hook sends "End Call" over the same TCP connection
   ▼
MSC: stop billing timer, stop audio thread
   │
   ▼
Append one CSV line to /tmp/calls_CDR_yyyy_MM_dd_HH.cdr
```

### 4.2 Real SIP Track (Zoiper ⇄ Asterisk ⇄ fastagi-app)

This is the production-shaped path: real SIP endpoints register on Asterisk,
and Asterisk delegates the prepaid billing decision to your Java app via
**AGI (Asterisk Gateway Interface)**, specifically the **FastAGI** variant
(Asterisk talks AGI over a TCP socket to an external server instead of
spawning a local script per call).

**Components:**
- **Asterisk** — SIP registrar + dialplan engine. SIP accounts for your
  softphones are defined in `pjsip.conf` (or `sip.conf` on chan_sip), and the
  dialplan (`extensions.conf`) routes calls for prepaid extensions to an
  `AGI()` (or `AGI(agi://host:port/scriptname)`) dialplan application.
- **fastagi-app** (`FastagiApp.java`) — a long-running Java **FastAGI
  server**, listening on a TCP port (FastAGI's conventional default is
  `4573`) for incoming AGI sessions from Asterisk.
- **ChargingAgiScript.java** — the per-call handler invoked by `FastagiApp`
  for each AGI session. It speaks the AGI line protocol: reads the AGI
  environment variables Asterisk sends on connect (e.g. `agi_callerid`,
  `agi_extension`), then issues AGI commands back (e.g. balance checks,
  playing prompts, hanging up) while the call is active.
- **DBUtil.java** (fastagi-app copy) — same JDBC connection helper pattern
  as the other modules, pointed at the same `prepaid_charging` database.

**End-to-end flow:**

```
Zoiper (SIP UA)
   │  REGISTER / INVITE
   ▼
Asterisk  ──dialplan──▶  AGI() app
   │                         │
   │   AGI protocol (TCP)    │
   ▼                         ▼
                     fastagi-app :4573
                      (FastagiApp.java)
                              │
                    per-call thread/session
                              ▼
                    ChargingAgiScript.java
                              │
                    balance check / deduction
                              ▼
                    prepaid_charging DB (users table)
```

1. The SIP phone (Zoiper) registers with Asterisk over SIP.
2. When the user dials, Asterisk's dialplan matches the extension and hits
   an `AGI()` step, which opens a TCP connection to `fastagi-app` and streams
   the AGI environment for that call.
3. `FastagiApp` accepts the connection and hands it off to
   `ChargingAgiScript`, which:
   - Resolves the caller's MSISDN from the AGI environment.
   - Looks the user up via `DBUtil`/`UserDAO` in the same `users` table used
     by the simulated track.
   - Applies the prepaid charging logic (balance check up front and/or
     periodic deduction while the call is bridged), talking back to Asterisk
     over AGI to allow, warn, or reject/hang up the call based on balance.
4. On call completion, a CDR line is written in the **same CSV format** used
   by the simulated track, so the `web-app`'s usage view works for both
   tracks without changes:
   ```
   MSISDN,StartTime,EndTime,DurationMinutes,CallResult,CallCost,BalanceAfterCall
   ```

**Setting it up:**

1. Install Asterisk and configure a SIP account per softphone in
   `pjsip.conf`/`sip.conf`.
2. In `extensions.conf`, route the prepaid test extension(s) to the AGI app,
   e.g.:
   ```
   [prepaid-outbound]
   exten => _X.,1,NoOp(Prepaid call from ${CALLERID(num)})
    same => n,AGI(agi://localhost:4573/charge)
    same => n,Dial(SIP/${EXTEN})
    same => n,Hangup()
   ```
   (Adjust the AGI URI/path and dial target to match how
   `ChargingAgiScript` is wired up in your `FastagiApp` — confirm the actual
   listen port and route name against the code before relying on this
   snippet verbatim.)
3. Build and run `fastagi-app` so it's listening **before** Asterisk routes
   any calls to it:
   ```bash
   cd fastagi-app
   java -jar target/fastagi-app.jar
   ```
4. Register Zoiper (or any SIP UA) with the credentials from
   `pjsip.conf`/`sip.conf`, and place a call to the prepaid extension.
5. Watch `fastagi-app`'s console output and the `users.balance` column to
   confirm charging, and check `/tmp/calls_CDR_*.cdr` for the generated CDR
   line.

> This section documents the general FastAGI/Asterisk wiring pattern this
> project follows. If any port numbers, dialplan context names, or AGI
> command sequences differ in your actual `extensions.conf` /
> `ChargingAgiScript.java`, treat those as the source of truth over this
> README and update the snippet above accordingly.

---

## 5. Web Application (`web-app`)

Deployed on Tomcat 10 (Jakarta EE 6 servlet API). Copy
`src/main/java/com/telecom/web/**` and `src/main/webapp/**` into your
NetBeans Web Application project, add the PostgreSQL JDBC driver to
`WEB-INF/lib`, and run.

Browser opens `index.html` → redirects to `/users`.

**Features:**
- List all users (ID, MSISDN, Balance), low balance (< 5 L.E) highlighted.
- Add / Edit / Delete users.
- **Recharge** — add balance to a user, logged to a `transactions` table
  (`sql/transactions_migration.sql`), viewable per-user under **History**.
- **Usage** — per-user consumption view, parsed live from the
  `/tmp/calls_CDR_*.cdr` files (works for calls from *either* the simulated
  track or the Asterisk/SIP track, since both write the same CDR format).

---

## 6. Testing Flow (End-to-End)

### Simulated track
1. `psql -f sql/schema.sql`.
2. `java msc`, then `java mobile 01223456789`.
3. Speak into the mic — MSC plays it back; wait 2+ minutes and watch balance
   deductions in the MSC console.
4. Ctrl+C on Mobile — CDR line printed and written to
   `/tmp/calls_CDR_<yyyy_MM_dd_HH>.cdr`.
5. Open the web app → confirm balance decreased, check **Usage** tab shows
   the call.
6. `java mobile 01200000000` (MSISDN not in DB) → immediate rejection, CDR
   with `User not found on DB`.
7. Two concurrent Mobile instances with different MSISDNs → both billed
   independently, both recorded to `.wav` instead of played live.

### SIP / Asterisk track
1. Start `fastagi-app`.
2. Start/verify Asterisk is running with the dialplan pointing at it.
3. Register Zoiper with a configured SIP account.
4. Dial the prepaid extension, let the call run a couple of minutes, hang up.
5. Confirm the balance dropped in the web app and the call shows up under
   **Usage** for that MSISDN, same as the simulated track.

---

## 7. Notes / Assumptions

- Balance is allowed to go negative on the simulated track (charging
  continues every minute regardless of balance, per spec); add a balance
  check in `CallSession.startCall()`/billing `TimerTask` if you want the
  call force-disconnected at zero balance. The AGI track should ideally
  enforce a hard stop via `ChargingAgiScript` since it's front-and-center
  for real calls — confirm this is implemented if it matters for your grading
  criteria.
- Audio format on the simulated track is fixed at 8kHz/16-bit/mono PCM —
  keep `AUDIO_FORMAT` in sync between `CallSession` and `MobileApp` if you
  change it.
- The `web-app` uses plain JSP + Servlet (no JSTL/EL frameworks) per the
  original assignment's "HTML, CSS, Servlet" requirement.
- CDR files are shared, on-disk, hourly-rotated CSVs — both `msc-app` and
  `fastagi-app` should write to the **same** `/tmp/calls_CDR_yyyy_MM_dd_HH.cdr`
  naming convention so the web app's Usage view picks up both tracks
  transparently.

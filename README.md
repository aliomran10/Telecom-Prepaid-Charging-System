# Prepaid Charging for Voice Call — TBMP-style Project

This project implements a 3-application prepaid voice-call billing system as
required, plus the bonus requirements (RTP, hourly CDR rotation, WAV recording,
concurrent calls).

## 1. Project Layout

```
PrepaidCharging/
├── mobile-app/    -> "Mobile Phone" application   (java mobile <MSISDN>)
├── msc-app/       -> "MSC" application            (java msc / java -jar msc.jar [--rtp])
├── web-app/       -> Web CRUD for USERS table (Servlet + JSP + CSS, deployed on Tomcat 10)
├── sql/schema.sql -> PostgreSQL schema + seed data
└── lib/           -> place postgresql JDBC driver jar here
```

Each of `mobile-app`, `msc-app`, `web-app` is a separate NetBeans project
(pom.xml included for Maven users; instructions below cover plain NetBeans
"Java Application" / "Web Application" projects too).

---

## 2. Database Setup (PostgreSQL)

You said PostgreSQL is already installed. Run:

```bash
sudo -u postgres psql
```

Inside `psql`:

```sql
CREATE DATABASE prepaid_charging;
\c prepaid_charging
```

Then run the contents of `sql/schema.sql` (creates the `users` table and
seeds 3 test users, including MSISDN `01223456789` with balance `50.00`).

```bash
psql -U postgres -d prepaid_charging -f sql/schema.sql
```

**Update credentials** if needed in:
- `msc-app/src/main/java/com/telecom/msc/util/DBUtil.java`
- `web-app/src/main/java/com/telecom/web/dao/DBUtil.java`

Default: `jdbc:postgresql://localhost:5432/prepaid_charging`, user `postgres`,
password `postgres`.

---

## 3. Get the PostgreSQL JDBC Driver

Download `postgresql-42.7.3.jar` (or any recent 42.x version) from
https://jdbc.postgresql.org/download/ and place it:

- In NetBeans: right-click `msc-app` → Properties → Libraries → Add JAR/Folder
  → select the driver jar. Do the same for `web-app`.
- If using Maven, the `pom.xml` files already declare the dependency
  (`org.postgresql:postgresql:42.7.3`) — Maven will download it automatically.

---

## 4. Running in NetBeans

### 4.1 Mobile Application (`mobile-app`)

1. New Project → Java with Maven → from existing pom (point to `mobile-app/pom.xml`),
   OR create a plain "Java Application" project and copy
   `src/main/java/com/telecom/mobile/MobileApp.java` into it.
2. Set Main Class: `com.telecom.mobile.MobileApp`
3. Run with arguments: `01223456789`
   - In NetBeans: right-click project → Properties → Run → Arguments → `01223456789`

**Expected output:**
```
Starting voice call as MSISDN 01223456789
Capturing Voice from Microphone and send via UDP.....
1 minutes elapsed
2 minutes elapsed
...
```

Press **Ctrl+C** (or stop the process) to end the call — this triggers the
shutdown hook which sends `End Call` to the MSC.

### 4.2 MSC Application (`msc-app`)

1. New Project → Java with Maven from `msc-app/pom.xml`, OR plain Java
   Application + add the PostgreSQL JDBC jar to libraries.
2. Set Main Class: `com.telecom.msc.MSCServer`
3. Run with **no arguments** (or `--rtp` to enable RTP header stripping).

**Expected output:**
```
Waiting for voice call Signaling start message via TCP
Accept Voice call start signaling message from MSISDN 01223456789
Capturing UDP traffic and play via speaker (port 54231) .....
[Billing] 01223456789 charged 1 L.E. Minute 1. New balance: 49.00
[Billing] 01223456789 charged 1 L.E. Minute 2. New balance: 48.00
Call End after receiving end call signaling message
Generating CDR line: 01223456789,2025-03-01T15:24:47.398,2025-03-01T15:26:47.398,2,Normal Call Clearing,2.00,48.00
```

**Important: Start the MSC application FIRST**, then start the Mobile
application (the mobile app connects to the MSC's TCP signaling port 5000).

### 4.3 Web Application (`web-app`)

1. New Project → Java Web → Web Application.
2. Set Server: Apache Tomcat 10 (or later — required for Jakarta EE 6 servlet API).
3. Copy:
   - `src/main/java/com/telecom/web/**` → your project's Java Sources
   - `src/main/webapp/**` (index.html, users.jsp, userForm.jsp, css/style.css,
     WEB-INF/web.xml) → your project's Web Pages
4. Add the PostgreSQL JDBC driver jar to the project's libraries (and ensure
   it's included in `WEB-INF/lib` on deploy).
5. Run the project (Run → Run Project, or right-click → Run).
6. Browser opens `index.html` → redirects to `/users` showing the CRUD table.

**Features:**
- List all users (ID, MSISDN, Balance)
- Add new user
- Edit existing user (MSISDN, Balance)
- Delete user (with confirmation)
- Low balance (< 5 L.E) highlighted in red

---

## 5. How the System Works (Architecture)

### 5.1 Signaling (TCP, port 5000)

1. **Mobile** connects to **MSC** via TCP on port 5000.
2. Mobile sends: `Start Call <MSISDN>`
3. MSC looks up the MSISDN in the database:
   - If **not found** → MSC writes a CDR with result `User not found on DB`
     and ends the session immediately.
   - If **found** → MSC opens a dedicated UDP socket (random free port) and
     replies: `UDP_PORT <port>`
4. Mobile reads the assigned UDP port and starts streaming audio there.
5. When the Mobile application shuts down (Ctrl+C / shutdown hook), it sends
   `End Call` over the same TCP connection.
6. MSC receives `End Call`, stops billing, stops the audio thread, computes
   the final balance, and writes the CDR.

### 5.2 Voice Streaming (UDP, dynamic port per call)

- Mobile captures raw PCM audio from the microphone (`TargetDataLine`,
  8kHz/16-bit/mono) and sends 1024-byte chunks via UDP to the port the MSC
  assigned.
- MSC's `CallSession` runs a dedicated **audio thread** per call that:
  - Receives UDP packets
  - **Single call active** → plays audio live via `SourceDataLine` (PC speaker)
  - **Multiple calls active (concurrent mode)** → instead buffers the audio
    and writes it to a `.wav` file when the call ends
    (`/tmp/voice_call_msisdn_<MSISDN>_date_YYYY_MM_DD_Time_HH_MM_SS.wav`)

### 5.3 Real-Time Charging

- A `java.util.Timer` (`scheduleAtFixedRate`, period = 60,000 ms) fires every
  minute while the call is active.
- Each tick: `UPDATE users SET balance = balance - 1 WHERE msisdn = ? RETURNING balance`
  (atomic deduction directly in PostgreSQL — 1 L.E per minute).

### 5.4 CDR Generation

- On call end, `CallSession` writes one CSV line to
  `/tmp/calls_CDR_yyyy_MM_dd_HH.cdr` (a **new file every hour**, satisfying
  the rotation bonus requirement without an external logging framework):

```
MSISDN,StartTime,EndTime,DurationMinutes,CallResult,CallCost,BalanceAfterCall
```

Example:
```
01223456789,2025-03-01T15:24:47.398253,2025-03-01T15:26:47.398253,2,Normal Call Clearing,2.00,48.00
```

### 5.5 Concurrency

- `MSCServer` uses a `ConcurrentHashMap` + cached `ExecutorService` thread pool —
  each incoming TCP connection (Mobile) is handled by its own `CallSession` on
  its own thread, with its own UDP socket and billing timer.
- `isConcurrentMode()` returns `true` whenever **more than one** call is
  active simultaneously. In that case all active `CallSession`s switch to
  **WAV recording mode** instead of speaker playback (since only one process
  can reasonably own the PC speaker at a time).

### 5.6 RTP Bonus

- Run MSC with `--rtp` flag, and Mobile with `--rtp` flag (3rd argument).
- Mobile wraps each audio chunk in a minimal 12-byte RTP header
  (version, payload type, sequence number, timestamp, SSRC) before sending
  via UDP.
- MSC strips the first 12 bytes of each packet before playback/recording when
  `--rtp` is enabled.

```bash
java msc --rtp
java mobile 01223456789 localhost --rtp
```

---

## 6. Testing Flow (End-to-End)

1. `psql -f sql/schema.sql` → creates `users` table with MSISDN `01223456789`, balance `50.00`.
2. Start **MSC**: `java msc`
3. Start **Mobile**: `java mobile 01223456789`
4. Speak into the microphone — MSC plays it back on the PC speaker.
5. Wait 2+ minutes — watch MSC console log balance deductions.
6. Stop Mobile (Ctrl+C) — MSC prints the CDR line and writes it to
   `/tmp/calls_CDR_<yyyy_MM_dd_HH>.cdr`.
7. Open the **web app** → confirm the user's balance decreased by the call duration.
8. To test "User not found": run `java mobile 01200000000` (an MSISDN not in
   the DB) — MSC should immediately reject the call and log a CDR with
   `User not found on DB`.
9. To test **concurrency**: start two Mobile instances with two different
   (existing) MSISDNs while MSC is running — both calls are charged
   independently, and both get recorded to `.wav` files instead of played at
   the speaker.

---

## 7. Notes / Assumptions

- Balance is allowed to go negative if a call runs past available balance
  (per the spec, charging continues every minute regardless); add a balance
  check in `CallSession.startCall()`/billing `TimerTask` if you want the call
  to be force-disconnected at zero balance.
- Audio format is fixed at 8kHz/16-bit/mono PCM for simplicity and small
  packet sizes — adjust `AUDIO_FORMAT` in `CallSession` and `MobileApp` if you
  need higher fidelity (keep both in sync).
- The web app uses plain JSP + Servlet (no JSTL/EL frameworks) per the
  assignment's "HTML, CSS, Servlet" requirement.

# Moving Queue Up onto Nitro

**Now:** $0/month, but the first visitor waits 46+ seconds · **After:** $0/month, always warm

You are moving two things onto your own Nitro box: the app itself (off Render) and the database
(off Neon). Nothing else moves. Spotify, Cloudinary and the S3 bucket stay exactly where they are
and stay free.

**One small code change is needed**, a WebSocket keepalive, and it is already written. Everything
else is settings.

---

## The idea in one paragraph

This migration is not about money. Render's free tier and Neon's free tier both cost nothing, so
the bill is $0 before and $0 after. The problem is that both of them go to sleep. Render parks a
free web service after 15 minutes with no traffic, and Neon suspends a free database after about 5
minutes idle. I measured it while writing this: the first request to `queue-up.onrender.com` took
longer than 120 seconds to come back, a retry took 46 seconds, and only then did it settle to 0.3
seconds. Anyone opening your link cold, a recruiter for example, sees a blank page for the better
part of a minute and probably leaves. Your Nitro box is already powered on 24/7 with 21 GB of spare
memory, so the app and its database simply stay running. There is no sleep timer to wake up from.

---

## Order of operations: new server first

Like the PaperPulse migration, this deploys to Nitro **before** turning Render off. You keep a
working rollback the whole way, and since both platforms are free there is no overlap cost at all.
There is no scheduled job in this app and no midnight race, so you can stop between any two steps
and leave it sitting as long as you like.

The one thing to be careful about is the database. From the moment you copy the data out of Neon,
anything a user does on the old Render site is written to Neon and will **not** appear on Nitro.
Keep the gap between Step 4 and Step 9 short, or accept that a few messages sent in between could
be lost. Realistically this app has little live traffic, so this is a small risk, but it is the
only genuinely one-way part of the move.

---

## How this one differs from the last two

| | GrabPic / PaperPulse | Queue Up |
|---|---|---|
| Frontend hosting | Vercel, stays put | **bundled into the same JAR, moves with the app** |
| Frontend env vars to repoint | 1 to 2 | **none, the app calls its own origin** |
| Database | Supabase, stayed put | **Neon, moves to a container on Nitro** |
| Data to copy | none | **users, matches and chat history** |
| Code changes | 2 files / none | **1 file, a WebSocket keepalive** |
| Third parties to tell about the new URL | none | **Spotify and the S3 bucket** |

The frontend point is the nice one. The React app is compiled into
`Backend/src/main/resources/static` and served by Spring Boot, so there is only one thing to
deploy. It calls the API at `/api` and opens its WebSocket against `window.location.host`, so it
follows the app to any domain with no rebuild needed for the URL.

---

## What it costs

| | Per month |
|---|---|
| Now | $0 |
| After | $0 |

Render Free and Neon Free are both $0, and Nitro is already powered on. The S3 bucket and
Cloudinary are unchanged and stay on whatever they cost you now, which at this size is pennies.

> These are read off the pricing tiers, not off your accounts. The reason to do this is the cold
> start, not the bill.

---

## What I checked before writing this

I built and ran the real image on Nitro against a real Postgres, so none of the below is guessed:

| | |
|---|---|
| `Dockerfile` builds clean on Nitro | yes, 54 seconds, no code fixes needed |
| Image size | 442 MB |
| App boots against Postgres 17 | yes, `Started BackendApplication in 4.634 seconds` |
| Hibernate builds the schema from empty | yes, 13 tables created |
| `/api/ping` | returns `pong` |
| `/api/health` | returns `{"status":"ok"}` with the database check passing |
| SPA deep links | `/login` returns 200, the SPA filter forwards to `index.html` |
| WebSocket upgrade | HTTP 101, and the server pushes `userOnline` immediately |
| WebSocket with a mismatched origin | **HTTP 403**, so `APP_CLIENT_URL` has to be exact |
| The new heartbeat message | accepted and ignored, connection stays open, nothing logged |
| Memory at idle | app 381 MB, Postgres 34 MB (Nitro has 21 GB free) |
| `curl` / `wget` in the runtime image | no `curl`, but busybox `wget` is there |
| Frontend build variables | confirmed baked into the JS bundle at build time |
| Render cold start, measured today | first request over 120s, retry 46s, warm 0.3s |

Three things that will bite if you do not know them, all found during that test:

- **`AWS_REGION` and `AWS_S3_BUCKET` are needed at build time, not just at run time.** They get
  compiled into the JavaScript. Get this wrong and chat attachments upload fine but every link
  points at `https://undefined.s3.undefined.amazonaws.com/`. Covered in Step 6.
- **`APP_CLIENT_URL` gates the WebSocket.** A mismatch returns 403 on the handshake and chat goes
  dead while the rest of the site looks perfectly healthy. Covered in Step 6.
- **Coolify defaults every new app to port 3000.** This one listens on 8080. Also Step 6.

---

## Step 1: Copy the settings out of Render and Neon (done)

**Why:** There is no `.env` file in this repo. I checked. The Render dashboard is the only place
these values exist in one list, and you need all of them in Step 6.

**Do this before touching anything else.** Nothing is stopped or deleted here.

**Render dashboard** → your `queue-up` service → **Environment**. Copy all of:

```
SPRING_APPLICATION_NAME
SPRING_PROFILES_ACTIVE
APP_CLIENT_URL
APP_BOT_CREATION_ENABLED

SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD

CLOUDINARY_CLOUD_NAME
CLOUDINARY_API_KEY
CLOUDINARY_API_SECRET

AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_REGION
AWS_S3_BUCKET

SPOTIFY_CLIENT_ID
SPOTIFY_CLIENT_SECRET
SPOTIFY_REDRIECT_URI

JWT_SECRET
OPENAI_API_KEY
```

> **`SPOTIFY_REDRIECT_URI` is misspelled**, and that is not a typo in this document. The name in
> `application.yml` is spelled `REDRIECT`, so the variable in Coolify has to be misspelled the same
> way or Spring will not start. Do not fix it while copying.

**Keep `JWT_SECRET` identical.** Every logged in user is holding a cookie signed with it. Reuse the
same value and nobody gets logged out by the move. Change it and everyone is signed out at once.

Also grab the **Neon connection string**: Neon console → your project → **Connection Details** →
copy the `postgresql://...` URI, the pooled or direct one, either works for a dump. You need it in
Step 4.

**You'll know it worked:** you have all 20 values and the Neon URI saved somewhere outside this
repo. Do not commit them.

---

## Step 2: Give Queue Up a web address (done)

**Why:** the app needs a public name that resolves to your house without exposing your IP.

The tunnel on Nitro already sends everything at `*.shahirahmed.com` into Coolify, so there is
nothing to edit on the server. You only need the DNS record.

**Cloudflare dashboard → DNS → Add record:**

| Field | Value |
|---|---|
| Type | `CNAME` |
| Name | `queue-up` |
| Target | `e7ccba18-5d3a-4417-9045-ff41b4906292.cfargotunnel.com` |
| Proxy status | **Proxied** (orange cloud on) |

> **Keep subdomains one level deep.** Cloudflare's free SSL covers `shahirahmed.com` and
> `*.shahirahmed.com` only. `queue-up.shahirahmed.com` is fine. Something like
> `app.queue-up.shahirahmed.com` sits two levels down, gets no certificate, and would need
> Advanced Certificate Manager at $10/month.

**You'll know it worked:** `https://queue-up.shahirahmed.com` returns a Coolify 404 page instead of
a DNS error. A 404 here is correct, nothing is deployed yet.

---

## Step 3: Postgres on Nitro (done)

**Why:** this is what replaces Neon, and it is the half of the cold start problem that the app
cannot hide. Even a warm app is slow if the database underneath it is asleep.

Already done while writing this runbook. A `postgres:17` container is running, attached to the
`coolify` network with no published host ports, so it is reachable from other containers by name
and from nowhere else:

```bash
docker volume create queueup-postgres-data
docker run -d --name queueup-postgres --network coolify --restart unless-stopped \
  -e POSTGRES_USER=queueup -e POSTGRES_PASSWORD="<password>" -e POSTGRES_DB=queueup \
  -v queueup-postgres-data:/var/lib/postgresql/data \
  postgres:17
```

The generated password is saved on Nitro at **`~/queueup-postgres-password.txt`**, the same
convention as `~/paperpulse-neo4j-password.txt`. Read it with:

```bash
cat ~/queueup-postgres-password.txt
```

It is running **PostgreSQL 17.11** and the database is currently empty. I booted the app against it
to prove the whole thing works, then dropped the schema again so Step 4 starts clean.

**Because it has a named volume and no expiry timer, this is strictly better than Neon Free.** Neon
suspends after 5 minutes idle, and free databases on every provider eventually get reclaimed. This
one does not pause, does not expire, and gets backed up with the rest of the box.

**You'll know it worked:**

```bash
docker exec queueup-postgres psql -U queueup -d queueup -c "select version();"
```

prints `PostgreSQL 17.11`.

---

## Step 4: Copy the data over from Neon

**Why:** so your existing users, matches and chat history survive the move. Without this, Hibernate
would happily build an empty schema on first boot and every account would be gone.

Run all of this **on Nitro**. There is no `pg_dump` installed on the box, so borrow one from the
Postgres image.

**First, check what version Neon is running:**

```bash
docker run --rm postgres:17 psql "<your Neon URI>" -c "select version();"
```

If that says PostgreSQL 17 or lower, carry on. If Neon is on 18, use `postgres:18` in the two
commands below instead, since a dump taken by an older `pg_dump` from a newer server is not
supported.

**Take the dump:**

```bash
docker run --rm postgres:17 pg_dump --no-owner --no-privileges \
  "<your Neon URI>" > ~/queueup-neon-dump.sql
```

`--no-owner --no-privileges` matters. Without them the dump carries Neon's role names, and the
restore fails on every `ALTER TABLE ... OWNER TO neondb_owner`.

If the password has symbols in it, percent-encode them in the URI (`@` is `%40`, `#` is `%23`).

**Sanity check the dump before you trust it:**

```bash
ls -lh ~/queueup-neon-dump.sql
grep -c "^CREATE TABLE" ~/queueup-neon-dump.sql
```

You should see 13 tables and a file that is not zero bytes. The 13 are `app_user`, `artist`,
`attachment`, `message`, `message_link_previews`, `track`, `user_dislikes`,
`user_followed_artists`, `user_likes`, `user_matches`, `user_saved_tracks`, `user_top_artists` and
`user_top_tracks`.

**Restore it:**

```bash
docker exec -i queueup-postgres psql -U queueup -d queueup < ~/queueup-neon-dump.sql
```

If you ever need to start this step over, empty the database first, otherwise the restore collides
with what is already there:

```bash
docker exec queueup-postgres psql -U queueup -d queueup \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO queueup;"
```

**You'll know it worked:** the row counts match what Neon has.

```bash
docker exec queueup-postgres psql -U queueup -d queueup \
  -c "select count(*) as users from app_user;" \
  -c "select count(*) as messages from message;" \
  -c "select count(*) as matches from user_matches;"
```

Compare those three numbers against the same query run on the Neon URI. They should be identical.

---

## Step 5: Push the WebSocket keepalive

**Why:** Cloudflare's free plan closes a proxied connection after **100 seconds of silence**. Queue
Up's chat socket sends nothing while you are just reading, so on an idle chat Cloudflare would cut
it roughly every 100 seconds. The client does reconnect 3 seconds later on its own, so chat would
still basically work, but you would flicker offline to everyone else on every cycle, and a message
that arrived inside the gap would not show up until the chat was reopened.

The fix is 24 lines in `Frontend/src/socket/socket.client.js`: send `{"type":"ping"}` every 45
seconds while the socket is open, and clear the timer on close and on logout. **This is already
written and sitting uncommitted in your working tree.** The backend needs no change at all: its
handler only acts on `"typing"` and ignores everything else, which I confirmed by sending the exact
frame at a running container and watching the connection stay open with nothing logged.

Coolify builds from GitHub and cannot see local files, so push it:

```bash
git add Frontend/src/socket/socket.client.js MIGRATION.md
git commit -m "Add WebSocket heartbeat to survive Cloudflare's 100s idle timeout"
git push
```

**You'll know it worked:** after Step 6 is deployed, open the site, open the browser console, leave
a chat sitting untouched for three minutes, and confirm you never see `WebSocket Disconnected`.

---

## Step 6: Deploy in Coolify

**Why:** this is the actual move.

Coolify → **New Resource** → **Application** → **Public Repository** →
`https://github.com/Shahir-47/Queue-Up` → Build Pack **Dockerfile**.

### Settings, field by field

| Field | Value | Coolify's default |
|---|---|---|
| Name | `queue-up` | generated |
| Base Directory | `/` | `/` |
| Dockerfile Location | `/Dockerfile` | `/Dockerfile` |
| Ports Exposes | `8080` | **`3000`, must change** |
| Domains | `http://queue-up.shahirahmed.com` | **an `sslip.io` name, must change** |
| Health check | **disabled** | enabled |

**Base Directory stays `/` here.** Unlike the GrabPic and PaperPulse apps, the Dockerfile is at the
repo root and does `COPY . .`, because the Maven build reaches sideways into `../Frontend` to
compile the React app. Point the base directory at `Backend` and the frontend build cannot find its
own source.

**The port is the one that silently breaks everything.** Nothing sets `server.port`, so Spring Boot
listens on 8080. Leaving Coolify's default 3000 means the proxy forwards to a port nothing is
listening on and every request 502s.

**Type the domain as `http://`, not `https://`.** Cloudflare terminates TLS. Typing `https://`
makes Coolify chase its own certificate, which fails through a tunnel.

**Health check:** leave it off, matching your other three apps. If you would rather have one, this
image can actually support it, unlike PaperPulse's: there is no `curl` but busybox `wget` is
present, so this works:

```
wget -q --spider http://127.0.0.1:8080/api/ping
```

Use `/api/ping` and not `/api/health` for that. `/api/health` returns 503 when the database is
unreachable, which would make Coolify keep restarting a perfectly healthy app during a database
blip. Save `/api/health` for the Uptime Kuma check in Step 10.

Do not add a host port mapping. Coolify reaches the container over its own network. Publishing 8080
on the host would collide with Coolify's proxy dashboard, which is already on 8080.

### Environment variables

Paste the values from Step 1, with these four changed:

```
SPRING_APPLICATION_NAME=QueueUp
SPRING_PROFILES_ACTIVE=prod
APP_CLIENT_URL=https://queue-up.shahirahmed.com      ← changed
APP_BOT_CREATION_ENABLED=false                       ← whatever Render has, bots were disabled in a4961f4

SPRING_DATASOURCE_URL=jdbc:postgresql://queueup-postgres:5432/queueup    ← changed
SPRING_DATASOURCE_USERNAME=queueup                                      ← changed
SPRING_DATASOURCE_PASSWORD=                          ← cat ~/queueup-postgres-password.txt

CLOUDINARY_CLOUD_NAME=               ← from Step 1
CLOUDINARY_API_KEY=                  ← from Step 1
CLOUDINARY_API_SECRET=               ← from Step 1

AWS_ACCESS_KEY_ID=                   ← from Step 1
AWS_SECRET_ACCESS_KEY=               ← from Step 1
AWS_REGION=                          ← from Step 1, needed at BUILD time too
AWS_S3_BUCKET=                       ← from Step 1, needed at BUILD time too

SPOTIFY_CLIENT_ID=                   ← from Step 1
SPOTIFY_CLIENT_SECRET=               ← from Step 1
SPOTIFY_REDRIECT_URI=https://queue-up.shahirahmed.com/api/auth/spotify/callback    ← changed

JWT_SECRET=                          ← from Step 1, keep it identical
OPENAI_API_KEY=                      ← from Step 1
```

**`SPRING_DATASOURCE_URL` uses the container name, not an IP and not `localhost`.** Both containers
sit on the `coolify` network, so `queueup-postgres` resolves. There is no `?sslmode=require` on the
end either: the connection never leaves the Docker network, and Postgres 17 in that container is
not listening for TLS.

**`APP_CLIENT_URL` must be exactly `https://queue-up.shahirahmed.com`.** `https`, no trailing
slash, no `www`. It is the single allowed CORS origin and the single allowed WebSocket origin. I
tested a mismatch against a running container and the WebSocket handshake came back **403**, which
looks like "chat is broken" and nothing else.

**`AWS_REGION` and `AWS_S3_BUCKET` have to reach the build, not just the container.** The
Dockerfile turns them into `VITE_AWS_REGION` and `VITE_S3_BUCKET` and Vite compiles them into the
JavaScript bundle. I checked your Coolify: every existing variable on your other apps has both
`is_runtime` and `is_buildtime` set to true, which is the default, so adding them normally is
enough. Just confirm it in Step 8 rather than assuming, because the failure is silent: uploads
still succeed and the saved link is simply broken forever.

**You'll know it worked:** green in Coolify, and `https://queue-up.shahirahmed.com/api/ping`
returns `pong`. The build takes about a minute. In the logs you want
`Started BackendApplication in ...` and Tomcat on port 8080.

---

## Step 7: Tell Spotify and S3 about the new address

**Why:** these are the two outside services that have your old URL written down. Neither one is
guessable from the code, and both fail in confusing ways.

### Spotify

**Spotify Developer Dashboard** → your app → **Settings** → **Edit** → **Redirect URIs** → add:

```
https://queue-up.shahirahmed.com/api/auth/spotify/callback
```

**Add it, do not replace the Render one yet.** Spotify allows several, and keeping the old one is
what makes the rollback in Step 9 work. Remove the Render URI at Step 10.

If this is missed, login gets as far as Spotify's consent screen and then fails with
`INVALID_CLIENT: Invalid redirect URI`.

### The S3 bucket

Chat attachments are uploaded by the browser straight to S3 with a presigned URL, so the **bucket's
CORS policy** decides whether the new domain is allowed to upload at all.

**S3 console** → your bucket → **Permissions** → **Cross-origin resource sharing (CORS)** → add the
new origin alongside the existing one:

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedOrigins": [
      "https://queue-up.onrender.com",
      "https://queue-up.shahirahmed.com"
    ],
    "ExposeHeaders": ["ETag"]
  }
]
```

Keep whatever methods and headers are already in there, this is only about adding the origin. If
this is missed, picking a file in chat fails in the browser console with a CORS error on the PUT
and the toast says "Could not upload file."

**You'll know it worked:** Step 8 covers both.

---

## Step 8: Check it actually works

Do this before turning Render off, while you still have something to compare against.

- [ ] `https://queue-up.shahirahmed.com` loads the app, and a hard refresh on a deep link like
      `/profile` still loads rather than 404ing
- [ ] Your existing account is still there and you can log in with your old password
- [ ] **You were not logged out.** If you were, `JWT_SECRET` does not match Step 1
- [ ] Old matches and old chat history are visible, which is the real proof Step 4 worked
- [ ] Connect Spotify from a fresh account, all the way through the consent screen
- [ ] The swipe feed loads and is sorted, so the matching engine is reading music data
- [ ] Open a chat and send a message, confirm it appears on the other side with no refresh
- [ ] The green online dot appears and typing indicators work
- [ ] **Upload a file in chat, then reopen it from the other account.** This is the one that
      catches the build variable problem. Right click the attachment, copy the link, and confirm it
      does not contain the word `undefined`
- [ ] Change your profile picture, which exercises Cloudinary
- [ ] Paste a URL into a chat and confirm the link preview renders
- [ ] Leave a chat open and untouched for **three minutes**, then send a message from the other
      side and confirm it arrives instantly. This is the heartbeat from Step 5 doing its job

A faster version of the build variable check, without uploading anything: open the site, view
source on the main JS bundle, and search for `.s3.`. You want to see your real bucket and region
there, not `undefined`.

---

## Step 9: Point the old address at the new one

**Why:** `queue-up.onrender.com` is in your README, your GitHub profile and anywhere you have
shared it. Those links should keep working.

Render Free does not offer redirects, so the honest options are:

1. **Leave the Render service running** and just update your README and links. Costs nothing, and
   the old URL keeps serving the old app pointed at Neon, which will slowly drift out of date.
2. **Suspend the Render service** and update your links. The old URL then goes to a Render error
   page. Cleanest, and the one I would pick.

Either way, update the README:

```
https://queue-up.onrender.com  →  https://queue-up.shahirahmed.com
```

`README.md` mentions it in three places: the Live Demo badge on line 17, the Demo Video line on
line 43, and the "live and available at" line near the bottom on line 1060. The badge needs two
edits on its own, since the Render URL is both the link target and the text printed on the badge
image (`Live%20Demo-queue--up.onrender.com`).

**You'll know it worked:** the README badge takes you to the Nitro copy.

---

## Step 10: Retire Render and Neon (wait a week first)

Once it has been running for a week with real use, and you have confirmed chat, Spotify login and
attachments all work:

- **Render:** dashboard → your service → **Settings** → **Delete Service**
- **Neon:** take one last dump first, `pg_dump` exactly as in Step 4, and keep the file somewhere
  off the box. Then delete the project.
- Remove the old `https://queue-up.onrender.com/api/auth/spotify/callback` redirect URI from the
  Spotify dashboard
- Remove `https://queue-up.onrender.com` from the S3 bucket CORS policy

**Do not delete Neon before you have that final dump somewhere safe.** It is the only copy of any
messages sent to the Render site after Step 4, and deleting a Neon project is immediate and final.

**One last thing:** open Uptime Kuma on Nitro (port 3002) and add an HTTP check on
`https://queue-up.shahirahmed.com/api/health` every 5 minutes. That endpoint returns 503 when the
database is unreachable, so the check covers both containers at once and tells you if either one
falls over. Unlike your other projects, this one has nothing left that needs keeping awake, since
the database is now yours.

---

## Notes from the actual migration

- **The build is faster than expected.** 54 seconds end to end on Nitro, including installing
  Node 20, running `npm install --legacy-peer-deps`, a Vite build of 2112 modules in 2.9 seconds,
  and the Maven package. The image comes out at 442 MB.
- **The frontend needed no URL configuration at all.** `axios` is created with `baseURL: "/api"`
  and the socket URL is built from `window.location.host`, so the whole app follows its own domain.
  This is the payoff of bundling the React build into the JAR, and it is why this migration has no
  equivalent of PaperPulse's "redeploy Vercel" step.
- **The runtime image has `wget` but not `curl`.** PaperPulse's `python:3.11-slim` had neither,
  which is why its health check had to be disabled outright. `eclipse-temurin:21-jre-alpine` ships
  busybox, so a health check here is a real option.
- **`spring-boot-devtools` is on the classpath** as a runtime dependency. It disables itself inside
  a packaged JAR, so it is harmless, but it is why you may see it mentioned in the startup logs.
- **`ddl-auto` is set to `update`.** Hibernate reconciles the schema on every boot, which is what
  makes the Neon restore forgiving: even if the dump is slightly behind the entity classes, the app
  will add what is missing on startup. It also means the app will happily start against a
  completely empty database and give you a working but empty site, which is exactly the failure you
  would see if Step 4 were skipped. An empty swipe feed is the symptom.
- **The Hikari pool is configured with `minimum-idle: 0`.** That was tuned for Neon, where holding
  connections open kept a metered database awake. Against a local Postgres it just means the first
  request after a quiet spell pays a few milliseconds to open a connection. Not worth changing, but
  worth knowing it is deliberate.

---

## If something goes wrong

Nothing on Render or Neon is deleted until Step 10, so you can always go back:

1. The Render service is still running and still pointed at Neon, so it is already live
2. Send people back to `https://queue-up.onrender.com`
3. The old Spotify redirect URI is still registered, so login there still works

Takes about a minute, since the rollback is "stop using the new URL". The only thing that does not
roll back is data: anything created on the Nitro copy after Step 4 lives only in
`queueup-postgres`, and anything created on Render after Step 4 lives only in Neon. They do not
merge.

---

## Later, if you feel like it

**The `Backend/Dockerfile` build could be cached better.** It does `COPY . .` before running Maven,
so any change to any file, including this document, invalidates the whole layer and re-downloads
every dependency. Splitting the `pom.xml` and `package.json` copies into their own earlier layers
would cut rebuilds from 54 seconds to a few. Purely a convenience, and it changes the Dockerfile,
so it is not part of this migration.

**Nothing else here has a sleep timer or an expiry clock left.** That is the actual win. Render's
15 minute spin down and Neon's 5 minute suspend are both gone, the S3 bucket and Cloudinary do not
pause, and the Postgres container has a named volume and restarts with the box.

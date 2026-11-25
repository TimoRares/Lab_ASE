#!/usr/bin/env python3
"""
Populeaza instakill.db cu date de test.

Utilizare (din folderul backend, cu backend-ul OPRIT):
    pip install faker bcrypt
    python populate_db.py                      # setari implicite: 10.000 utilizatori
    python populate_db.py --users 500          # mai putini utilizatori (rulare rapida)
    python populate_db.py --users 2000 --min-posts 5 --max-posts 8 --seed 42

Ce face:
  1. Sterge continutul tabelelor comments, likes, posts si users (pastreaza utilizatorii alice si bob).
  2. Adauga utilizatorul charlie (parola: "password", hash bcrypt) si --users utilizatori generati.
  3. Pentru fiecare utilizator genereaza postari, iar pentru fiecare postare comentarii si like-uri.
Toate insertiile se fac intr-o tranzactie, in loturi (batch-uri).

Formatul datelor este cel scris de aplicatie: id-uri UUID (text) si timestamp-uri in milisecunde epoch.
Tipul media nu e o coloana: se deduce din extensia lui image_url (.jpg = poza, .mp4 = video).
"""
import argparse
import random
import sqlite3
import sys
import time
import uuid
from datetime import datetime, timedelta

try:
    import bcrypt
    from faker import Faker
except ImportError:
    sys.exit("Lipsesc dependentele. Ruleaza: pip install faker bcrypt")

KEEP_USERS = ("alice", "bob")

# Linkuri publice, folosite doar ca date de test (poze si videoclipuri reale, ca sa se vada in feed)
PHOTO_URL = "https://picsum.photos/seed/instakill{n}/300/200.jpg"
VIDEO_URLS = [
    "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
    "https://www.w3schools.com/html/mov_bbb.mp4",
]


def parse_args():
    p = argparse.ArgumentParser(description="Populeaza instakill.db cu date de test")
    p.add_argument("--db", default="instakill.db", help="calea catre baza SQLite (implicit: instakill.db)")
    p.add_argument("--users", type=int, default=10_000, help="numar de utilizatori generati (implicit: 10000)")
    p.add_argument("--min-posts", type=int, default=10, help="minim postari / utilizator (implicit: 10)")
    p.add_argument("--max-posts", type=int, default=20, help="maxim postari / utilizator (implicit: 20)")
    p.add_argument("--max-comments", type=int, default=10, help="maxim comentarii / postare (implicit: 10)")
    p.add_argument("--max-likes", type=int, default=20, help="maxim like-uri / postare (implicit: 20)")
    p.add_argument("--batch-users", type=int, default=500,
                   help="la cati utilizatori se face commit (implicit: 500)")
    p.add_argument("--password", default="password", help="parola lui charlie si a utilizatorilor generati")
    p.add_argument("--seed", type=int, default=None, help="seed pentru rezultate reproductibile")
    args = p.parse_args()
    if args.users < 0 or args.min_posts < 0 or args.max_posts < args.min_posts:
        p.error("valori invalide pentru --users / --min-posts / --max-posts")
    return args


def ms(dt: datetime) -> int:
    return int(dt.timestamp() * 1000)


def main():
    args = parse_args()
    if args.seed is not None:
        random.seed(args.seed)
    fake = Faker()
    if args.seed is not None:
        Faker.seed(args.seed)

    started = time.time()
    now = datetime.now()
    today = now.date()
    week_ago = today - timedelta(days=7)

    conn = sqlite3.connect(args.db)
    conn.execute("PRAGMA foreign_keys = OFF")
    tables = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    missing = {"users", "posts", "comments", "likes"} - tables
    if missing:
        sys.exit(f"Tabele lipsa in {args.db}: {sorted(missing)}. Porneste o data backend-ul ca sa creeze schema.")

    password_hash = bcrypt.hashpw(args.password.encode(), bcrypt.gensalt(rounds=10)).decode()

    # ---- 1. curatare (intr-o tranzactie) ----
    conn.execute("BEGIN")
    conn.execute("DELETE FROM comments")
    conn.execute("DELETE FROM likes")
    conn.execute("DELETE FROM posts")
    placeholders = ",".join("?" * len(KEEP_USERS))
    conn.execute(f"DELETE FROM users WHERE username NOT IN ({placeholders})", KEEP_USERS)
    # charlie e recreat de la zero
    conn.execute("DELETE FROM users WHERE username = 'charlie'")
    existing = {username: uid for uid, username in conn.execute("SELECT id, username FROM users")}  # alice, bob
    used_names = set(existing) | {"charlie"}
    used_emails = {r[0] for r in conn.execute("SELECT email FROM users")} | {"charlie@example.com"}

    # ---- 2. utilizatori ----
    user_rows = []
    now_ms = ms(now)
    charlie_id = str(uuid.uuid4())
    user_rows.append((charlie_id, now_ms, "charlie@example.com", password_hash, "charlie"))
    for _ in range(args.users):
        username = fake.user_name()
        while username in used_names:                 # username-uri unice
            username = f"{fake.user_name()}{random.randint(1, 99999)}"
        used_names.add(username)
        email = f"{username}@example.com"
        while email in used_emails:
            email = f"{username}{random.randint(1, 99999)}@example.com"
        used_emails.add(email)
        created = ms(fake.date_time_between(start_date="-60d", end_date="-8d"))
        user_rows.append((str(uuid.uuid4()), created, email, password_hash, username))

    conn.executemany(
        "INSERT INTO users (id, created_at, email, password_hash, username) VALUES (?,?,?,?,?)", user_rows)
    conn.commit()

    # toti utilizatorii care postează / dau like / comenteaza: alice, bob, charlie + cei generati
    all_ids = list(existing.values()) + [r[0] for r in user_rows]
    print(f"Utilizatori in baza: {len(all_ids)} (inclusiv alice, bob, charlie)")

    # ---- 3. postari, comentarii, like-uri, in loturi ----
    conn.execute("DROP INDEX IF EXISTS idx_likes_post_user")   # reconstruit la final (inserari mai rapide)
    total_posts = total_comments = total_likes = 0
    posts_b, comments_b, likes_b = [], [], []

    def flush():
        nonlocal posts_b, comments_b, likes_b
        conn.execute("BEGIN")
        conn.executemany(
            "INSERT INTO posts (id, author_id, comment_count, content, created_at, image_url, like_count, updated_at)"
            " VALUES (?,?,?,?,?,?,?,?)", posts_b)
        conn.executemany(
            "INSERT INTO comments (id, author_id, content, created_at, post_id) VALUES (?,?,?,?,?)", comments_b)
        conn.executemany(
            "INSERT INTO likes (id, created_at, post_id, user_id) VALUES (?,?,?,?)", likes_b)
        conn.commit()
        posts_b, comments_b, likes_b = [], [], []

    for idx, author_id in enumerate(all_ids, start=1):
        for _ in range(random.randint(args.min_posts, args.max_posts)):
            post_id = str(uuid.uuid4())
            # created_on in ultima saptamana, cu faker.date_between_dates (+ ora aleatoare din zi, nu in viitor)
            day = fake.date_between_dates(date_start=week_ago, date_end=today)
            created_dt = datetime.combine(day, datetime.min.time()) + timedelta(seconds=random.randint(0, 86399))
            if created_dt > now:
                created_dt = now - timedelta(seconds=random.randint(1, 3600))
            created = ms(created_dt)

            if random.random() < 0.5:                                  # photo
                image_url = PHOTO_URL.format(n=random.randint(1, 1_000_000))
            else:                                                       # video
                image_url = random.choice(VIDEO_URLS)

            content = " ".join(fake.words(nb=random.randint(1, 10)))   # pana la 10 cuvinte
            n_comments = random.randint(0, args.max_comments)
            n_likes = min(random.randint(0, args.max_likes), len(all_ids))

            span = max(1, now_ms - created)
            for _ in range(n_comments):
                comments_b.append((str(uuid.uuid4()), random.choice(all_ids), " ".join(fake.words(nb=random.randint(1, 10))),
                                   created + random.randint(0, span), post_id))
            for liker in random.sample(all_ids, n_likes):              # utilizatori distincti => fara dubluri
                likes_b.append((str(uuid.uuid4()), created + random.randint(0, span), post_id, liker))

            posts_b.append((post_id, author_id, n_comments, content, created, image_url, n_likes, created))
            total_posts += 1
            total_comments += n_comments
            total_likes += n_likes

        if idx % args.batch_users == 0:
            flush()
            print(f"  {idx}/{len(all_ids)} utilizatori procesati "
                  f"({total_posts} postari, {total_comments} comentarii, {total_likes} like-uri)")
    flush()

    # ---- 4. indexuri utile interogarilor de analytics ----
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_likes_post_user ON likes (post_id, user_id)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_comments_post ON comments (post_id)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_posts_author ON posts (author_id)")
    conn.commit()

    # ---- 5. verificare integritate ----
    orphans = {
        "posts fara autor": "SELECT COUNT(*) FROM posts WHERE author_id NOT IN (SELECT id FROM users)",
        "comentarii fara postare": "SELECT COUNT(*) FROM comments WHERE post_id NOT IN (SELECT id FROM posts)",
        "comentarii fara autor": "SELECT COUNT(*) FROM comments WHERE author_id NOT IN (SELECT id FROM users)",
        "like-uri fara postare": "SELECT COUNT(*) FROM likes WHERE post_id NOT IN (SELECT id FROM posts)",
        "like-uri fara utilizator": "SELECT COUNT(*) FROM likes WHERE user_id NOT IN (SELECT id FROM users)",
    }
    bad = {name: conn.execute(q).fetchone()[0] for name, q in orphans.items()}
    conn.close()

    print(f"\nGata in {time.time() - started:.1f}s: {len(all_ids)} utilizatori, {total_posts} postari, "
          f"{total_comments} comentarii, {total_likes} like-uri.")
    if any(bad.values()):
        print("ATENTIE, inregistrari orfane:", {k: v for k, v in bad.items() if v})
        sys.exit(1)
    print("Integritate OK (fara inregistrari orfane). Utilizatori de test: alice, bob, charlie (parola: "
          f"{args.password}).")


if __name__ == "__main__":
    main()

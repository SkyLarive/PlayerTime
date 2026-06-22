# PlayTime

Ein kleines Paper/Spigot-Plugin, das die Spielzeit pro Spieler mitschreibt und in MariaDB ablegt.
Gezählt wird zwischen Join und Quit, gespeichert wird persistent, und mit `/playtime` sieht jeder
seine eigene Zeit.

## Bauen

Im Projektordner:

```bash
mvn clean package
```

Danach liegt die fertige Jar unter `target/playertime-1.0.0.jar`.

## Installieren

1. Die Jar nach `plugins/` auf deinen Server kopieren
2. Server einmal starten. Das Plugin legt dabei automatisch die `config.yml` und die Tabelle in der DB an.
3. Server stoppen, in `plugins/playertime/config.yml` deine DB-Daten eintragen.
4. Server wieder starten. Fertig.

## Konfiguration

Die `config.yml` sieht so aus:

```yaml
database:
  host: localhost
  port: 3306
  name: minecraft
  user: root
  password: ""
  pool-size: 10
  use-ssl: false

save-interval-seconds: 300
```

- `host`, `port`, `name`, `user`, `password`: deine MariaDB-Zugangsdaten. `name` ist der Datenbankname,
  die Datenbank selbst muss schon existieren (die Tabelle macht das Plugin).
- `pool-size`: wie viele DB-Verbindungen der Pool maximal offen hält. 10 reicht für die meisten Server locker.
- `use-ssl`: auf `true`, wenn deine DB SSL verlangt.
- `save-interval-seconds`: wie oft die Online-Spieler zwischengespeichert werden. Default sind 300 Sekunden,
  Minimum 30.

## Befehle und Rechte

`/playtime` | Zeigt deine eigene Spielzeit | für alle |
`/playtime <Spieler>` | Zeigt die Zeit eines anderen (online oder offline) | `playtime.others` (Standard: OP) |

Aliase: `/pt` und `/spielzeit`.

## Tabellen-Layout

```sql
CREATE TABLE playtime (
    uuid CHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    playtime_seconds BIGINT NOT NULL DEFAULT 0,
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

Die UUID ist der Primärschlüssel, der Name wird mitgeführt, damit Offline-Abfragen über
`/playtime <Spieler>` funktionieren.

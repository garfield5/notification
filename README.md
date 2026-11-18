# Értesítési szolgáltatás

Java 21 és Spring Boot alapú Maven mintaprojekt. A REST API PostgreSQL-ben rögzíti a kérést és az outbox állapotát egy tranzakcióban. Egy ütemezett folyamat a kézbesítési feladatot Kafka-ba írja, a fogyasztó pedig a kézbesítés eredményét az adatbázisban tárolja. Az EMAIL, SMS és PUSH csatornákhoz a demó naplózó küldőt használ.

A minta a [Medium-cikk nyilvános előnézetéből](https://medium.com/but-it-works-on-my-machine/system-design-interview-architecting-a-scalable-notification-service-with-spring-boot-d2edee229a43) indul ki.

## Indítás

JDK 21, Maven 3.6.3+ és Docker Compose v2 szükséges. Az adatbázis és a broker a helyi 5432-es és 9092-es portot használja. Az alkalmazás alapértelmezésben a helyi 8080-as porton figyel.

```bash
docker compose up -d --wait
mvn clean package
java -jar target/notification-service-0.1.0.jar
```

## Kipróbálás

```bash
curl -i http://localhost:8080/api/notifications \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: example-1' \
  -d '{"channel":"EMAIL","recipient":"demo:retry","message":"Szia!"}'
```

A válasz HTTP 202. Az eredmény a `Location` fejlécben megadott útvonalon kérdezhető le.

```bash
curl 'http://localhost:8080/api/notifications/IDE_JON_AZ_AZONOSITO'
```

| Címzett | Várt eredmény |
| --- | --- |
| Tetszőleges normál érték | `SENT` egy próbálkozás után |
| `demo:retry` | `SENT` két próbálkozás után |
| `demo:fail` | `FAILED` három próbálkozás után |
| `demo:reject` | `FAILED` egy próbálkozás után |

Átmeneti hiba esetén az új próbálkozások 2, majd 4 másodperc elteltével válnak esedékessé. Azonos idempotenciakulcs és azonos tartalom ugyanazt az azonosítót adja vissza. Eltérő tartalom ugyanazzal a kulccsal HTTP 409 választ eredményez.

## Feldolgozási garanciák

- A relay Kafka-visszaigazolás után jelöli publikáltnak a feladatot. Ha a visszaigazolás után a PostgreSQL-tranzakció megszakad, ugyanaz az esemény újra megjelenhet.
- A fogyasztó sorzárral ellenőrzi az aktuális állapotot és próbálkozásszámot. Így egy korábbi vagy már feldolgozott próbálkozás ismételt eseménye átugorható.
- Valós külső szolgáltatónál az elküldés és az adatbázis-commit közötti kiesés ismételt kézbesítést okozhat. Ha a szolgáltató támogatja, az értesítés UUID-ja átadható szolgáltatói idempotenciakulcsként.
- A három Kafka-partíció lehetővé teszi a párhuzamos fogyasztást. A Compose egyetlen brokert indít, 1-es replikációs faktorral.

Leállításhoz állítsd le a Java folyamatot, majd futtasd a `docker compose down` parancsot. A Docker-adatkötetek megőrzik a tárolt adatokat.

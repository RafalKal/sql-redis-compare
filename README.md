# Redis SQL Shop Demo

Demonstracyjna aplikacja Spring Boot pokazująca, jak PostgreSQL i Redis uzupełniają się w systemie sklepu internetowego.

## 1. Cel projektu

Celem projektu jest porównanie dwóch typów magazynowania danych:

- PostgreSQL dla danych trwałych, relacyjnych i krytycznych biznesowo,
- Redis dla danych szybkich, tymczasowych i często modyfikowanych.

Projekt nie ma udowadniać, że Redis jest zawsze lepszy od PostgreSQL. Pokazuje raczej, że obie technologie mają inne role i najlepiej działają razem.

## 2. Architektura SQL + Redis

### PostgreSQL

PostgreSQL jest głównym źródłem prawdy dla danych biznesowych:

- `users`
- `products`
- `orders`
- `order_items`
- `invoices`

Dodatkowo w bazie SQL są tabele pomocnicze do porównania z Redis:

- `sql_user_sessions`
- `sql_cart_items`
- `sql_product_views`
- `sql_product_sales_stats`

### Redis

Redis przechowuje dane o wysokiej zmienności i niskim koszcie odtworzenia:

- `session:{userId}` - sesje użytkowników, TTL 30 minut,
- `cart:{userId}` - koszyki użytkowników, hash z TTL 24 godziny,
- `cache:product:{productId}` - cache produktu, TTL 10 minut,
- `views:product:{productId}` - licznik wyświetleń,
- `bestsellers` - ranking bestsellerów jako sorted set.

## 3. Technologie

- Java
- Spring Boot
- Gradle
- PostgreSQL
- Redis
- Flyway
- Docker Compose
- REST API + `CommandLineRunner`

## 4. Tryby działania

Tryb wybiera się parametrem `app.mode`:

- `seed` - czyści PostgreSQL i Redis oraz zasila je danymi testowymi,
- `demo` - wykonuje scenariusz biznesowy i wypisuje logi,
- `benchmark` - uruchamia porównanie SQL vs Redis i zapisuje wyniki do CSV.

Przykłady:

```bash
./gradlew bootRun --args="--app.mode=seed"
./gradlew bootRun --args="--app.mode=demo"
./gradlew bootRun --args="--app.mode=benchmark --benchmark.iterations=10000"
```

## 5. Dane w PostgreSQL

W PostgreSQL przechowywane są:

- użytkownicy,
- katalog produktów,
- zamówienia,
- pozycje zamówień,
- faktury.

To są dane, które wymagają:

- trwałości,
- relacji,
- integralności,
- transakcyjności,
- możliwości późniejszego audytu.

## 6. Dane w Redis

W Redis przechowywane są:

- sesje użytkowników,
- koszyki,
- cache produktów,
- liczniki wyświetleń,
- ranking bestsellerów.

To są dane, które:

- często się zmieniają,
- nie zawsze muszą być trwałe,
- dobrze pasują do operacji key-value, hash, counter i sorted set,
- mają przyspieszać obsługę aplikacji.

## 7. Uruchomienie Dockera

Domyślnie aplikacja używa portów:

- PostgreSQL: `5432`
- Redis: `6379`

Uruchomienie:

```bash
docker compose up -d
```

Zatrzymanie:

```bash
docker compose down
```

Jeżeli porty `5432` albo `6379` są zajęte lokalnie, można użyć override:

```bash
POSTGRES_HOST_PORT=15432 REDIS_HOST_PORT=16379 docker compose up -d
```

Wtedy aplikację uruchamia się z:

```bash
APP_POSTGRES_PORT=15432 APP_REDIS_PORT=16379 ./gradlew bootRun --args="--app.mode=demo"
```

PowerShell:

```powershell
$env:POSTGRES_HOST_PORT='15432'
$env:REDIS_HOST_PORT='16379'
docker compose up -d

$env:APP_POSTGRES_PORT='15432'
$env:APP_REDIS_PORT='16379'
.\gradlew.bat bootRun --args="--app.mode=demo"
```

## 8. Uruchomienie aplikacji

Seed:

```bash
./gradlew bootRun --args="--app.mode=seed"
```

Demo:

```bash
./gradlew bootRun --args="--app.mode=demo"
```

Benchmark:

```bash
./gradlew bootRun --args="--app.mode=benchmark --benchmark.iterations=10000 --benchmark.warmupIterations=1000"
```

Wynik benchmarku zapisuje się do:

```text
build/reports/benchmark-results.csv
```

## 9. Scenariusz demo

Tryb `demo` wykonuje następujące kroki:

1. pobiera użytkownika o ID `1`,
2. tworzy sesję w SQL i Redis,
3. pobiera produkt `10` z SQL,
4. zapisuje go do cache Redis,
5. dodaje produkt do koszyka w SQL i Redis,
6. odczytuje koszyk z obu magazynów,
7. zwiększa licznik wyświetleń,
8. zwiększa sprzedaż i aktualizuje ranking,
9. pobiera TOP 10 bestsellerów,
10. tworzy zamówienie i fakturę w PostgreSQL,
11. wypisuje, dlaczego faktura nie jest głównym przypadkiem użycia Redis.

## 10. REST API

Najważniejsze endpointy:

- `POST /api/demo/seed`
- `POST /api/demo/scenario`
- `POST /api/demo/benchmark`
- `GET /api/demo/sql/product/{id}`
- `GET /api/demo/redis/product/{id}`
- `POST /api/demo/sql/cart/{userId}/product/{productId}`
- `POST /api/demo/redis/cart/{userId}/product/{productId}`
- `GET /api/demo/sql/cart/{userId}`
- `GET /api/demo/redis/cart/{userId}`
- `GET /api/demo/bestsellers/sql`
- `GET /api/demo/bestsellers/redis`

## 11. Przykładowe wyniki benchmarku

Poniżej wynik z jednego rzeczywistego uruchomienia na tej maszynie dla `10000` iteracji:

| Operacja | Iteracje | SQL [ms] | Redis [ms] | Różnica |
|---|---:|---:|---:|---:|
| Odczyt produktu | 10000 | 6990.62 | 9514.17 | 0.73x |
| Utworzenie sesji | 10000 | 18525.43 | 7284.40 | 2.54x |
| Dodanie produktu do koszyka | 10000 | 15703.86 | 12563.77 | 1.25x |
| Odczyt koszyka | 10000 | 4954.98 | 5165.61 | 0.96x |
| Licznik wyświetleń | 10000 | 12027.15 | 5736.39 | 2.10x |
| Aktualizacja rankingu bestsellerów | 10000 | 13524.06 | 8063.43 | 1.68x |
| TOP 10 bestsellerów | 10000 | 9232.09 | 7739.43 | 1.19x |

Przykładowy plik CSV:

```csv
operation,iterations,sql_ms,redis_ms,ratio
read_product,10000,6990.62,9514.17,0.73
create_session,10000,18525.43,7284.40,2.54
add_to_cart,10000,15703.86,12563.77,1.25
read_cart,10000,4954.98,5165.61,0.96
increment_views,10000,12027.15,5736.39,2.10
update_bestsellers,10000,13524.06,8063.43,1.68
top_bestsellers,10000,9232.09,7739.43,1.19
```

## 12. Wnioski

Najważniejsze obserwacje:

1. Redis zwykle dobrze wypada w operacjach krótkich i mutowalnych, takich jak sesje, liczniki i ranking sorted set, bo operuje w pamięci.
2. PostgreSQL pozostaje właściwym miejscem dla trwałych danych biznesowych: zamówień, produktów i faktur.
3. Wyniki benchmarku zależą od środowiska, sterowników, serializacji, sieci i kształtu danych. Nie należy ich interpretować jako uniwersalnego dowodu, że Redis jest zawsze szybszy.
4. W sklepie internetowym najlepszym podejściem jest użycie obu technologii razem: PostgreSQL jako systemu transakcyjnego i Redis jako warstwy przyspieszającej.

## 13. Weryfikacja

Projekt został przygotowany tak, aby spełniać poniższe wymagania:

- uruchamianie przez Gradle,
- migracja Flyway,
- seed danych testowych,
- scenariusz demo,
- benchmark SQL vs Redis,
- zapis wyników do CSV,
- prosty REST API do uruchamiania operacji.

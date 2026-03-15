# Final Summary — Consumer Groups, Rebalancing & Horizontal Scalability

## Как работают consumer groups

Consumer group — механизм Kafka для параллельной обработки сообщений из топика несколькими consumer-инстансами. Основные принципы, подтверждённые экспериментами:

1. **Один partition — один consumer в группе.** Kafka назначает каждую партицию ровно одному consumer в пределах группы. В эксперименте 3.1 единственный consumer получил все 4 партиции. В 3.2 при двух consumers — по 2 партиции каждому. В 3.3 при 5 consumers и 4 партициях один consumer остался idle (без партиций).

2. **Координатор группы (group coordinator)** — брокер, ответственный за назначение партиций. При каждом изменении состава группы (join/leave/failure) координатор инициирует ребалансировку: отзывает (revoke) партиции и назначает (assign) заново согласно выбранной стратегии (Range, RoundRobin, Sticky, CooperativeSticky).

3. **Manual commit** гарантирует, что offset продвигается только после успешной обработки. Если consumer умирает между обработкой и commit — новый владелец партиции может переобработать те же сообщения (at-least-once семантика).

4. **Consumer lag** — разница между log-end offset и committed offset. Во всех экспериментах финальный lag = 0, что означает полную обработку всех сообщений.

### Данные из экспериментов

| Сценарий | Consumers | Partitions | Throughput (combined msg/sec) | Idle |
|----------|-----------|------------|-------------------------------|------|
| 3.1 Baseline | 1 | 4 | 2 820 | 0 |
| 3.2 Phase 1 | 2 | 4 | 6 798 | 0 |
| 3.2 Phase 2 | 3 | 4 | 7 873 | 0 |
| 3.3 Over-provision | 5 | 4 | 12 984 | 1 |

---

## Почему масштабирование Kafka — это не просто «добавить инстансы»

Эксперименты 3.2 и 3.3 наглядно показали ограничения горизонтального масштабирования:

1. **Параллелизм ограничен числом партиций.** `max_parallelism = min(consumers, partitions)`. В 3.3 пятый consumer не получил партиций, не обработал ни одного сообщения и не увеличил throughput. Число партиций — это потолок масштабирования consumer group.

2. **Добавление consumer вызывает ребалансировку.** В 3.2 при добавлении третьего consumer партиции были перераспределены. Это означает кратковременную остановку обработки на затронутых партициях (при eager rebalancing — на всех).

3. **Неравномерное распределение при нечётном соотношении.** В 3.2 Phase 2 (3 consumers, 4 partitions) один consumer получил 2 партиции, два — по одной. Нагрузка не идеально равномерна.

4. **Throughput растёт не линейно.** 1 consumer → 2 820, 2 → 6 798 (×2.4), 3 → 7 873 (×1.16 к Phase 1), 4 активных → 12 984. Рост зависит от баланса партиций и узких мест (сеть, диск, consumer processing time).

---

## Какие проблемы возникают при ребалансировке

Эксперименты 3.4 и 3.5 продемонстрировали стоимость ребалансировок:

### 1. Задержка обнаружения failure (~45 секунд)

При kill consumer-2 в 3.4 и 3.5 ребалансировка произошла только через ~45–47 секунд — время `session.timeout.ms`. Всё это время partition мёртвого consumer не обрабатывается, lag растёт. Это главная проблема failure-triggered rebalancing.

### 2. Eager rebalancing: stop-the-world

В 3.4 (eager, дефолтная стратегия) при ребалансировке **все** партиции у **всех** survivors были revoked и затем reassigned:
- consumer-1: revoked [2], assigned [2, 3] — durationMs = 26
- consumer-3: revoked [0, 1], assigned [0, 1] — durationMs = 27

Даже партиции, не меняющие владельца, на мгновение теряют consumer. При длительной обработке это может привести к дубликатам (незакоммиченные сообщения будут переобработаны).

### 3. Дубликаты при manual commit

В наших экспериментах дубликатов не было — обработка была быстрой, и consumer-2 успел закоммитить offset до kill. Но в реальном продакшене с длительной обработкой (DB-операции, HTTP-вызовы) между обработкой и commit может пройти значительное время. При kill в этом окне — дубликаты неизбежны (at-least-once).

### 4. Количество ребалансировок

| Сценарий | Триггер | Ребалансировок | Длительность |
|----------|---------|----------------|--------------|
| 3.1 Baseline | Старт | 1 (initial) | 0 ms |
| 3.2 Scale up | New consumer | 1 per phase | instant |
| 3.3 Over-provision | 5 join | несколько (initial) | instant |
| 3.4 Failure (eager) | Kill | 1 | 26–27 ms + 47 sec detection |
| 3.5 Failure (cooperative) | Kill | 1 | 0 ms + 45 sec detection |

---

## Какие архитектурные решения помогают минимизировать влияние ребалансировок

На основании экспериментов и наблюдений — рекомендации:

### 1. Cooperative rebalancing (CooperativeStickyAssignor)

Эксперимент 3.5 показал: при cooperative rebalancing партиции survivors **не отзываются**. consumer-3 продолжил обработку partitions 0, 3 без перерыва (processingDurationMs = 190 мс, `revokedPartitions = []`). При eager (3.4) все партиции были revoked у обоих survivors.

**Рекомендация:** использовать `CooperativeStickyAssignor` в продакшене. Настройка:

```yaml
spring.kafka.consumer.properties.partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor
```

### 2. Правильное число партиций

Эксперимент 3.3 показал: consumers > partitions ⇒ idle инстансы без прироста throughput. Партиции нужно планировать заранее с запасом — увеличение числа партиций на живом топике возможно, но необратимо и вызывает ребалансировку.

**Рекомендация:** количество партиций = ожидаемый максимум consumers (с запасом 2–3×).

### 3. Уменьшение session.timeout.ms

В 3.4 и 3.5 задержка обнаружения failure составила ~45 сек (дефолт). Это время partition мёртвого consumer простаивает. Уменьшение `session.timeout.ms` (например, до 10–15 секунд) ускоряет обнаружение, но увеличивает риск ложных срабатываний при GC-паузах или сетевых задержках.

**Рекомендация:** баланс между скоростью обнаружения и стабильностью. Типичные значения: `session.timeout.ms=15000`, `heartbeat.interval.ms=5000`.

### 4. Идемпотентная обработка

При manual commit и at-least-once семантике дубликаты возможны при failure. Consumer-side deduplication (по messageId, idempotent DB-операции) — обязательна для критичных данных.

### 5. Static group membership (group.instance.id)

Назначение фиксированного `group.instance.id` каждому consumer позволяет Kafka помнить назначение партиций при рестарте. Consumer может переподключиться без ребалансировки (в пределах `session.timeout.ms`). Критично для rolling deployments.

### 6. Мониторинг consumer lag

Consumer lag — главный индикатор здоровья consumer group. При ребалансировке lag временно растёт. Мониторинг lag по партициям позволяет обнаружить:
- проблемы с производительностью consumer,
- зависшие partition (мёртвый consumer до session.timeout),
- дисбаланс нагрузки.

---

## Стратегия ребалансировки по экспериментам

| Эксперимент | Assignment Strategy | Тип ребалансировки |
|-------------|--------------------|--------------------|
| 3.1 Baseline | `RangeAssignor` (default) | Eager — все партиции revoked, затем assigned заново |
| 3.2 Scale up | `RangeAssignor` (default) | Eager |
| 3.3 Over-provision | `RangeAssignor` (default) | Eager |
| 3.4 Failure | `RangeAssignor` (default) | Eager |
| 3.5 Cooperative | `CooperativeStickyAssignor` | Cooperative (incremental) — revoke только мигрирующих партиций, survivors не затронуты |

В экспериментах 3.1–3.4 использовалась дефолтная стратегия Kafka — `RangeAssignor` с **eager rebalancing**: при каждой ребалансировке **все** партиции **всех** consumers отзываются (revoke), а затем назначаются заново (assign). Это гарантирует корректное перераспределение, но вызывает кратковременную остановку обработки на всех партициях — даже на тех, которые не меняют владельца.

В эксперименте 3.5 мы переключились на `CooperativeStickyAssignor` — **cooperative (incremental) rebalancing**: отзываются только те партиции, которые реально мигрируют к другому consumer. Партиции survivors остаются у них без revoke. Это подтверждено данными: `revokedPartitions = []` у обоих выживших consumers.

---

## Experimental Table (по форме из задания)

| Scenario | Consumers | Partitions | Rebalance Trigger | Observed Behavior |
|----------|-----------|------------|-------------------|-------------------|
| 3.1 Baseline | 1 | 4 | none | Stable processing, all 4 partitions on 1 consumer, throughput 2820 msg/s |
| 3.2 Scale up | 2 → 3 | 4 | new consumer | Partition redistribution, throughput +15.8%, no message loss |
| 3.3 Over-provision | 5 | 4 | 5 consumers join | 1 consumer idle, throughput capped at 4 active consumers |
| 3.4 Failure (eager) | 3 → 2 | 4 | consumer crash | ~47 sec detection pause, all partitions revoked/reassigned, 26–27 ms rebalance |
| 3.5 Failure (cooperative) | 3 → 2 | 4 | consumer crash | ~45 sec detection pause, no revoke on survivors, 0 ms rebalance |

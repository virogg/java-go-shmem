# java-go-shmem


Межпроцессное общение между Go и Java с использование mmap файлов, lock-free кольцевого буффера с низкой задержкой, с пакетной обработкой в общей памяти. \
Он использует:
- барьеры памяти через [volatile операции](java/src/main/java/com/jgshmem/utils/MemoryVolatileLong.java) вместо блокировок, чтобы сообщения отправлялись как можно быстрее.
- кольцевой буффер в общей памяти, поэтому даже с небольшим объемом памяти можно передавать неограниченное количество сообщений другому процессу.

Поскольку кольцевой буффер представляет собой ограниченную круговую очередь, реализованный подход заключается в том, что у нас есть ожидающий producer и consumer.

Другими словами, producer кольца будет ждать, когда кольцо будет полным, а consumer кольца будет ждать, когда кольцо будет пустым. По сути, медленный consumer заставит producer'а ждать, пока в кольце появится свободное место. Consumer читает все сообщения в том же порядке, в котором они были отправлены producer'ом.

Для максимальной производительности производитель и потребитель должны быть в состоянии "*busy-spin*" во время ожидания, опрашивая кольцо.

---

Поддерживает два backend'а:
- `mmap` (по умолчанию): память отображается в mmap файлы.
- `posix_shm`: именованные POSIX сегменты (`shm_open` + JNI), требует сборки нативной библиотеки.

## Что внутри
- Протокол кольца: два числа последовательности (producer/consumer) плюс данные, размер заголовка 2 × 64 байта.
- Go: `pkg/ring` (работа с кольцом), `pkg/memory` (mmap/posix_shm), `pkg/message/PayloadMessage` (длина + payload).
- Java: `com.jgshmem.ring.WaitingRingProducer/WaitingRingConsumer`, бэкенды в `com.jgshmem.backend`, JNI доступ в `com.jgshmem.memory.PosixShmMemory`.
- Конфигурация в `shared/config*.json` (пути файлов/имён shm, `capacity`, `maxObjectSize`).

## Требования
- Go toolchain.
- __JDK11__ [(1)](#p1) + Maven.
- Для POSIX shm: `make`, clang/cc, доступ к `shm_open`/`shm_unlink`; на macOS нужна сборка JNI (см. ниже).

<a><p id="p1">(1)</a> Тут важно, что мы используем именно JDK версии 11, т.к. предполагается использовать проект в связке с Hadoop HBase, а рекомендованная версия там 11.</p>

## Примеры и скрипты
- Echo-пример: запустить Java `JavaEchoService`, затем Go `cmd/example/main.go` (оба читают `shared/config.json` или `config-posix.json`).
- Интеграция POSIX shm: `scripts/run_integration_test_posix.sh`.
- Сравнение backend'ов: `scripts/run_compare_backends.sh`.
- Сброс сегментов/файлов: `go run -tags posixshm cmd/reset/main.go -config shared/config-posix.json` или `scripts/run_integration_test_posix.sh` (делает reset перед запуском).

## Настройка конфигурации
- Для `mmap` укажите `filename`.
- Для `posix_shm` укажите `shmName`, начинающийся с `/`.
- `capacity` × `maxObjectSize` + заголовок должны совпадать на обеих сторонах (Go/Java). \
POSIX shm выравнивается до размера страницы ОС.

## Нюансы
- Кольцевые буфферы — фиксированного размера; переполнение приводит к busy-spin до появления свободного места.
- POSIX shm требует выравнивания и отсутствия старых сегментов; скрипты выполняют `reset`.
- Java Unsafe может требовать `--add-opens` (флаги заданы в `scripts/common.sh` через `JAVA_OPTS`).

## Бенчмарки
| Metric | mmap backend | POSIX shm backend |
|---|---:|---:|
| Latency Min | 125 ns | 84 ns |
| Latency Max | 216.834 µs | 1.218917 ms |
| Latency Avg | 254 ns | 220 ns |
| Latency P50 | 250 ns | 208 ns |
| Latency P90 | 250 ns | 250 ns |
| Latency P99 | 750 ns | 375 ns |
| Throughput | 15,488,571 msg/s | 17,909,854 msg/s |
| Throughput latency | 0.06 µs/msg | 0.06 µs/msg |

## Тесты
- Go: `go test ./...` и при необходимости `go test -tags posixshm ./...`.
- Java: `mvn test` с нужными `--add-opens`.  
- Integration: `scripts/run_integration_test_posix.sh` или `scripts/run_integration_test.sh` для mmap.


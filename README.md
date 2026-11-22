# Smart Cold Storage Monitor

Smart Cold Storage Monitor is a Java application for cold storage facility managers, quality assurance officers and maintenance engineers. Food, vaccines and medicines are damaged when storage temperatures leave their safe range, and manual checks can miss short spikes and slow, prolonged violations. The system simulates real-time cold storage sensors from recorded datasets, classifies every reading against the safe range of 2.0 to 4.0 °C as COMPLIANT, WARNING or NON-COMPLIANT, alerts users about unsafe conditions, tracks running statistics for each storage room, evaluates whether the third-party maintenance contractor meets its temperature standard, and records every violation in a tamper-evident, SHA-256 hash-linked audit log aligned with NIST SP 800-53.

## Team

| Member          | GitHub Username                                    | Role                                                   |
|-----------------|----------------------------------------------------|--------------------------------------------------------|
| Aakriti Kaul    | [@aak-kaul](https://github.com/aak-kaul)           | Project setup, core monitoring pipeline, audit logging |
| Yazi Zhang      | [@Lizik01](https://github.com/Lizik01)             | ML analytics (statistics, temperature prediction)      |
| Anqi Zhou       | [@AnqiAngelZhou](https://github.com/AnqiAngelZhou) | Views (desktop GUI and web dashboard)                  |
| Togzhan Usseiin | [@Usseiint](https://github.com/Usseiint)           | Sensor data ingestion and real-time simulation         |

## Repository Structure

The layout follows the Model–View–Controller design from our Phase 1 document. The model holds domain state, the controller coordinates each reading and notifies observers, and the view only displays results. Data ingestion, sensor simulation, analytics and audit logging are kept in their own packages.

```
smart-cold-storage-monitor/
  pom.xml                                Maven build (Java 17, JUnit 5)
  src/main/java/edu/northeastern/coldstorage/
    Main.java                            Entry point: runs the core use case end to end
    model/                               Domain objects: SensorReading, StorageUnit, Alert,
                                         ComplianceStatus, MonitoringReport
    data/                                ColdStorageDataReader: CSV ingestion, skips invalid rows
    sensor/                              Sensor interface, CsvReplaySensor, SpikeSensor,
                                         SensorType, SensorFactory (Factory pattern),
                                         SensorSimulator (multi-threaded real-time simulation)
    ml/                                  ComplianceChecker (threshold rules, contractor 5% rule),
                                         StatisticsCalculator (mean, variance, std deviation)
    controller/                          MonitorController (store, check, alert, report),
                                         AlertListener (Observer interface)
    view/                                ConsoleAlertView: colour-coded alerts and summary
    logging/                             AuditLogger (Singleton, SHA-256 hash chain), AuditEntry
  src/main/resources/
    IoT.csv                              Kaggle dataset: 4,977 readings, 3 rooms
  src/test/java/edu/northeastern/coldstorage/
    controller/ data/ logging/ ml/       JUnit 5 test suites, one per component
    model/ sensor/ view/
```

## Setup and Run

**Prerequisites:** Java 17 or newer, Maven 3.8+ (bundled with IntelliJ IDEA), and Git. No accounts, API keys or environment variables are needed. The dataset is included in `src/main/resources/IoT.csv`.

```
# Clone
git clone https://github.com/aak-kaul/smart-cold-storage-monitor.git
cd smart-cold-storage-monitor

# Install dependencies and build
mvn compile

# Run the application
mvn compile exec:java

# Run tests
mvn test
```

**In IntelliJ IDEA:** File → Open → select the project folder and wait for Maven to load. Run `src/main/java/edu/northeastern/coldstorage/Main.java` with the ▶ button. To run the tests, right-click `src/test/java` and choose **Run 'All Tests'**.

To analyze another CSV in the same format: `mvn compile exec:java -Dexec.args="path/to/file.csv"`. The audit log is written to `logs/audit.log` (git-ignored).

**Expected output (abridged):**

```
Loaded 4977 readings from IoT.csv (0 invalid rows skipped)
Safe range: 2.0 to 4.0 C, warning margin +/- 1.0 C
ALERT [WARNING] Cold_Storage_C at 2024-01-02T00:00: 4.17 C -> YELLOW
...
  COMPLIANT      (GREEN ):  2404  (48.3%)
  WARNING        (YELLOW):  2207  (44.3%)
  NON-COMPLIANT  (RED   ):   366  (7.4%)
Mean temperature   : 4.07 C
Contractor (TMC) compliance: FAIL (7.4% non-compliant, limit 5.0%)
Audit log: 2576 entries written to logs/audit.log, integrity check: PASSED
```

**Dataset:** [IoT Based Cold Storage Monitoring System](https://www.kaggle.com/datasets/sohilsingh11/iot-based-cold-storage-mointoring-system) (Kaggle), `IoT.csv`: 4,977 hourly readings from `Cold_Storage_A`, `B` and `C`, January–July 2024. The actual mean is 4.07 °C (range −3.94 to 18.97 °C), which updates the estimate in our Phase 1 proposal.

## Feature Status

| Functional Requirement (from Phase 1) | Status      | Where to Find It | Notes |
|---------------------------------------|-------------|------------------|-------|
| FR1: Sensor data ingestion            | Complete    | `data/ColdStorageDataReader` (CSV ingestion, two datasets); `sensor/SensorSimulator` (real-time simulation at an adjustable interval, all sensors running concurrently on a `ScheduledExecutorService`); `sensor/SensorFactory` | Historical CSV ingestion is complete, with columns looked up by name and invalid rows skipped and counted. Real-time simulation with multi-threading added in Phase 3. |
| FR2: Machine learning analytics       | [Partial / Complete] | `ml/StatisticsCalculator`, running statistics in `model/StorageUnit`; [prediction classes, if added] | Mean and variance are computed overall and per room; each `StorageUnit` keeps running statistics. Regression-based temperature prediction is not started. [update if added] |
| FR3: Compliance monitoring            | Complete    | `ml/ComplianceChecker` (COMPLIANT / WARNING / NON-COMPLIANT, contractor 5% rule) | Every reading is classified as COMPLIANT (2.0–4.0 °C), WARNING (within 1.0 °C outside, up to the 5.0 °C breach limit) or NON-COMPLIANT. The contractor fails if more than 5% of readings are non-compliant. |
| FR4: Web dashboard                    | [Not started / Partial] | [package or files, if added] | A console dashboard stands in for now. Spring Boot and Chart.js are planned. [update if added] |
| FR5: Alert management                 | [Partial]   | `controller/AlertListener`, `view/ConsoleAlertView`; [acknowledge / silence commands, if added] | Alerts are raised for every unsafe reading and pushed to all listeners (Observer). Visual and audio alarms and acknowledge/silence actions are not started; `Alert` already supports acknowledgement in the model. [update if added] |
| FR6: Security and audit logging       | [Partial]   | `logging/AuditLogger`, `logging/AuditEntry` (SHA-256 hash chain, integrity check) | Violations and system events go into a SHA-256 hash-linked log with integrity verification (NIST SP 800-53 AU-2, AU-3, AU-9). Logging of operator actions will follow once those actions exist. |

**Working end to end:** the core use case "Detect unsafe storage conditions."

1. `ColdStorageDataReader` loads readings from `IoT.csv`.
2. `SensorFactory` creates one sensor per room, and `SensorSimulator` runs them concurrently, sending one reading per sensor per interval.
3. For each reading, `MonitorController` stores it in its room's `StorageUnit`, updating the current temperature, status and running mean and variance.
4. `ComplianceChecker` classifies the reading as COMPLIANT, WARNING or NON-COMPLIANT.
5. If the reading is unsafe, an `Alert` is created and every registered `AlertListener` is notified.
6. `ConsoleAlertView` prints the alert with its colour, and `AuditLogger` records it as a hash-linked entry.
7. A `MonitoringReport` summarises status counts, statistics, each room's state and the contractor's PASS/FAIL result.

## Scope and Design Changes Since Phase 1

| Change | Reason |
|---|---|
| Maven instead of Gradle, in a single module | Simpler setup; Maven is bundled with IntelliJ IDEA, so every team member could build without extra installs. |
| WARNING defined as a reading up to 1.0 °C outside the 2.0–4.0 °C range (capped at the 5.0 °C breach limit); anything beyond is NON-COMPLIANT | The proposal had overlapping ranges for WARNING and the critical range; one clear rule was needed for testing. |
| Contractor rule: TMC fails when more than 5% of readings are NON-COMPLIANT | The proposal stated the 5% rule in two different ways (below 2.0 / above 5.0 °C, and above 3.9 °C); we kept one version. |
| Dataset statistics updated (mean 4.07 °C, range −3.94 to 18.97 °C) | The real `IoT.csv` differs from the estimates in the Phase 1 proposal. |
| Sensors are simulated by replaying recorded data (`CsvReplaySensor`), plus a `SpikeSensor` that injects temperature spikes | No physical hardware is available; the spike sensor lets us trigger alerts on purpose in demos and tests. |
| [Other changes, e.g. JavaFX vs Swing choice, ML library choice] | [Reason] |

## Design Patterns and Principles Applied

| Pattern / Principle | Where | Why |
|---|---|---|
| MVC | `model/`, `view/`, `controller/` packages | Keeps domain state, display and coordination separate, so new views can be added without changing the model. |
| Observer | `AlertListener` implemented by `ConsoleAlertView` and `AuditLogger`; notified by `MonitorController` | New views (desktop GUI, web dashboard) can subscribe to alerts without changing the controller. |
| Singleton | `AuditLogger` | Exactly one audit trail, so every event lands in the same hash chain. |
| Factory | `SensorFactory.create(SensorType, id, readings)` returns `CsvReplaySensor` or `SpikeSensor` | The simulator depends only on the `Sensor` interface. A new sensor type needs one new class and one new case in the factory, with no other changes. |
| Decorator | `SpikeSensor` wraps any `Sensor` | Adds temperature spikes to an existing sensor's readings without changing that sensor. |
| Thread safety (concurrency) | `SensorSimulator` (`ScheduledExecutorService`); `synchronized` methods in `MonitorController` and `StorageUnit` | Several sensors report at the same time. Synchronizing `process()` makes each reading's store, check, alert and audit steps one atomic operation, so no readings are lost and the audit hash chain stays consistent (NFR: concurrency safety). |
| [Strategy / Command, if added] | [Classes] | [Why] |

```mermaid
classDiagram
    class Sensor {
        <<interface>>
        +getId() String
        +nextReading() Optional~SensorReading~
    }
    class SensorFactory {
        +create(SensorType, String, List) Sensor
    }
    class SensorSimulator {
        +start()
        +awaitCompletion(long, TimeUnit) boolean
        +stop()
    }
    class MonitorController {
        +addListener(AlertListener)
        +process(SensorReading) ComplianceStatus
        +processAll(List) MonitoringReport
    }
    class AlertListener {
        <<interface>>
        +onAlert(Alert)
    }
    class AuditLogger {
        <<singleton>>
        +getInstance() AuditLogger
        +verifyIntegrity() boolean
    }
    Sensor <|.. CsvReplaySensor
    Sensor <|.. SpikeSensor
    SpikeSensor --> Sensor : wraps
    SensorFactory ..> Sensor : creates
    SensorSimulator --> Sensor : polls on a thread pool
    SensorSimulator --> MonitorController : sends readings to
    MonitorController --> ComplianceChecker : classifies with
    MonitorController --> StorageUnit : stores readings in
    MonitorController --> AlertListener : notifies
    AlertListener <|.. ConsoleAlertView
    AlertListener <|.. AuditLogger
    AuditLogger --> AuditEntry : hash-linked chain
    ColdStorageDataReader --> SensorReading : creates
```

## Testing

- **Framework:** JUnit 5, run through Maven Surefire.
- **Phase 2 baseline:** 33 unit tests across 5 test classes, all passing (`ComplianceCheckerTest` 9, `StatisticsCalculatorTest` 4, `ColdStorageDataReaderTest` 7, `MonitorControllerTest` 7, `AuditLoggerTest` 6), plus `StorageUnitTest`, `ConsoleAlertViewTest` and the extra reader tests added by teammates.
- **Unit tests:**
  - `ComplianceCheckerTest` (9): green, yellow and red rules, exact 2.0 and 4.0 boundaries, custom and invalid limits, the contractor 5% rule
  - `StatisticsCalculatorTest` (4): mean, variance and standard deviation against hand-calculated values; empty input
  - `ColdStorageDataReaderTest`: valid parsing, bad rows skipped, any column order, missing columns, empty input, both datasets, byte-order mark, padded values, Windows line endings
  - `StorageUnitTest`: running mean, variance and standard deviation per room; readings from the wrong room rejected
  - `MonitorControllerTest` (7): alerts only for unsafe readings, multiple and removed listeners, per-room statistics, report totals
  - `AuditLoggerTest` (6): singleton, hash linking, detection of edited and deleted entries, alert logging, file output
  - `ConsoleAlertViewTest`: status colours, alert display limit
  - `SensorFactoryTest` (4): correct class per sensor type, CSV replay order, spike on every 10th reading, null type rejected
- **Integration and concurrency tests** (`SensorSimulatorTest`):
  - Three sensors running concurrently through the full pipeline (simulator → controller → compliance check → alerts); checks that all 150 readings are processed and the spike sensor raises exactly 5 alerts
  - 10 threads writing 10,000 readings to one room at the same time; checks that no reading is lost
  - Invalid simulator arguments are rejected
- **Results:** [60] tests, [59] passing, [1] failing (see Known Issues).

```
mvn test                       # all tests
mvn test -Dtest=SensorSimulatorTest   # one test class
```

## Known Issues

- `ConsoleAlertViewTest.nonCompliantAlertIsPrintedInRed` fails on Windows with IntelliJ IDEA (Java 25); it is under investigation.
- The simulator is exercised by the tests; connecting it to `Main` as a live demo mode is planned for the QA phase.
- Throughput (100 readings per second) has not been measured yet; a stress test is planned for the QA phase.
- [Other known bugs or limitations]

## Next Steps

- Simulate live sensors with multi-threading (`ScheduledExecutorService`) at adjustable intervals, with a `SensorFactory` (Factory pattern) and thread-safe shared state.
- Add temperature prediction with linear regression behind a `Predictor` interface (Strategy pattern), so models can be swapped.
- Add operator actions to acknowledge and silence alerts (Command pattern), recorded in the audit log.
- Build the JavaFX or Swing desktop GUI (MVC) with colour-coded status and an audio alarm.
- Build the Spring Boot REST API and web dashboard (Tailwind CSS, Chart.js) with temperature trends, variance and compliance history.

## Team Contributions (Phase 3)

| Member          | Contributions This Phase |
|-----------------|--------------------------|
| Aakriti Kaul    | [Specific features, classes or files; tests written; reviews done] |
| Yazi Zhang      | [Specific features, classes or files; tests written; reviews done] |
| Anqi Zhou       | [Specific features, classes or files; tests written; reviews done] |
| Togzhan Usseiin | `Sensor` interface, `CsvReplaySensor`, `SpikeSensor`, `SensorType`, `SensorFactory` (Factory pattern); multi-threaded `SensorSimulator` with `ScheduledExecutorService`; thread safety for `MonitorController` and `StorageUnit`; `SensorFactoryTest` and `SensorSimulatorTest` (integration and concurrency tests); README update to the Phase 3 structure |

### Phase 2 Contributions

| Member          | Contributions |
|-----------------|---------------|
| Aakriti Kaul    | Created the GitHub repository, Maven project and package structure; integrated the Kaggle dataset; implemented the core use case: `SensorReading`, `StorageUnit`, `Alert`, `ComplianceStatus`, `MonitoringReport`, `ColdStorageDataReader`, `ComplianceChecker`, `StatisticsCalculator`, `MonitorController`, `AlertListener`, `ConsoleAlertView`, `AuditLogger`, `AuditEntry` and `Main`; wrote the 33 JUnit tests; wrote the README |
| Yazi Zhang      | Wrote `StorageUnitTest`, which verifies the running mean and variance for each storage room and checks that readings from the wrong room are rejected; added a `getStandardDeviation()` method to `StorageUnit` with tests |
| Anqi Zhou       | Wrote `ConsoleAlertViewTest`, which verifies that alerts are printed with their status colour and that the alert display limit and "... and N more alerts" message work correctly; improved the layout of the console monitoring summary |
| Togzhan Usseiin | Extended `ColdStorageDataReader` to support the `cold_storage_high_frequency.csv` dataset, with tests; added tests for CSV files that contain a byte-order mark or extra spaces around values |

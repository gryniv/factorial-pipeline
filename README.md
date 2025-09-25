# Factorial Pipeline

## README (English)

### Overview

A **console-based, multi-threaded Java program** for calculating factorials of numbers from an input file. The program ensures **ordered output**, **controlled concurrency**, and **rate-limiting**, while supporting detailed error handling and progress reporting.

---

## Task Description

**Goal:**
Read numbers from `input.txt`, compute factorials, and write results to `output.txt`.

**Requirements:**

* **Reader Thread**: reads numbers from input.
* **Writer Thread**: writes results to output in the same order.
* **Thread Pool**: executes factorial computations in parallel.
* **Rate Limiting**: caps throughput to max `ratePerSecond` factorials globally.
* **Error Handling**: invalid lines are logged into an error file or inline.

**Example Output Format:**

```
1 = 1
4 = 24
6 = 720
```

---

## Configuration

Configuration via `AppConfig` (defaults or `.properties` file):

| Property            | Description                                                              | Example      |
| ------------------- | ------------------------------------------------------------------------ | ------------ |
| `ratePerSecond`     | Maximum calculations per second across all workers.                      | `100`        |
| `maxConcurrency`    | Maximum allowed worker threads.                                          | `100`        |
| `factorialSmallMax` | Values ≤ this threshold are precomputed.                                 | `20`         |
| `inputPath`         | Path to input file (numbers per line).                                   | `input.txt`  |
| `outputPath`        | Path to output file.                                                     | `output.txt` |
| `errorsPath`        | Path to error file. If equal to `outputPath`, errors are written inline. | `errors.txt` |

---

## Error Logging Modes

### Inline Mode

If `errorsPath == outputPath`, errors appear directly inline with results:

```
5 = 120
-3
7 = 5040
```

### Separate File Mode

If `errorsPath != outputPath`, errors are logged separately:

* `output.txt`:

```
5 = 120
7 = 5040
```

* `errors.txt`:

```
Line 2: [-3] -> negative number not allowed (-3)
Line 3: [abc] -> not a valid integer (abc)
```

---

## How It Works

* **ReaderTask**: reads lines, validates, submits jobs, supports file growth (tail mode).
* **Worker Threads**: compute factorials with caching, respect global rate limiter.
* **WriterTask**: ensures order-preserving writes, buffering out-of-order results.
* **ErrorLogger**: logs to file or queue (inline mode).
* **ProgressTask**: reports statistics periodically.
* **FactorialPipeline**: coordinates all components.

**Sample Progress Output:**

```
Progress | read 1404 | submitted 1400 | completed 1300 | errors 3 | pool 100 | rate 100/s
```

---

## Usage

### Sample run (inline errors mode)

```
Enter pool size (positive integer): 100
CLI pool=100, effective pool=100, rate=100/s, inlineErrors=true
Progress | read 38346 | submitted 38273 | completed 38273 | errors 73 | pool 100 | rate 100/s | mode=INLINE_TO_OUTPUT | elapsed 00:06:23.749 (383749 ms)
```

### Metrics explained

* **read** — total lines read from input (including invalid / empty).
* **submitted** — tasks submitted to workers (valid lines only).
* **completed** — finished factorial computations.
* **errors** — invalid lines encountered (logged inline or to errors file).
* **pool** — configured worker threads actually used.
* **rate** — global throughput cap enforced by the rate limiter.
* **mode** — where errors are written: `INLINE_TO_OUTPUT` or `SEPARATE_FILE`.
* **elapsed** — total wall time since start.

---

## Notes

* Output is always order-preserving.
* Invalid inputs never block results thanks to SKIP markers.
* Factorials for small values are cached.
* Designed for extensibility.

---

## License

MIT License


import argparse
import concurrent.futures
import datetime
import json
import os
import math
import platform
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def request_json(url, method="GET", payload=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    access_token = os.environ.get("POSTAPI_ACCESS_TOKEN")
    if access_token:
        headers["Authorization"] = "Bearer " + access_token
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers=headers,
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def api_url(base_url, path):
    return base_url.rstrip("/") + path


def seed_posts(args):
    if not args.confirm_test_db:
        raise SystemExit("Refusing to seed without --confirm-test-db; point the app at a separate test database first.")
    token = "experiment6-bench-" + uuid.uuid4().hex[:12]
    url = api_url(args.base_url, "/api/posts")
    for index in range(args.count):
        content = f"{token} sample post {index:06d} for pagination benchmark"
        request_json(url, method="POST", payload={"content": content})
        if (index + 1) % 500 == 0:
            print(f"Created {index + 1}/{args.count} posts", flush=True)
    print(json.dumps({"created": args.count, "keyword": token}, indent=2))


def percentile95(values):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(0.95 * len(ordered)) - 1)]


def timed_request(url):
    started = time.perf_counter()
    try:
        response = request_json(url)
        return time.perf_counter() - started, True, response, None
    except (OSError, ValueError, urllib.error.URLError) as error:
        return time.perf_counter() - started, False, None, str(error)


def benchmark(args):
    encoded = urllib.parse.urlencode(
        {
            "keyword": args.keyword,
            "page": 0,
            "size": 10,
            "sortBy": "createdAt",
            "direction": "desc",
        }
    )
    search_url = api_url(args.base_url, "/api/posts/search?" + encoded)

    cold_seconds, cold_ok, cold_response, cold_error = timed_request(search_url)
    if not cold_ok:
        raise SystemExit(f"Cold request failed: {cold_error}")

    dataset_url = api_url(args.base_url, "/api/posts?page=0&size=1&sortBy=createdAt&direction=desc")
    dataset_response = request_json(dataset_url)
    dataset_size = dataset_response["data"]["totalElements"]

    warmup_errors = 0
    for _ in range(args.warmup):
        _, ok, _, _ = timed_request(search_url)
        warmup_errors += not ok

    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        results = list(executor.map(timed_request, [search_url] * args.requests))
    elapsed = time.perf_counter() - started

    latencies = [result[0] for result in results]
    errors = [result[3] for result in results if not result[1]]
    successful = len(latencies) - len(errors)
    report = {
        "measured_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "mode": args.mode,
        "cache_setting_required": "APP_CACHE_ENABLED=" + ("true" if args.mode == "enabled" else "false"),
        "workload": "GET /api/posts/search, page=0,size=10,sortBy=createdAt,direction=desc",
        "keyword": args.keyword,
        "dataset_size_posts": dataset_size,
        "request_count": args.requests,
        "warmup_request_count": args.warmup,
        "warmup_errors": warmup_errors,
        "concurrency": args.concurrency,
        "environment": {
            "platform": platform.platform(),
            "python": sys.version.split()[0],
            "base_url": args.base_url,
        },
        "cold_first_request_ms": round(cold_seconds * 1000, 3),
        "warm_measurement": {
            "average_latency_ms": round(statistics.fmean(latencies) * 1000, 3),
            "p95_latency_ms": round(percentile95(latencies) * 1000, 3),
            "throughput_requests_per_second": round(successful / elapsed, 3) if elapsed else 0,
            "error_rate": round(len(errors) / args.requests, 6),
            "elapsed_seconds": round(elapsed, 3),
        },
        "errors": errors[:10],
    }
    with open(args.output, "w", encoding="utf-8") as output_file:
        json.dump(report, output_file, indent=2)
        output_file.write("\n")
    print(json.dumps(report, indent=2))
    print(f"Saved measured results to {args.output}")


def positive_int(value):
    parsed = int(value)
    if parsed < 1:
        raise argparse.ArgumentTypeError("must be at least 1")
    return parsed


def non_negative_int(value):
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must be non-negative")
    return parsed


def main():
    parser = argparse.ArgumentParser(description="Seed and benchmark the Experiment 6 paginated posts API.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    seed_parser = subparsers.add_parser("seed", help="append benchmark posts without deleting existing data")
    seed_parser.add_argument("--base-url", default="http://localhost:8080")
    seed_parser.add_argument("--count", type=positive_int, default=5000)
    seed_parser.add_argument("--confirm-test-db", action="store_true")
    seed_parser.set_defaults(run=seed_posts)

    bench_parser = subparsers.add_parser("bench", help="measure the same search-page workload")
    bench_parser.add_argument("--base-url", default="http://localhost:8080")
    bench_parser.add_argument("--keyword", required=True)
    bench_parser.add_argument("--mode", choices=("enabled", "disabled"), required=True)
    bench_parser.add_argument("--requests", type=positive_int, default=1000)
    bench_parser.add_argument("--warmup", type=non_negative_int, default=50)
    bench_parser.add_argument("--concurrency", type=positive_int, default=10)
    bench_parser.add_argument("--output", default="experiment6-benchmark.json")
    bench_parser.set_defaults(run=benchmark)

    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
import { performance } from 'perf_hooks';

async function measure() {
  console.log("=== PRS Live System Performance Benchmark ===");
  console.log("Node version:", process.version, "Platform:", process.platform, process.arch);

  // 1. Authenticate
  const authStart = performance.now();
  const authRes = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin' })
  });
  const authData = await authRes.json();
  const token = authData.token;
  console.log(`Auth time: ${(performance.now() - authStart).toFixed(2)} ms`);

  // Endpoints to benchmark
  const benchmarks = [
    { name: "GET /api/dashboard", url: "http://localhost:8080/api/dashboard", method: "GET" },
    { name: "GET /api/projects/1/schedule", url: "http://localhost:8080/api/projects/1/schedule", method: "GET" },
    { name: "GET /api/projects/1/critical-path", url: "http://localhost:8080/api/projects/1/critical-path", method: "GET" },
    { name: "POST /api/projects/1/schedule/recalculate", url: "http://localhost:8080/api/projects/1/schedule/recalculate", method: "POST" },
    { name: "POST /api/projects/1/optimization/runs", url: "http://localhost:8080/api/projects/1/optimization/runs", method: "POST", body: JSON.stringify({ objective: "BALANCED" }) }
  ];

  const REPETITIONS = 10;

  for (const b of benchmarks) {
    const latencies = [];
    for (let i = 0; i < REPETITIONS; i++) {
      const start = performance.now();
      const res = await fetch(b.url, {
        method: b.method,
        headers: {
          'Authorization': 'Bearer ' + token,
          'Content-Type': 'application/json'
        },
        body: b.body
      });
      await res.json();
      latencies.push(performance.now() - start);
    }

    latencies.sort((a, b) => a - b);
    const min = latencies[0].toFixed(2);
    const max = latencies[latencies.length - 1].toFixed(2);
    const sum = latencies.reduce((acc, v) => acc + v, 0);
    const avg = (sum / latencies.length).toFixed(2);
    const p95 = latencies[Math.floor(latencies.length * 0.95)].toFixed(2);

    console.log(`[${b.name}] Reps: ${REPETITIONS} | Min: ${min} ms | Avg: ${avg} ms | P95: ${p95} ms | Max: ${max} ms`);
  }

  console.log("=== Benchmark complete ===");
}

measure().catch(console.error);

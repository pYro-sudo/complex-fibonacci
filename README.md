# Fibonacci Calculator

RESTful microservice for calculating Fibonacci numbers in the complex domain using Binet's formula implementation.

## Features

- **Complex Number Support**: Calculate Fibonacci values for complex arguments
- **High Performance**: Async I/O with Vert.x and Redis caching
- **Resilience**: Circuit breaker and rate limiting patterns
- **Dockerized**: Complete containerized deployment

## API Usage

### GET Request
```bash
curl -X GET "http://localhost:8080/fibonacci?number=3.5+2.1"
```

### POST Request
```bash
curl -X POST http://localhost:8080/fibonacci \
  -H "Content-Type: application/json" \
  -d '{"number": "3.5 2.1"}'
```

**Number formats accepted:**
- `"5"` (real number)
- `"3.5,2.1"` (comma separator)
- `"3.5+2.1i"` (complex notation)
- `"2.1i"` (imaginary number)

## Mathematical Approach

This implementation uses the generalized Binet's formula for complex numbers:

```
F(z) = [φᶻ - ψᶻ] / √5
where:
φ = (1 + √5)/2 ≈ 1.618 (golden ratio)
ψ = (1 - √5)/2 ≈ -0.618
```

For complex exponentiation, I use logarithmic form:
```
φᶻ = exp(z × ln(φ))
```

### Why This Approach?
- **Iterative methods**: Only work for integers, O(n) complexity
- **Matrix exponentiation**: Difficult to generalize for complex exponents
- **Binet's formula**: Natural extension to complex plane with O(1) computation time (but the computations themselves are algorithmically not that fancy, but this is the tradeoff)

## Deployment

```bash
docker-compose up -d
```

Services:
- **Application**: http://localhost:8080
- **Redis**: localhost:6379

## Technology Stack

- Java 17
- Vert.x 4.4 (reactive toolkit)
- Redis 7 (caching)
- Resilience4j (rate limiting)
- Apache Commons Math (complex arithmetic)

## Configuration

Environment variables:
- `REDIS_URL`: Redis connection string (default: `redis://redis:6379`)
- `APP_PORT`: HTTP server port (default: 8080)
- `JAVA_OPTS`: JVM options

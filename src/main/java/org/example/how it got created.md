## Fibonacci calculator

It basically consumes the requests to spit out the answer, in this case, the fibonacci number. 
But the case here, that we can calculate the value in the field of the complex numbers. Here is used Binet's formula(but when i derived it, i did't know that the guy has already done it some centuries ago).

## Why Binet's formula?

Just a reminder the formula is:

$$F(z) = \frac{1}{2\varphi - 1} \left( \varphi^{z} - (1-\varphi)^{z} \right)$$

well, not to be precise,

- but the iterative method sucks because of the linear time.
- recursive algorithms exponentially grow, so they suck exponentially.
- matrix algoritm is excellent, but what if to develop it in regards to the complex field? Well, we have problems here, because if we use just the formula like 
```math
\begin{pmatrix}
F_{n+1} & F_n \\
F_n & F_{n-1}
\end{pmatrix}
=
\begin{pmatrix}
1 & 1 \\
1 & 0
\end{pmatrix}^n
```
we get the problem, where we need to derive for the complex numbers. And how we are gonna do it? We can use [Baker–Campbell–Hausdorff formula](https://en.wikipedia.org/wiki/Baker%E2%80%93Campbell%E2%80%93Hausdorff_formula) , but did you see it? I mean, damn, no way i would do that to my pc. Okay, i could explain, but you need to know the Lie groups and his algebra, theory of groups, linear algebra. Okay, but here might be another method called
- diagonalization. So the way it is done might be seen [here](https://en.wikipedia.org/wiki/Fibonacci_sequence) but no way I am using that abomination. I mean, for integers it is good, but finding the spectres is costly, and it will elad to Binet's formula anyway, so why bother doing it? Also calculating the inverses might be costly and not precise due to the conditionality
- or we could expand the formula before the previous one by using the Laurent series. But that also would be costly, because we would turn to the exponents and logarithms.
But if to consider about the logarithms, it is not an injective function like in the field of the real numbers. And calculating exponents would lead to the matrix multiplication and summation of matrices, so no way
- also no way i am using Catalan's identity or [Jacobi theta function](https://en.wikipedia.org/wiki/Theta_function) because the identities are recursive and the Jacobi function requires Laurent series, and to remind, there are lots and lots of poles, that is why the summation occurs upon all the integers

## Stack used

- Java 17 (but keep an eye on the versions of the libraries)
- Redis
- Vert.x
- Apache Commons (their math3 library)

## Launch

So basically you need `Maven` and `Docker (and Docker Compose)`. 

If you have all the thingies, you just need to run the compose stuff to run. 

When you manage to run the project, in the logs of the app container will be the rules how to use the app.

Nevertheless, just use `curl` or `Postman`, for example, and write 

- `GET` request like `http://localhost:8080/fibonacci?number=3,45`

**OR**

- `POST` request to `/fibonacci` `supplied with JSON` like `{"number": "4 5"}`, so the result is similar to

  `curl -X POST http://localhost:8080/fibonacci
  -H "Content-Type: application/json"
  -d '{"number": "3.5 2.1"}'` but we can use commas as well, also keep an eye on the quoting signs

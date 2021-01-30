# Scase

Deploy scala code as a service or function with no boilerplate

When we think of implementing a microservice or a serverless function, we typically want to do something like:

```scala

// My service logic...
case m@MyRequest1 =>
  m.reply(MyResponse())
case m@MyRequest2 =>
  m.reply()
```

and then

```scala
// My client code...
myClient.sendRequest(MyRequest1("hello"))
```

We want this to be as type safe as possible, without any boilerplate, with no possibility of receiving the "wrong"
type of forgetting to send a reply entirely.

We definitely don't want to care if the service is eventually deployed as a Lambda, an Apache Pulsar function, or a
standalone app in a container, maybe run in an Akka Cluster, or perhaps run as a test locally.

We just want to be able to run it somewhere 'out there' in the cloud, or maybe run the same code locally in a test,
without having to make changes. We want to focus on the business logic and implement it on top of an elegant and concise
API, without any boilerplate.

We want to be able to access the service from anywhere in a type safe way.

We want to decouple the business logic from complicated frameworks like Akka.

We want to be able to write an immutable, idiomatic Scala model for the service API, completely decoupled from the
underlying implementation.

We want to use concurrency seamlessly and safely.

If you later change your mind about the target environment, you don't want to rewrite anything, just deploy it to the
new environment as it is and expect it to work.

**Scase** gives you exactly that, with the additional benefit of:

* Maximum type safety, with zero boilerplate
* Portable code between deployment and runtime environments, no rewriting needed
* Out-of-the-box support for deployment on a range of runtime environments, like AWS Lambda, SQS, SNS, Akka Cluster,
  Apache Pulsar or standalone app
* Simple, future proof, platform independent code for your application logic
* Straightforward integration with Cloudformation and Terraform
* Extendable support for serialization and network protocols, with built-in support for Spray Json, Circe, Java
  Serialization
* Well integrated with the Future, Cats Effect and other common Scala libraries and standard APIs
* Lightweight, extendable library that provides simple layer between runtime and application code - no "framework"
* Additional Java-friendly client API to allow easy interop with Java code
* Test support
* Well defined error handling semantics
* Purely functional, from top to bottom, but without the need to understand or directly depend on any of the complicated
  FP constructs

On top of this, **Scase** does not force you to use any "convention" in how you model your messages or correlate
requests to responses. It comes with sensible defaults and support for common styles, but all this is pluggable and easy
to customize. 

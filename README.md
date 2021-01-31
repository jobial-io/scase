# Scase

Deploy Scala code as a service or serverless function without boilerplate

When we think of implementing a microservice or a serverless function, we typically want to do something like:

```scala

// My service logic
case m@MyRequest =>
  m.reply(MyResponse())
...
```

and then

```scala
// My client code...
myClient.sendRequest(MyRequest("hello")) // : F[MyResponse]
```

We want this to be as type safe as possible, with no possibility of replying with the "wrong"
type or forgetting to send a reply entirely, and without any unnecessary boilerplate.

We definitely don't want to care if the service is eventually deployed as a Lambda, an Apache Pulsar function, or a
standalone app in a container, maybe run in an Akka Cluster, or perhaps run as a test locally.

We just want to be able to run it somewhere 'out there' in the cloud, or maybe run the same code locally in a test,
without having to make changes. Basically, we want to focus on the business logic and implement it on top of an elegant
and concise API, with no boilerplate.

In addition to that, we would like to

* be able to access the service from anywhere in a type safe way

* decouple the business logic from complicated frameworks like Akka

* be able to write an immutable, idiomatic Scala model for the service API, completely decoupled from the underlying
  implementation

* use concurrency seamlessly and safely

* still be able to access some common features of messaging and serverless runtimes if needed (e.g. message attributes).

**Scase** gives you exactly that, with the additional benefit of:

* Maximum type safety, with zero boilerplate
* Portable code between deployment and runtime environments, no rewriting needed
* Out-of-the-box support for deployment on a range of runtime environments, like AWS Lambda, SQS, SNS, Akka Cluster,
  Apache Pulsar or standalone app
* Simple, future proof, platform independent code for your application logic
* Straightforward integration with Cloudformation and Terraform
* Extendable support for serialization and network protocols, with built-in support for Spray Json, Circe, Java
  serialization
* Integrated with the Future, Cats Effect, Monix and other common Scala libraries and standard APIs
* Lightweight, modular, extendable design that provides a simple layer between runtime and application code - not a "
  framework"
* Additional Java-friendly client API to allow easy interop with Java and other JVM languages
* Test support
* Well defined error handling
* Purely functional, from top to bottom, but without the need to understand or directly depend on any of the complicated
  FP constructs

Additionally, **Scase** does not force you to use a specific "convention" when it comes to modelling your messages or
correlating request and response types. It comes with sensible defaults and support for common styles, but all of these
are pluggable and easy to customize.

## An example



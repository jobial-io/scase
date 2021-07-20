# Scase

Easily deploy Scala code as a microservice or serverless function with zero boilerplate and maximum type safety.

When we think of implementing a microservice or a serverless function, we typically want to do something like:

```scala

// My service logic
case m: MyRequest =>
  m.reply
(MyResponse(.
..) )
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
* Out-of-the-box integration with Cloudformation
* Easily extendable support for serialization and network protocols, with built-in support for Circe, Spray Json, Java
  serialization and others
* Integrated with Scala Future, Cats Effect, ZIO, Monix
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

A few things to highlight in the example:

* The service must handle every message, it is a compile time error to not reply a request
* The request must be replied using the right type, again it is checked at compile time
* The response message on the client side is type safe, for Hello it receives a HelloResponse and for Hi the response is
  HiResponse.

# Comparison with an Akka actor

An Akka actor is a low level concurrency primitive. In that sense, it serves a very different purpose to a Scase
service. However, since both Akka actors and Scase services are built around message passing and handling messages,
there are some similarities in the API which makes a comparison worthwhile:

&nbsp; | Akka Actor | Scase Service
 --- | --- | --- 
**Purpose** | Low level concurrency construct | Thin platform independent layer on any messaging middleware, runtime or protocol (including Akka actors)
**Handler** | Receive is a partial function, mainly because the actor API was untyped initially, which means it was not possible to decide if a message can be handled without implementing it as a partial function. | Handle is not a partial function: Scase aims to achieve maximum type safety, which means if a service contractually handles a type of message, it should always be able to do that. Conversely, if a service receives a type of message it cannot handle, the Scase library can tell this based on the type alone, before passing it to the service code. This design makes code generally much safer by reducing the possibility of accidentally unhandled requests / responses.
**Request-response type mapping** | Responses are mapped based on a special field in the request message in Akka Typed. In untyped actors there is no relationship between requests and responses at the type level. | The mapping is represented as a type class, which means any request to response mapping convention can be implemented easily (including Akka's). Scase provides default mappings for common patterns.
**Concurrency** | Akka actors are by design single-threaded | A Scase service is agnostic to the actual runtime that executes the message handler. Also, the effect type is pluggable, which allows easy and complete control over concurrency in the service.
**Runtime** | Akka actors run on the runtime provided by the library | Scase is just a thin layer on the runtime typically provided by the underlying messaging infrastructure: the same service can run locally or on a serverless cloud runtime. The main purpose of Scase is to provide a portable API and hide the unnecessary details.

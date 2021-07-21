# Scase

Easily run Scala code as a portable serverless function or microservice, with zero boilerplate and maximum type safety.

When we think of implementing a microservice or a serverless function, we typically want to do something like:

```scala

// Service logic
case m: MyRequest =>
  ...
  m.reply(MyResponse(...)) // computed in some effect F
```

and then on the client side:

```scala
// Client code...
myClient ? MyRequest("hello") // : F[MyResponse]
```

We want this to be as type safe as possible, with no possibility of replying with the "wrong"
type or forgetting to send a reply entirely, and we want an implementation with no boilerplate.

We usually don't care if the service is eventually deployed as a Lambda, an Apache Pulsar function, or a
standalone app in a container, or maybe run in an Akka Cluster, or in a test locally.

 We want to focus on the business logic and implement it on top of a safe
and concise API, and be able to run it in different environments without having to make changes.

In addition, we would like to:

* Be able to access the service from anywhere in a type safe way

* Decouple the business logic from frameworks like **Akka** or **AWS Lambda**

* Be able to write immutable, idiomatic Scala code for the service logic, completely decoupled from the underlying
  implementation

* Use concurrency seamlessly and safely

* Still be able to access common features of messaging and serverless runtimes if needed (e.g. message attributes).

**Scase** gives you exactly that, with the additional benefit of:

* Maximum type safety, with zero boilerplate
* Portable code between deployment and runtime environments, no rewriting needed
* Out-of-the-box support for deployment on a range of runtime environments, like **AWS Lambda**, **SQS**, **SNS**, **Akka**,
  **Apache Pulsar**, **Kafka** or standalone app
* Simple, future proof, platform independent code for your application logic
* Out-of-the-box integration with Cloudformation
* Easily extendable support for serialization and network protocols, with built-in support for Circe, Spray Json, Java
  serialization and others
* Integrated with Scala / Java Future, **Cats Effect**, **ZIO**, **Monix**
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

* The service must handle every message, it is a compile time error if a request is not replied appropriately
* The request must be replied using the right type, again, it is checked at compile time
* The response message on the client side is type safe, e.g. for Hello the client code receives a HelloResponse and for Hi the response 
  type is HiResponse
* Not possible to send a request that does not conform to the client, it is checked at compile time.  
  
## How to use

TODO: usage in sbt, maven...
 
## Integrations

### AWS Lambda

### AWS SQS

### AWS SNS

### AWS CloudFormation

### Apache Pulsar

### Kafka

### Akka

### JMS

### Local

## Messaging patterns

### Request-response

### Stream processing

### Sink service

### What about HTTP?

But wait, aren't microservices just HTTP / REST services? In many enterprise environments this is not the case or 
the preferred solution. HTTP is very constrained in comparison to general asynchronous message passing.
A good reading on this is:


Of course, nothing prevents anyone from deploying a Scase service as an HTTP endpoint. Also, many messaging
solutions implement the underlying message passing over HTTP (e.g. AWS Lambda).

## Marshalling

Marshalling / unmarshalling is done using the Marshaller and Unmarshaller type classes. Scase provides implementation
of these type classes for many popular serialization formats and libraries:

* Circe (JSON)
* Spray JSON
* Java serialization
* Raw bytes

The marshalling API is designed to be able to deal with both text and binary protocols (e.g. AWS Lambda encodes and passes
messages as text, not bytes).

## Comparison with an Akka actor

An Akka actor is a low level concurrency primitive. In that sense, it serves a very different purpose to a Scase
service. However, since both Akka actors and Scase services are built around the concept of message passing and handling messages,
there are some similarities in the API which makes a comparison worthwhile:

&nbsp; | Akka Actor | Scase Service
 --- | --- | --- 
**Purpose** | Low level concurrency construct | A platform independent serverless function that can run on any messaging middleware, runtime or protocol (including Akka actors). It does not make any assumptions about concurrency or the runtime.
**Handler** | Receive is a partial function in an Akka actor. This is mainly because the actor API was originally untyped. Because of that, it was not possible to decide if a message was handled by the actor without implementing it as a partial function. | Handle is **not** a partial function: Scase aims to achieve maximum type safety, which means if a service contractually declares to handle a certain type of message, it can be checked at compile time. Conversely, if a service receives a type of message it cannot handle, the Scase library can tell this based on the type alone, before passing it to the service code. This design makes code generally much safer by reducing the possibility of accidentally unhandled requests or responses.
**Request-response type mapping** | Responses are mapped based on a special field in the request message in typed actors. In untyped actors there is no relationship (or compile time check) between requests and responses at the type level. | Request-to-response mapping is type safe and checked at compile time. The mapping is represented as a type class, which means any mapping convention can be implemented easily (including Akka's). Scase provides default mappings for common patterns.
**Concurrency** | Akka actors are by design single-threaded, mutable constructs that automatically synchronize over the actor instance to allow safe mutations. | A Scase service is a pure Scala function that is agnostic to the actual runtime or the concurrency model used in the message handler. The effect type in the service is pluggable, which allows easy and complete control over concurrency in the service.
**Runtime** | Akka actors run on the runtime provided by the library | Scase is just a thin layer on the runtime provided by the underlying messaging infrastructure: the same service can run locally or on a serverless cloud runtime, for example. The main purpose of Scase is to provide a portable API and decouple business logic from the underlying details.

You can easily expose an existing actor as a Scase service or run a Scase service as an Actor:
...

## Performance

Scase, being a thin and lightweight layer, typically adds negligible overhead to the underlying runtime. The performance characteristics
are usually determined by the deployment platform and the pluggable effect type, as well as the application code.

## Java support

A Scase client can be seamlessly used in Java using the Java-friendly client extension.
Here is how you can create a Java client for an existing service configuration:

Of course, any other independent client can talk to a Scase service, there is nothing special about services implemented
and deployed through Scase.
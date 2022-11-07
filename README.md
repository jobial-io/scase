# Scase

[![Continuous Integration](https://github.com/jobial-io/scase/actions/workflows/ci.yml/badge.svg)](https://github.com/jobial-io/scase/actions/workflows/ci.yml)
[![Scala version support](https://index.scala-lang.org/jobial-io/scase/scase/latest-by-scala-version.svg?targetType=Js)](https://index.scala-lang.org/jobial-io/scase)
[![Codecov](https://codecov.io/gh/jobial-io/scase/branch/master/graph/badge.svg?token=WP67SLCC4P)](https://codecov.io/gh/jobial-io/scase)

**Run Scala or Java code as a portable serverless function or microservice with zero boilerplate and complete type safety**

**Integrate different Message-Oriented Middlewares and protocols easily**

**Uniform and portable messaging API for your applications**

<br/>

**Scase** is a lightweight functional library that bridges the gap between microservice platforms, messaging APIs and functional Scala code.

**Scase** helps you achieve

 * Quick implementation and deployment of serverless functions or microservices
 * Type-safe, functional implementation of common messaging patterns in enterprise applications
 * Uniform API for a wide range of messaging protocols and middelware
 * Clean separation of service API, implementation and client side
 * Portability: deployment on multiple platforms such as **AWS Lambda**, **Kafka** or **Apache Pulsar** without rewriting application code
 * Clean, purely functional internal design and API
 * Pluggable protocol support
 * High performance

## Motivation

Mircoservices and messaging are fundamental building blocks of many applications. From an application developer point of view, the use cases are often similar, yet
the underlying APIs and runtimes can be quite different. In many cases, integration and deployment requires a substantial effort and a lot of boilerplate code.
Application code usually outlives these runtimes and protocols and sometimes the same code has to run on multiple runtimes at the same time (e.g. local execution, cloud deployment, testing, migration...).
**Scase** provides a clean and simple way to address these use cases while keeping the application code portable and clean.

## Serverless functions

When thinking of implementing a microservice or a serverless function, we typically want to do something like:

```scala
// Service code:
case m: MyRequest =>
  // do processing in some effect F and then:
  m.reply(MyResponse(...))

case m: MyOtherRequest =>
  ...
```

and on the client side:

```scala
// Client code:
myClient ? MyRequest("hello") // : F[MyResponse]
```

We want the code to be **as type-safe as possible**, with no possibility of replying with the "wrong"
type or forgetting to send a response entirely.

Application developers like to focus on the business logic, implemented on top of a **safe, concise and platform independent API**.

It is usually not important if the service is eventually **deployed as an AWS Lambda**, an **Apache Pulsar function** or a **standalone app in a container, or maybe run in an Akka Cluster, or in a test locally**.

In addition, we would like to:

* Be able to access services from anywhere, in a type-safe way, using a uniform API

* Decouple the business logic from frameworks like **Akka** or **AWS Lambda**

* Be able to write idiomatic, functional Scala code for the service logic, completely decoupled from the underlying implementation

* Use concurrency seamlessly and safely

* Still be able to access common features of messaging and serverless runtimes if needed (e.g. message attributes, commit / rollback).

**Scase** makes these available through a concise, platform independent API, with the benefit of:

* Maximum type safety and no boilerplate
* Portable code between deployment and runtime environments, without rewriting
* Support for deployment on a range of runtime environments, like **AWS Lambda**, **SQS**, **SNS**, **Akka**,
  **Apache Pulsar**, **Kafka** or standalone app
* Simple, future proof, platform independent code for your application logic
* Integration with Cloudformation
* Easily extendable support for serialization and network protocols, with built-in support for Circe, Spray Json, Java serialization and others
* Integrated with effect systems like **Cats Effect**, **ZIO**, **Monix** (can also be used seamlessly with Scala / Java Futures)
* Lightweight, modular, extendable design that provides a simple layer between runtime and application code - not a "
  framework"
* Java-friendly DSL for clients and services
* Test support
* Well defined error handling
* Purely functional, from top to bottom, but without the need to understand complex FP constructs.

Additionally, **Scase** does not enforce any specific convention of modelling messages or correlating request and response types. 
It comes with sensible defaults, with pluggable support for custom styles.

**Scase** can also be used as a simple, functional alternative to native messaging APIs (e.g. for Apache Pulsar, Kafka, SQS and so on). 

## An example

A simple greeting service:

```scala
import cats.effect.IO
import io.jobial.scase.core._

trait GreetingService extends RequestHandler[IO, GreetingRequest[_ <: GreetingResponse], GreetingResponse] {

  def handleRequest(implicit context: RequestContext[IO]) = {
    case m: Hello =>
      m ! HelloResponse(s"Hello, ${m.person}!")
    case m: Hi =>
      for {
        _ <- IO(println(s"processing request $m..."))
      } yield m ! HiResponse(s"Hi ${m.person}!") 
  }
}
```

Full example can be found at
[scase-pulsar-example](https://github.com/jobial-io/scase-pulsar-example/tree/master/src/main/scala/io/jobial/scase/example/greeting/pulsar).

A few things to highlight:

* The service must handle every message, it is a compile time error if a request is not replied appropriately
* The request must be replied using the right type, again, it is checked at compile time
* The response message on the client side is type-safe; for example, for `Hello` the client code receives a `HelloResponse` and for `Hi` the response type is `HiResponse`
* It is not possible to send a request that does not conform to the client's type, this is checked at compile time.
* It is optional to use Cats Effect's `IO`: you can use any other effect type. If you are not familiar with functional effects or need to use non-pure code, you can just wrap
your code in an `IO`.

## How to use

You need to add

```scala
libraryDependencies ++= Seq(
  "io.jobial" %% "scase" % "0.9.0"
)
```

to `build.sbt` or

```xml

<dependency>
    <groupId>io.jobial</groupId>
    <artifactId>scase_${scala.version}</artifactId>
    <version>0.9.0</version>
</dependency>
```

to `pom.xml` if you use Maven, where scala.version is either 2.11, 2.12, 2.13 and 3.0 coming soon.


## Effect types

### Cats Effect

**Scase** is built around the "tagless final" pattern throughout using Cats type classes, which makes the API as well as the internal implementation
agnostic to the effect type chosen by the application developer. 
It supports the effect types provided by Cats Effect (IO) as well as ZIO out of the box. Other effect types can easily be plugged
into the library as long as they support the minimum requirements (usually Concurrent and Timer instances have to be available for the effect).

### ZIO

ZIO is supported seamlessly through ZIO cats-interop:

[Example](https://github.com/jobial-io/scase-zio-example/tree/master/src/main/scala/io/jobial/scase/example/greeting/zio)

## Integrations

### AWS Lambda

TODO: add explanation on AWS env

[Example](https://github.com/jobial-io/scase-lambda-example/tree/master/src/main/scala/io/jobial/scase/example/greeting/lambda)

### AWS SQS

[Example](https://github.com/jobial-io/scase-sqs-example/tree/master/src/main/scala/io/jobial/scase/example/greeting/sqs)

### AWS SNS
...

### AWS CloudFormation

[Example](https://github.com/jobial-io/scase-lambda-example/tree/master/src/main/scala/io/jobial/scase/example/greeting/lambda)

### Apache Pulsar

[Example](https://github.com/jobial-io/scase-pulsar-example/tree/master/src/main/scala/io/jobial/scase/example/greeting/pulsar)

### Kafka
...
### Akka
...
### JMS
...
### Local

[Example](https://github.com/jobial-io/scase-core/tree/master/src/test/scala/io/jobial/scase/local/LocalRequestResponseServiceTest.scala)

### Tibco Rendezvous
...

## Messaging patterns

### Request-response
...
### One-way
...
### Stream processing
...
### Routing
...
### Transactional transports

If the underlying transport supports transactional messaging (SQS, for example), Scase exposes this functionality through the
Consumer and Producer APIs.

### What about HTTP?

But wait, aren't microservices just HTTP / REST services? In many enterprise environments this is not the case or the preferred solution. HTTP is very constrained in comparison to general asynchronous message passing. A good reading on the topic is:

Of course, nothing prevents anyone from deploying a **Scase** service as an HTTP endpoint. Also, many messaging solutions implement the underlying message passing over HTTP (e.g. AWS Lambda, SQS, SNS).

## Java DSL

Scase provides a Java DSL to allow seamless and idiomatic usage in Java.

[Example](https://github.com/jobial-io/scase-pulsar-tibrvmsg-java-example/tree/master/src/main/java/io/jobial/scase/example/javadsl/greeting/pulsar/tibrvmsg)

Of course, any third party client can talk to a **Scase** service and vice versa, a Scase client can call a non-Scase service.

## Core concepts

### Client API

...

### Service API

...

### Marshalling

Marshalling / unmarshalling is done using the `Marshaller` and Unmarshaller type classes. **Scase** provides implementation for many popular serialization formats and libraries:

* Circe (JSON) ([Example](https://github.com/jobial-io/scase-pulsar-example/tree/master/src/main/scala/io/jobial/scase/example/greeting/pulsar))
* Java serialization
* Raw bytes
* Spray JSON ([Example](https://github.com/jobial-io/scase-spray-json-example/tree/master/src/main/scala/io/jobial/scase/example/greeting/sprayjson))
* Tibco Rendezvous ([Example](https://github.com/jobial-io/scase-pulsar-tibrvmsg-java-router-example/tree/master/src/main/scala/io/jobial/scase/example/javadsl/router/pulsar/tibrvmsg))

The marshalling API is designed to be able to deal with both text and binary protocols (e.g. AWS Lambda encodes and passes messages as text, not bytes).
Support for custom formats can be added by implementing the `Marshaller` and `Unmarshaller` type classes.

### Request-response type mapping

Mapping a request type to a response type is implemented through the `RequestResponseMapping` multi-parameter type class. For example,
if you want a service to respond with `FooResponse` to `FooRequest`, you need to have an instance of 

```scala
RequestResponseMapping[FooRequest, FooResponse]
```

available. Scase provides a default implementation of this type class for requests
that extend the `Request[RESPONSE]` trait to support the following pattern:

```scala
sealed trait GreetingRequest[RESPONSE] extends Request[RESPONSE]

sealed trait GreetingResponse

case class Hello(person: String) extends GreetingRequest[HelloResponse]

case class HelloResponse(sayingHello: String) extends GreetingResponse
```

### Lower level messaging API
...

## Comparison with Akka actors

An Akka actor is a low-level concurrency primitive. In this sense, it serves a very different purpose to a high-level service. However, since both **Akka** actors and **Scase** services are built around the concept of message passing and handling messages, there are some similarities in the APIs which make an interesting comparison:

&nbsp; | Akka Actor | Scase Service
 --- | --- | --- 
**Purpose** | Low level concurrency construct | A high level, platform independent, serverless function that can run on any messaging middleware, runtime or protocol (including Akka actors). It does not make any assumptions about concurrency or the runtime.
**When should you use?** | As an application developer, almost never. Actors are very low level, don't compose easily and do not map well to cloud runtimes. Akka actors are building blocks for higher level constructs, like Reactive Streams or Akka HTTP routes, for example. You should always consider using those high level APIs unless there is a good reason to drop down to the actor level. | When you write high level application code with serverless and microservices in mind, you should consider **Scase**: it will give you more type safety, flexibility in deployment, testing, and seamless transitioning between runtimes and cloud providers. Your code will be simpler, more portable and future proof.
**Handler** | Receive is a partial function in an Akka actor. This is mainly because the actor API was originally untyped. Because of that, it was not possible to decide if a message was actually handled by the actor without implementing it as a partial function. | Handle is **not** a partial function: **Scase** aims to achieve maximum type safety, which means if a service contractually declares to handle a certain type of message, it can be checked at compile time. Conversely, if a service receives a type of message it cannot handle, the **Scase** library can tell this fact based on the type alone, before passing it to the service code. This design makes code much safer in general by reducing the possibility of accidentally unhandled requests or responses.
**Request-response type mapping** | Responses are mapped based on a special field in the request message in typed actors, or using an OO-style JDK proxy which is not checked at compile time. In untyped actors, there is no relationship (or compile time check) between requests and responses at the type level. | Request-response mapping is type-safe and checked at compile time. The mapping is represented as a type class, which means any mapping convention can be implemented easily (including Akka's). 
**Concurrency** | Akka actors are by design single-threaded, mutable constructs. They automatically synchronize over the actor instance to allow safe mutations. | A Scase service is a pure Scala function that is agnostic to the actual runtime or the concurrency model used in the message handler. The effect type in the service is pluggable, which allows easy and complete control over concurrency in the service.
**Runtime** | Akka actors always run on the runtime provided by the library, an actor cannot be mapped to a cloud runtime. | Scase is just a thin layer on the runtime provided by the underlying messaging infrastructure: the same service can run locally or on a serverless cloud runtime, for example. The main purpose of **Scase** is to provide a portable API and decouple business logic from the underlying details. You can easily expose an existing actor as a Scase service or vice versa, run a Scase service as an Actor:
**Library** | Akka has grown from a simple library to a huge framework, with all its implications. Akka is fundamentally object-oriented. | Scase is a lightweight, modular, purely functional library, implemented on Cats and Cats Effect, with a pluggable effect type ("tagless final" style). 
...

## Performance

**Scase**, being a thin and lightweight layer, typically adds negligible overhead to the underlying runtime. The performance characteristics are mainly determined by the deployment platform and the pluggable effect type, as well as the application code.


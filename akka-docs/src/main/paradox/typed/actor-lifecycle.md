---
project.description: The Akka Actor lifecycle.
---
# Actor lifecycle

@@@ note
For the Akka Classic documentation of this feature see @ref:[Classic Actors](../actors.md).
@@@

## Dependency

To use Akka Actor Typed, you must add the following dependency in your project:

@@dependency[sbt,Maven,Gradle] {
  group=com.typesafe.akka
  artifact=akka-actor-typed_$scala.binary_version$
  version=$akka.version$
}

## Introduction

TODO intro

## Creating Actors

An actor can create, or _spawn_, an arbitrary number of child actors, which in turn can spawn children of their own, thus
forming an actor hierarchy. @apidoc[akka.actor.typed.ActorSystem] hosts the hierarchy and there can be only one _root actor_,
actor at the top of the hierarchy of the `ActorSystem`. The lifecycle of a child actor is tied to the parent -- a child
can stop itself or be stopped at any time but it can never outlive its parent.

### The ActorContext

The ActorContext can be accessed for many purposes such as:

* Spawning child actors and supervision
* Watching other actors to receive a `Terminated(otherActor)` event should the watched actor stop permanently
* Logging
* Creating message adapters
* Request-response interactions (ask) with another actor
* Access to the `self` ActorRef

If a behavior needs to use the `ActorContext`, for example to spawn child actors, or use
@scala[`context.self`]@java[`context.getSelf()`], it can be obtained by wrapping construction with `Behaviors.setup`:

Scala
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/scala/docs/akka/typed/IntroSpec.scala) { #hello-world-main }

Java
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/java/jdocs/akka/typed/IntroTest.java) { #hello-world-main-setup }

#### ActorContext Thread Safety

Many of the methods in `ActorContext` are not thread-safe and

* Must not be accessed by threads from @scala[`scala.concurrent.Future`]@java[`java.util.concurrent.CompletionStage`] callbacks
* Must not be shared between several actor instances
* Must only be used in the ordinary actor message processing thread

### The Guardian Actor

The top level actor, also called the guardian actor, is created along with the `ActorSystem`. Messages sent to the actor
system are directed to the root actor. The root actor is defined by the behavior used to create the `ActorSystem`,
named `HelloWorldMain` in the example below:

Scala
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/scala/docs/akka/typed/IntroSpec.scala) { #hello-world }

Java
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/java/jdocs/akka/typed/IntroTest.java) { #hello-world }

For very simple applications the guardian may contain the actual application logic and handle messages. As soon as the application
handles more than one concern the the guardian should instead just bootstrap the application, spawn the various subsystems as 
children and monitor their lifecycles.

When the guardian actor stops this will stop the `ActorSystem`.  

@@@ Note

In the classic counter part, the @apidoc[akka.actor.ActorSystem], the root actor was provided out of the box and you
could spawn top-level actors from the outside of the `ActorSystem` using `actorOf`. @ref:[SpawnProtocol](#spawnprotocol)
is a tool that mimics the old style of starting up actors.

@@@


### Spawning Children

Child actors are created and started with @apidoc[typed.*.ActorContext]'s `spawn`.
In the example below, when the root actor
is started, it spawns a child actor described by the `HelloWorld` behavior. Additionally, when the root actor receives a
`Start` message, it creates a child actor defined by the behavior `HelloWorldBot`:

Scala
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/scala/docs/akka/typed/IntroSpec.scala) { #hello-world-main }

Java
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/java/jdocs/akka/typed/IntroTest.java) { #hello-world-main }

To specify a dispatcher when spawning an actor use @apidoc[DispatcherSelector]. If not specified, the actor will
use the default dispatcher, see @ref:[Default dispatcher](dispatchers.md#default-dispatcher) for details.

Scala
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/scala/docs/akka/typed/IntroSpec.scala) { #hello-world-main-with-dispatchers }

Java
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/java/jdocs/akka/typed/IntroTest.java) { #hello-world-main-with-dispatchers }

Refer to @ref:[Actors](actors.md#first-example) for a walk-through of the above examples.

### SpawnProtocol

The guardian actor should be responsible for initialization of tasks and create the initial actors of the application,
but sometimes you might want to spawn new actors from the outside of the guardian actor. For example creating one actor
per HTTP request.

That is not difficult to implement in your behavior, but since this is a common pattern there is a predefined
message protocol and implementation of a behavior for this. It can be used as the guardian actor of the `ActorSystem`,
possibly combined with `Behaviors.setup` to start some initial tasks or actors. Child actors can then be started from
the outside by telling or asking `SpawnProtocol.Spawn` to the actor reference of the system. When using `ask` this is
similar to how `ActorSystem.actorOf` can be used in classic actors with the difference that a
@scala[`Future`]@java[`CompletionStage`] of the `ActorRef` is returned.

The guardian behavior can be defined as:

Scala
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/scala/docs/akka/typed/SpawnProtocolDocSpec.scala) { #imports1 #main }

Java
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/java/jdocs/akka/typed/SpawnProtocolDocTest.java) { #imports1 #main }

and the `ActorSystem` can be created with that `main` behavior and asked to spawn other actors:

Scala
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/scala/docs/akka/typed/SpawnProtocolDocSpec.scala) { #imports2 #system-spawn }

Java
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/java/jdocs/akka/typed/SpawnProtocolDocTest.java) { #imports2 #system-spawn }

The `SpawnProtocol` can also be used at other places in the actor hierarchy. It doesn't have to be the root
guardian actor.

A way to find running actors is described in @ref:[Actor discovery](actor-discovery.md).

## Stopping Actors

An actor can stop itself by returning `Behaviors.stopped` as the next behavior.

Child actors can be forced to be stopped after it finishes processing its current message by using the
`stop` method of the `ActorContext` from the parent actor. Only child actors can be stopped in that way.

The child actors will be stopped as part of the shutdown procedure of the parent.

The `PostStop` signal that results from stopping an actor can be used for cleaning up resources. Note that
a behavior that handles such `PostStop` signal can optionally be defined as a parameter to `Behaviors.stopped`
if different actions is needed when the actor gracefully stops itself from when it is stopped abruptly.

Here is an illustrating example:

Scala
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/scala/docs/akka/typed/GracefulStopDocSpec.scala) {
    #imports
    #master-actor
    #worker-actor
    #graceful-shutdown
  }

Java
:  @@snip [IntroSpec.scala](/akka-actor-typed-tests/src/test/java/jdocs/akka/typed/GracefulStopDocTest.java)  {
   #imports
   #master-actor
   #worker-actor
   #graceful-shutdown
 }

/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.akka.typed;

// #imports

import java.util.concurrent.TimeUnit;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

// #imports

interface GracefulStopDocTest {

  // #master-actor

  public class MasterControlProgram extends AbstractBehavior<MasterControlProgram.Command> {

    interface Command {}

    public static final class SpawnJob implements Command {
      public final String name;

      public SpawnJob(String name) {
        this.name = name;
      }
    }

    public enum GracefulShutdown implements Command {
      INSTANCE
    }

    public static Behavior<Command> create() {
      return Behaviors.setup(MasterControlProgram::new);
    }

    public MasterControlProgram(ActorContext<Command> context) {
      super(context);
    }

    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
          .onMessage(SpawnJob.class, this::onSpawnJob)
          .onMessage(GracefulShutdown.class, message -> onGracefulShutdown())
          .onSignal(PostStop.class, signal -> onPostStop())
          .build();
    }

    private Behavior<Command> onSpawnJob(SpawnJob message) {
      getContext().getSystem().log().info("Spawning job {}!", message.name);
      getContext().spawn(Job.create(message.name), message.name);
      return this;
    }

    private Behavior<Command> onGracefulShutdown() {
      getContext().getSystem().log().info("Initiating graceful shutdown...");

      // perform graceful stop, executing cleanup before final system termination
      // behavior executing cleanup is passed as a parameter to Actor.stopped
      return Behaviors.stopped(() -> getContext().getSystem().log().info("Cleanup!"));
    }

    private Behavior<Command> onPostStop() {
      getContext().getSystem().log().info("Master Control Program stopped");
      return this;
    }
  }
  // #master-actor

  public static void main(String[] args) throws Exception {
    // #graceful-shutdown

    final ActorSystem<MasterControlProgram.Command> system =
        ActorSystem.create(MasterControlProgram.create(), "B6700");

    system.tell(new MasterControlProgram.SpawnJob("a"));
    system.tell(new MasterControlProgram.SpawnJob("b"));

    // sleep here to allow time for the new actors to be started
    Thread.sleep(100);

    system.tell(MasterControlProgram.GracefulShutdown.INSTANCE);

    system.getWhenTerminated().toCompletableFuture().get(3, TimeUnit.SECONDS);
    // #graceful-shutdown
  }

  // #worker-actor

  public class Job extends AbstractBehavior<Job.Command> {

    interface Command {}

    public static Behavior<Command> create(String name) {
      return Behaviors.setup(context -> new Job(context, name));
    }

    private final String name;

    public Job(ActorContext<Command> context, String name) {
      super(context);
      this.name = name;
    }

    @Override
    public Receive<Job.Command> createReceive() {
      return newReceiveBuilder().onSignal(PostStop.class, postStop -> onPostStop()).build();
    }

    private Behavior<Command> onPostStop() {
      getContext().getSystem().log().info("Worker {} stopped", name);
      return this;
    }
  }
  // #worker-actor
}

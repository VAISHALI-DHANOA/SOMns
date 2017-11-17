package tools.concurrency;

import java.util.ArrayList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import som.VM;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import som.vm.Activity;
import som.vm.VmSettings;
import tools.TraceData;
import tools.debugger.SteppingStrategy;
import tools.debugger.entities.EntityType;
import tools.debugger.entities.SteppingType;

public abstract class TracingActivityThread extends ForkJoinWorkerThread {

  public static AtomicInteger           threadIdGen    = new AtomicInteger(1);
  protected final long                  threadId;
  protected long                        nextActivityId = 1;
  protected long                        nextMessageId;
  protected long                        nextPromiseId;
  protected long                        nextEntityId;
  // Used for tracing, accessed by the ExecAllMessages classes
  public long                           createdMessages;
  public long                           resolvedPromises;
  public long                           erroredPromises;

  protected final TraceBuffer           traceBuffer;

  protected SteppingStrategy            steppingStrategy;

  protected ConcurrentEntityScope       topEntity;

  // Work Steal Parameters
  public BlockingDeque<SomForkJoinTask> taskQueue = new LinkedBlockingDeque<SomForkJoinTask>();

  public int workStealingTries = 0;
  public final SimpleRandom backoffRnd = new SimpleRandom();

  public static class SimpleRandom {
    private int seed = 74755;

    public int next() {
      seed = ((seed * 1309) + 13849) & 65535;
      return seed;
    }

    public int next(final int bound) {
      return next() % bound;
    }
  }

  private static class ConcurrentEntityScope {

    private final EntityType            type;
    private final ConcurrentEntityScope next;

    ConcurrentEntityScope(final EntityType type,
        final ConcurrentEntityScope next) {
      this.type = type;
      this.next = next;
    }
  }

  public TracingActivityThread(final ForkJoinPool pool) {
    super(pool);
    if (VmSettings.ACTOR_TRACING) {
      traceBuffer = TraceBuffer.create();
      threadId = threadIdGen.getAndIncrement();
      nextActivityId = 1 + (threadId << TraceData.ACTIVITY_ID_BITS);
      nextMessageId = (threadId << TraceData.ACTIVITY_ID_BITS);
      nextPromiseId = (threadId << TraceData.ACTIVITY_ID_BITS);
    } else {
      threadId = threadIdGen.getAndIncrement();
      traceBuffer = null;
    }
    setName(getClass().getSimpleName() + "-" + threadId);

    if(!VmSettings.ENABLE_ORG)
    {
      synchronized (VM.threads) {
        VM.threads[VM.numWSThreads] = this;
        VM.numWSThreads += 1;
      }
    }
  }

  public abstract Activity getActivity();

  public final boolean isStepping(final SteppingType type) {
    if (steppingStrategy == null) {
      return false;
    }
    return steppingStrategy.is(type);
  }

  public final void setSteppingStrategy(final SteppingType type) {
    this.steppingStrategy = new SteppingStrategy(type);
  }

  public void enterConcurrentScope(final EntityType type) {
    topEntity = new ConcurrentEntityScope(type, topEntity);
  }

  public void leaveConcurrentScope(final EntityType type) {
    assert topEntity.type == type;
    topEntity = topEntity.next;
  }

  public EntityType[] getConcurrentEntityScopes() {
    if (topEntity == null) {
      return null;
    }

    ArrayList<EntityType> list = new ArrayList<>();

    ConcurrentEntityScope current = topEntity;

    while (current != null) {
      list.add(current.type);
      current = current.next;
    }
    return list.toArray(new EntityType[0]);
  }

  public long generateActivityId() {
    long result = nextActivityId;
    nextActivityId++;
    assert TraceData.isWithinJSIntValueRange(result);
    assert result != -1 : "-1 is not a valid activity id";
    return result;
  }

  public long generatePromiseId() {
    return nextPromiseId++;
  }

  public final TraceBuffer getBuffer() {
    return traceBuffer;
  }

  public abstract long getCurrentMessageId();

  @Override
  protected void onStart() {
    super.onStart();
    if (VmSettings.ACTOR_TRACING) {
      traceBuffer.init(ActorExecutionTrace.getEmptyBuffer(), threadId);
      ActorExecutionTrace.registerThread(this);
    }
    setName(getClass().getSimpleName() + "-" + threadId);
  }

  @Override
  protected void onTermination(final Throwable exception) {
    if (VmSettings.ACTOR_TRACING) {
      traceBuffer.returnBuffer();
      ActorExecutionTrace.unregisterThread(this);
    }
    super.onTermination(exception);
  }

  public static TracingActivityThread currentThread() {
    return (TracingActivityThread) Thread.currentThread();
  }

  private long generateEntityId() {
    long result = nextEntityId;
    nextEntityId++;
    assert TraceData.isWithinJSIntValueRange(result);
    assert result != -1 : "-1 is not a valid entity id";
    return result;
}

  public static long newEntityId() {
    if (VmSettings.ACTOR_TRACING && Thread.currentThread() instanceof TracingActivityThread) {
      TracingActivityThread t = TracingActivityThread.currentThread();
      return t.generateEntityId();
    } else {
      return 0; // main actor
    }
}
}
//http://www.programcreek.com/java-api-examples/index.php?api=com.oracle.truffle.api.Truffle
//http://cesquivias.github.io/blog/2014/12/02/writing-a-language-in-truffle-part-2-using-truffle-and-graal/
package tools.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.VM;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.primitives.threading.TaskThreads.SomForkJoinTask;

public class WorkStealingWorker implements Runnable {

  public WSWork         wsTask;
  private int           ID;
  public static boolean taskExecuted = false;

  public WorkStealingWorker(final WSWork task, final int id) {
    this.wsTask = task;
    this.ID = id;
  }

  @Override
  public void run() {
    Thread currentThread = Thread.currentThread();
    while (true) {
      tryStealingAndExecuting("WS", currentThread);
    }
  }

  @TruffleBoundary //Boundary added because method not yet optimized
  public static void tryStealingAndExecuting(final String x, final Thread currentThread) {
    List<Thread> copy = new ArrayList<Thread>(VM.threads);


    for (Thread victim : copy) {
      if (!victim.equals(currentThread)) {
        SomForkJoinTask sf = stealTask(victim);

        if (sf != null && !sf.stolen) {
          // System.out.print(x);

          assert(sf.result == null);

          sf.stolen = true;

          sf.result = sf.block.getMethod().invoke(sf.evaluateArgsForSpawn);

          //System.out.println(x + " Puts result: " + sf.result + " " + currentThread.getName());
        }
      }
    }
  }


  @TruffleBoundary //Boundary added because method not yet optimized
  public static void tryStealingAndExecuting(final String x, final Thread currentThread, final BlockDispatchNode dispatch) {
    List<Thread> copy = new ArrayList<Thread>(VM.threads);


    for (Thread victim : copy) {
      if (!victim.equals(currentThread)) {
        SomForkJoinTask sf = stealTask(victim);

        if (sf != null && !sf.stolen) {
          // System.out.print(x);

          assert(sf.result == null);

          sf.stolen = true;

          sf.result = dispatch.executeDispatch(sf.evaluateArgsForSpawn);
              //sf.block.getMethod().invoke(sf.evaluateArgsForSpawn);

          //System.out.println(x + " Puts result: " + sf.result + " " + currentThread.getName());
        }
      }
    }
  }

  @TruffleBoundary
  private static SomForkJoinTask stealTask(final Thread victim) {
    return ((TracingActivityThread)victim).taskQueue.poll();
  }

  public int getID() {
    return ID;
  }

  public void setID(final int iD) {
    this.ID = iD;
  }

  public static class Join extends RuntimeException {

    private static final long serialVersionUID = 1L;
  }

  public static class Finish extends RuntimeException {

    private static final long serialVersionUID = 1L;
  }

  public static class WSWork {

    private ForkJoinPool       fjPool;
    private int                ID;
    private WorkStealingWorker wsworker;

    public WSWork(final ForkJoinPool fj, final int ID) {
      this.fjPool = fj;
      this.ID = ID;
      this.wsworker = createWorkers();
    }

    public WorkStealingWorker createWorkers() {
      return new WorkStealingWorker(this, this.ID);
    }

    public void execute() {
      this.fjPool.execute(wsworker);
    }
  }
}

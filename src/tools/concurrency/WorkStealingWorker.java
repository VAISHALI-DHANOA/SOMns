//http://www.programcreek.com/java-api-examples/index.php?api=com.oracle.truffle.api.Truffle
//http://cesquivias.github.io/blog/2014/12/02/writing-a-language-in-truffle-part-2-using-truffle-and-graal/
package tools.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import som.VM;
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

    while (true) {
      computeResult("WS");
    }
  }

  public static void computeResult(final String x) {

    List<Thread> copy = new ArrayList<Thread>(VM.threads);

    for (Thread victim : copy) {

      if(!victim.equals(Thread.currentThread()))
      {
        SomForkJoinTask sf = ((TracingActivityThread)victim).taskQueue.poll();

        if (sf != null && !sf.stolen) {

          assert(sf.result == null);

          sf.stolen = true;

          sf.result = sf.node.executeGeneric(sf.frame);

          System.out.println(x + " Puts result: " + sf.result + " " + Thread.currentThread().getName());
        }
      }
    }
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

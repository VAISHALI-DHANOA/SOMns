package som.interpreter.nodes;

import java.util.concurrent.ThreadLocalRandom;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import tools.concurrency.WorkStealingWorker;

public class OptJoinNode extends ExprWithTagsNode {

  @Child
  ExpressionNode receiver;

  private int    reTries     = 0;
  private long   waitTime;
  private long   maxWaitTime = 1500L;
  private int    maxReTries  = 10;

  public OptJoinNode(final SourceSection source,
      final ExpressionNode receiver) {
    super(source);
    this.receiver = receiver;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {

    SomForkJoinTask task = (SomForkJoinTask) receiver.executeGeneric(frame);

    try {

      while (task.result == null) {

        System.out.println("..");

        if (reTries < maxReTries) {
          waitTime = Math.min(maxWaitTime, getWaitTime(reTries));
        } else {
          waitTime = 500;
        }
        reTries += 1;
        Thread.sleep(waitTime);

        if (task.result == null) {
          WorkStealingWorker.computeResult("Join");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return task.result;
  }

  private long getWaitTime(final int retryCount) {

    long randomNum = ThreadLocalRandom.current().nextInt(0, 1000 + 1);
    long waitTime = ((long) Math.pow(2, retryCount) * randomNum);
    return waitTime;
  }

}

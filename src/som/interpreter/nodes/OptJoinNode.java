package som.interpreter.nodes;

import java.util.concurrent.ThreadLocalRandom;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import tools.concurrency.WorkStealingWorker;

public class OptJoinNode extends ExprWithTagsNode {

  @Child
  private ExpressionNode receiver;
  @Child
  private BlockDispatchNode dispatch;

  private int    reTries     = 0;
  private long   waitTime;
  private long   maxWaitTime = 1500L;
  private int    maxReTries  = 10;

  public OptJoinNode(final SourceSection source,
      final ExpressionNode receiver) {
    super(source);
    this.receiver = receiver;
    this.dispatch = BlockDispatchNodeGen.create();
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {

    SomForkJoinTask task = (SomForkJoinTask) receiver.executeGeneric(frame);
    Thread currentThread = Thread.currentThread();

    try {

      while (task.result == null) {

        backOffBeforeStealing();

        if (task.result == null) {
          WorkStealingWorker.tryStealingAndExecuting("Join", currentThread, dispatch);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return task.result;
  }

  @TruffleBoundary
  private void backOffBeforeStealing() throws InterruptedException {
    if (reTries < maxReTries) {
      waitTime = Math.min(maxWaitTime, getWaitTime(reTries));
    } else {
      waitTime = 500;
    }
    reTries += 1;
    Thread.sleep(waitTime);
  }

  private long getWaitTime(final int retryCount) {

    long randomNum = ThreadLocalRandom.current().nextInt(0, 1000 + 1);
    long waitTime = ((long) Math.pow(2, retryCount) * randomNum);
    return waitTime;
  }

}

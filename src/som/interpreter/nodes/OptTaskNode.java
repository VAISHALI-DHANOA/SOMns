package som.interpreter.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import tools.concurrency.TracingActivityThread;

public final class OptTaskNode extends ExprWithTagsNode {

  @Child
  ExpressionNode block;

  public OptTaskNode(final SourceSection source, final ExpressionNode block) {
    super(source);
    this.block = block;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {

    SomForkJoinTask somTask = new SomForkJoinTask(block, frame);
    //somTask.result = block.executeGeneric(frame);

     try {
      TracingActivityThread tracingThread = TracingActivityThread.currentThread();

      if (isSystemLikelyIdle(tracingThread)) {
        offerTaskForStealing(somTask, tracingThread);
        System.out.println("Puts Work " + tracingThread.getName());
      } else {
        somTask.result = block.executeGeneric(frame);
      }
    } catch (InterruptedException e) {
      System.out.println("Exception in Opt task node: " + e);
    }
    return somTask;
  }

  @TruffleBoundary
  private void offerTaskForStealing(final SomForkJoinTask somTask,
      final TracingActivityThread tracingThread) throws InterruptedException {
    tracingThread.taskQueue.put(somTask);
  }

  @TruffleBoundary
  private boolean isSystemLikelyIdle(final TracingActivityThread tracingThread) {
    return tracingThread.taskQueue.isEmpty();
  }
}

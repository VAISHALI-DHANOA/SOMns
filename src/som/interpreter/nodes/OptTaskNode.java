package som.interpreter.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import som.vmobjects.SBlock;
import tools.concurrency.TracingActivityThread;

public final class OptTaskNode extends ExprWithTagsNode {

  @Child
  ExpressionNode valueSend;
  @Child
  ExpressionNode block;
  ExpressionNode argArray[];

  public OptTaskNode(final SourceSection source, final ExpressionNode valueSend,
      final ExpressionNode block, final ExpressionNode argArray[]) {
    super(source);
    this.valueSend = valueSend;
    this.block = block;
    this.argArray = argArray;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {

    SomForkJoinTask somTask;
    Object[] args = executeArgs(frame);

    assert args[0] instanceof SBlock;

    try {
      TracingActivityThread tracingThread = TracingActivityThread.currentThread();

      if (isSystemLikelyIdle(tracingThread)) {
        somTask = new SomForkJoinTask(args);
        offerTaskForStealing(somTask, tracingThread);
        System.out.println("Puts Work " + tracingThread.getName());
      }
      else {
        somTask = new SomForkJoinTask(null);
        somTask.result = ((PreevaluatedExpression) valueSend).doPreEvaluated(frame, args);
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    return somTask;
  }

  @ExplodeLoop
  private Object[] executeArgs(final VirtualFrame frame) {
    Object executedArgArray[] = new Object[this.argArray.length + 1];
    int i = 1;

    executedArgArray[0] = block.executeGeneric(frame);

    for (ExpressionNode e : this.argArray) {
      executedArgArray[i] = e.executeGeneric(frame);
      i++;
    }
    return executedArgArray;
  }

  @TruffleBoundary
  private void offerTaskForStealing(final SomForkJoinTask somTask,
      final TracingActivityThread tracingThread) throws InterruptedException {
    tracingThread.taskQueue.put(somTask);
  }

  @TruffleBoundary
  private boolean isSystemLikelyIdle(
      final TracingActivityThread tracingThread) {
    return tracingThread.taskQueue.isEmpty();
  }
}

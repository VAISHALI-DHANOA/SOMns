package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.WorkStealingWorker;

public class OptJoinNode extends ExprWithTagsNode {

  @Child private ExpressionNode receiver;
  @Child private BlockDispatchNode dispatch;

  public OptJoinNode(final SourceSection source,
      final ExpressionNode receiver) {
    super(source);
    this.receiver = receiver;
    this.dispatch = BlockDispatchNodeGen.create();
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {

    SomForkJoinTask task = (SomForkJoinTask) receiver.executeGeneric(frame);
    TracingActivityThread currentThread = TracingActivityThread.currentThread();

    while (task.result == null) {
      boolean stolenTask = WorkStealingWorker.tryStealingAndExecuting(currentThread, dispatch);
      //WorkStealingWorker.doBackoffIfNecessary(currentThread, stolenTask);
    }
    return task.result;
  }
}
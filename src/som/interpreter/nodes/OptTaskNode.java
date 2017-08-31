package som.interpreter.nodes;

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

      if(tracingThread.taskQueue.isEmpty())
      {
        TracingActivityThread.currentThread().taskQueue.put(somTask);
        System.out.println("Puts Work " + Thread.currentThread().getName());
      }
      else
      {
        somTask.result = block.executeGeneric(frame);
      }

    } catch (InterruptedException e) {

      System.out.println("Exception in Opt task node: " + e);
    }
    return somTask;
  }
}

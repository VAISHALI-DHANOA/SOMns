package som.interpreter;

import java.util.ArrayList;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;

import som.VM;
import som.compiler.Variable.Local;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode;
import som.interpreter.nodes.MessageSendNode.AbstractUninitializedMessageSendNode;
import som.interpreter.nodes.NonLocalVariableNode;
import som.interpreter.nodes.OptJoinNode;
import som.interpreter.nodes.OptTaskNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerTernaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerUnaryPrimitiveNode;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;
import som.primitives.BlockPrimsFactory.ValueTwoPrimFactory;

public final class PostParsedVisitor implements NodeVisitor {

  InliningVisitor                 inline;
  public final int                contextLevel;
  protected final MethodScope     scope;
  private final VM                vm;
  private static ArrayList<Local> wvar = new ArrayList<Local>();

  public static ExpressionNode doInline(final ExpressionNode body,
      final MethodScope inlinedCurrentScope, final int appliesTo, final VM vm) {

    return NodeVisitorUtil.applyVisitor(body,
        new PostParsedVisitor(inlinedCurrentScope, appliesTo, vm));
  }

  private PostParsedVisitor(final MethodScope scope, final int appliesTo,
      final VM vm) {
    this.scope = scope;
    this.contextLevel = appliesTo;
    this.vm = vm;
  }

  @Override
  public boolean visit(final Node node) {

    if (node instanceof EagerTernaryPrimitiveNode) {
      EagerTernaryPrimitiveNode msgSend = (EagerTernaryPrimitiveNode) node;

      if (msgSend.getSelector().getString().equals("spawn:with:")) {

        ExpressionNode[] exp = msgSend.getArguments();
        ExpressionNode[] real = new ExpressionNode[3];

        if (exp[1] instanceof AbstractUninitializedMessageSendNode) {

          AbstractUninitializedMessageSendNode o = (AbstractUninitializedMessageSendNode) exp[1];
          real = o.getArguments();

        }

        Node n = exp[0];

        while (n.getParent() != null) {

          n = n.getParent();

          if (n instanceof NonLocalVariableNode) {

            wvar.add(((NonLocalVariableNode) n).getVar());

          } else if (n instanceof LocalVariableNode) {

            wvar.add(((LocalVariableNode) n).getVar());
            break;

          }
        }

        Node replacement = new OptTaskNode(node.getSourceSection(),
            ValueTwoPrimFactory.create(false, node.getSourceSection(), exp[0],
                real[1], real[2]));
        node.replace(replacement);
        System.out.println("Spawn Node: " + replacement);
      }
    }

    if (node instanceof EagerBinaryPrimitiveNode) {
      EagerBinaryPrimitiveNode msgSend = (EagerBinaryPrimitiveNode) node;

      if (msgSend.getSelector().getString().equals("spawn:")) {

        ExpressionNode[] exp = msgSend.getArguments();
        Node replacement = new OptTaskNode(node.getSourceSection(),
            ValueNonePrimFactory.create(false, node.getSourceSection(),
                exp[0]));
        Node n = exp[0];

        while (n.getParent() != null) {

          n = n.getParent();

          if (n instanceof NonLocalVariableNode) {

            wvar.add(((NonLocalVariableNode) n).getVar());

          } else if (n instanceof LocalVariableNode) {

            wvar.add(((LocalVariableNode) n).getVar());
            break;

          }
        }

        node.replace(replacement);
        System.out.println("Spawn Node: " + replacement);
      }
    }

    else if (node instanceof EagerUnaryPrimitiveNode) {

      EagerUnaryPrimitiveNode joinNode = (EagerUnaryPrimitiveNode) node;

      if (joinNode.getSelector().getString().equals("join")) {

        ExpressionNode exp = joinNode.getArgument();

        Node replacement = exp;

        if (exp instanceof LocalVariableNode) {

          if (wvar.contains(((LocalVariableNode) exp).getVar())) {
            replacement = new OptJoinNode(node.getSourceSection(), exp);
          }
        }

        if (exp instanceof NonLocalVariableNode) {

          if (wvar.contains(((NonLocalVariableNode) exp).getVar())) {

            replacement = new OptJoinNode(node.getSourceSection(), exp);
          }
        }

        node.replace(replacement);
        System.out.println("Join Node: " + replacement);
      }
    }
    return true;
  }

  /*
   * @Override public boolean visit(final Node node) {
   *
   * if (node instanceof EagerBinaryPrimitiveNode) { EagerBinaryPrimitiveNode
   * msgSend = (EagerBinaryPrimitiveNode) node;
   *
   * if (msgSend.getSelector().getString().equals("spawn:")) {
   *
   * ExpressionNode[]exp = msgSend.getArguments(); Node replacement
   * =ValueNonePrimFactory.create(false, node.getSourceSection(), exp[0]);
   * node.replace(replacement); } }
   *
   * else if (node instanceof EagerUnaryPrimitiveNode) {
   *
   * EagerUnaryPrimitiveNode joinNode = (EagerUnaryPrimitiveNode) node;
   *
   * if (joinNode.getSelector().getString().equals("join")) {
   *
   * ExpressionNode exp = joinNode.getArgument(); node.replace(exp); } }
   *
   * return true; }
   */

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}

/*
 * public boolean visit(final Node node) {
 *
 * if (node instanceof EagerTernaryPrimitiveNode) { EagerTernaryPrimitiveNode
 * msgSend = (EagerTernaryPrimitiveNode) node;
 *
 * if (msgSend.getSelector().getString().equals("spawn:with:")) {
 *
 * ExpressionNode[] exp = msgSend.getArguments(); ExpressionNode[] real = new
 * ExpressionNode[3];
 *
 * if (exp[1] instanceof AbstractUninitializedMessageSendNode) {
 *
 * AbstractUninitializedMessageSendNode o =
 * (AbstractUninitializedMessageSendNode) exp[1]; real = o.getArguments(); }
 *
 * Node n = exp[0];
 *
 * while (n.getParent() != null) { n = n.getParent();
 *
 * if (n instanceof NonLocalVariableNode) { wvar = ((NonLocalVariableNode)
 * n).getVar(); } else if (n instanceof LocalVariableNode) { wvar =
 * ((LocalVariableNode) n).getVar(); break; } }
 *
 * Node replacement = new
 * OptTaskNode(node.getSourceSection(),ValueTwoPrimFactory.create(false,node.
 * getSourceSection(),exp[0],real[1],real[2])); //Node replacement =
 * ValueTwoPrimFactory.create(false,node.getSourceSection(),exp[0],real[1],real[
 * 2]); node.replace(replacement); } }
 *
 * if (node instanceof EagerBinaryPrimitiveNode) { EagerBinaryPrimitiveNode
 * msgSend = (EagerBinaryPrimitiveNode) node;
 *
 * if (msgSend.getSelector().getString().equals("spawn:")) {
 *
 * ExpressionNode[] exp = msgSend.getArguments(); Node replacement = null;
 *
 * if (isReplaced || !convertToOptTask) { replacement =
 * ValueNonePrimFactory.create(false, node.getSourceSection(), exp[0]); }
 *
 * if (!isReplaced && convertToOptTask) {
 *
 * replacement = new OptTaskNode(node.getSourceSection(),
 * ValueNonePrimFactory.create(false, node.getSourceSection(), exp[0]));
 *
 * convertToOptTask = false; isReplaced = true;
 *
 * Node n = exp[0];
 *
 * while (n.getParent() != null) { n = n.getParent();
 *
 * if (n instanceof NonLocalVariableNode) { wvar = ((NonLocalVariableNode)
 * n).getVar(); } else if (n instanceof LocalVariableNode) { wvar =
 * ((LocalVariableNode) n).getVar(); break; } } }
 *
 * node.replace(replacement); System.out.println("Spawn Node: " + replacement);
 * } }
 *
 * else if (node instanceof EagerUnaryPrimitiveNode) {
 *
 * EagerUnaryPrimitiveNode joinNode = (EagerUnaryPrimitiveNode) node;
 *
 * if (joinNode.getSelector().getString().equals("join")) {
 *
 * ExpressionNode exp = joinNode.getArgument();
 *
 * Node replacement = exp;
 *
 * if (exp instanceof LocalVariableNode) {
 *
 * if (wvar.equals(((LocalVariableNode) exp).getVar())) { replacement = new
 * OptJoinNode(node.getSourceSection(), exp); } }
 *
 * if (exp instanceof NonLocalVariableNode) {
 *
 * if (wvar.equals(((NonLocalVariableNode) exp).getVar())) {
 *
 * replacement = new OptJoinNode(node.getSourceSection(), exp); } }
 *
 * node.replace(replacement); System.out.println("Join Node: " + replacement); }
 * } return true; }
 */

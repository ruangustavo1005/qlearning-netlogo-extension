package primitives

import model.{Agent, Session}
import org.nlogo.api.OutputDestination.Normal
import org.nlogo.api._
import org.nlogo.core.Syntax
import org.nlogo.core.Syntax.NumberType
import org.nlogo.api.ScalaConversions._



class Learning extends Command {
  override def getSyntax: Syntax = Syntax.commandSyntax()

  override def perform(args: Array[Argument], context: Context): Unit = {
    val optAgent : Option[Agent] = Session.instance().getAgent(context.getAgent)
    if(optAgent.isEmpty) {
      throw new ExtensionException("Agent " + context.getAgent.id + " isn't a learner agent")
    } else {
      val agent : Agent = optAgent.get

      if(agent.actions.isEmpty)
        throw new ExtensionException("No action has been defined for agent " + context.getAgent.id)

      if(agent.learningRate == -1)
        throw new ExtensionException("No learning rate has been defined for agent " + context.getAgent.id)

      if(agent.discountFactor == -1)
        throw new ExtensionException("No discount factor has been defined for agent " + context.getAgent.id)

      if(agent.actionSelection.method == "")
        throw new ExtensionException("No action selection method has been defined for agent " + context.getAgent.id)

      val actualState : String = agent.getState(context)
      var actualQlist : List[Double] = null
      val optQlist : Option[List[Double]] = agent.qTable.get(actualState)

      if(optQlist.isEmpty) { //Estado não visitado anteriormente
        actualQlist = List.fill(agent.actions.length)(0)
        agent.qTable += (actualState -> actualQlist)
      } else {
        actualQlist = optQlist.get
      }

      val actionActualState : Int = agent.actionSelection.getAction(actualQlist, context)

      //val params : Array[AnyRef] = Array()
      agent.actions(actionActualState).perform(context, Array(AnyRef))

      val qValueActualState : Double = actualQlist(actionActualState)
      val reward : Double = try agent.rewardFunc.report(context, Array(AnyRef)).asInstanceOf[Double]
      catch {
        case _ : NullPointerException =>
          throw new ExtensionException("No reward function for agent " + context.getAgent.id + " was defined")
      }
      val newState : String = agent.getState(context)
      val newStateBestAction : Double = agent.getBestActionExpectedReward(newState)

      val newQvalue : Double =
        qValueActualState + (agent.learningRate * (reward + (agent.discountFactor * newStateBestAction) - qValueActualState))

      val newQlist : List[Double] = actualQlist.patch(actionActualState, List(newQvalue), 1)
      agent.qTable += (actualState -> newQlist)

      val print : String =
          "actual State: " + actualState + "\n" +
          "actual qlist: " + actualQlist.toString() + "\n" +
          "qValue Actual State: " + qValueActualState + "\n" +
          "reward: " + reward + "\n" +
          "new State: " + newState + "\n" +
          "new state best action: " + newStateBestAction + "\n" +
          "new Qvalue: " + newQvalue + "\n" +
          "new QList: " + newQlist + "\n-----------------------------"

      context.workspace.outputObject(
        print , null, true, false, Normal)

      val isEndEpisode : Boolean = agent.isEndEpisode.report(context, Array()).asInstanceOf[Boolean]
      if(isEndEpisode) {
        agent.episode = agent.episode + 1
        if(agent.actionSelection.epsilon - agent.actionSelection.decreaseRate < 0)
          agent.actionSelection.epsilon = 0
        else if (agent.actionSelection.epsilon - agent.actionSelection.decreaseRate > 0)
          agent.actionSelection.epsilon = agent.actionSelection.epsilon - agent.actionSelection.decreaseRate
        agent.resetEpisode.perform(context, Array())
      }
    }
  }
}

class GetEpisode extends Reporter {
  override def getSyntax: Syntax = Syntax.reporterSyntax(ret = NumberType)

  override def report(args: Array[Argument], context: Context): AnyRef = {
    Session.instance().getAgent(context.getAgent).get.episode.toLogoObject
  }
}

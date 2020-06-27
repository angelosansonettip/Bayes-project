package di.unito.eliminationask;
import aima.core.probability.RandomVariable;
import java.util.HashSet;
import java.util.Set;

public class SimpleNode {

    public SimpleNode(RandomVariable rnd){
        randomVariable = rnd;
    }

    RandomVariable randomVariable;

    Set<SimpleNode> adjacentNodes = new HashSet<>();

    public boolean scoreComputed = false;
    public int score;

    public RandomVariable getRandomVariable(){
        return randomVariable;
    }

    public Set<SimpleNode> getAdjacentNodes(){
        return adjacentNodes;
    }

    @Override
    public String toString() {
        return randomVariable.toString();
    }
}

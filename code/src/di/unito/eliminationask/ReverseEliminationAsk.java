package di.unito.eliminationask;

import aima.core.probability.RandomVariable;
import aima.core.probability.bayes.BayesianNetwork;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ReverseEliminationAsk extends CustomEliminationAsk {

    @Override
    public List<RandomVariable> order(BayesianNetwork bn, Collection<RandomVariable> vars) {
        List<RandomVariable> order = new ArrayList(vars);
        Collections.reverse(order);
        return order;
    }
}

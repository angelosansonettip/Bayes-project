package di.unito.eliminationask;

import aima.core.probability.RandomVariable;
import aima.core.probability.bayes.BayesianNetwork;
import aima.core.probability.bayes.Node;
import aima.core.util.SetOps;

import java.util.*;

public class MinFillEliminationAsk extends CustomEliminationAsk {
    @Override
    public List<RandomVariable> order(BayesianNetwork bn, Collection<RandomVariable> vars) {
        HashMap<RandomVariable, SimpleNode> nodes = new HashMap<>();

        //inizializzazione
        for(RandomVariable var : vars){
            SimpleNode node = nodes.getOrDefault(var, null);
            if(node == null){
                node = new SimpleNode(var);
                nodes.put(var, node);
            }

            //aggiungo tutti i vicini
            for(Node bayesNode : bn.getNode(var).getMarkovBlanket()){

                RandomVariable subVar = bayesNode.getRandomVariable();
                if(subVar == var)
                    continue;

                SimpleNode subNode = nodes.getOrDefault(subVar, null);
                if(subNode == null){
                    subNode = new SimpleNode(subVar);
                    nodes.put(subVar, subNode);
                }

                node.adjacentNodes.add(subNode);
            }
        }

        //
        ArrayList<RandomVariable> orderedVars = new ArrayList<>();

        while (nodes.size() > 0) {

            //prendi il nodo con meno archi
            int min = Integer.MAX_VALUE;
            SimpleNode minNode = null;

            for(SimpleNode node : nodes.values()){

                //
                if(!node.scoreComputed) {
                    int newArcs = 0;

                    Set<SimpleNode> consideredNodes = new HashSet<>();
                    for (SimpleNode subNode : node.adjacentNodes) {

                        //calcolo l'insieme dei nodi che formeranno nuove coppie col sotto-nodo considerato
                        //N.B. un nodo è già collegato con tutti i nodi della Markov Blanket
                        Set<SimpleNode> nonAdjacentNodes = new LinkedHashSet(node.adjacentNodes);
                        nonAdjacentNodes.removeAll(subNode.adjacentNodes);

                        //rimuovo dalla lista l'insieme dei nodi che sono stati già considerati, perché nel caso avranno già creato loro un collegamento
                        nonAdjacentNodes.removeAll(consideredNodes);

                        newArcs += nonAdjacentNodes.size() - 1;

                        //aggiungi il nodo alla lista dei considerati
                        consideredNodes.add(subNode);
                    }

                    //memorizzo score
                    node.scoreComputed = true;
                    node.score = newArcs;
                }

                if(node.score < min){
                    min = node.score;
                    minNode = node;
                }
            }

            //aggiungi un arco tra ogni coppia di nodi non adiacenti per i vicini del nodo selezionato
            for(SimpleNode node : minNode.adjacentNodes){
                //si aggiungono tutti i nodi adiacenti al nodo minimo
                node.adjacentNodes.addAll(minNode.adjacentNodes);

                //un nodo non può essere adiacente a se stesso
                node.adjacentNodes.remove(node);

                //il nodo non è piu collegato al nodo minnode, visto che sarà rimosso
                node.adjacentNodes.remove(minNode);

                //sarà ricalcolato lo score
                node.scoreComputed = false;
            }

            //rimozione nodo selezionato
            nodes.remove(minNode.randomVariable);

            //aggiungere nodo alla lista ordinata
            orderedVars.add(minNode.randomVariable);
        }

        return orderedVars;


    }
}

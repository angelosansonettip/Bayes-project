package di.unito.eliminationask;

import aima.core.probability.CategoricalDistribution;
import aima.core.probability.Factor;
import aima.core.probability.RandomVariable;
import aima.core.probability.bayes.BayesianNetwork;
import aima.core.probability.bayes.FiniteNode;
import aima.core.probability.bayes.Node;
import aima.core.probability.bayes.exact.EliminationAsk;
import aima.core.probability.bayes.impl.BayesNet;
import aima.core.probability.bayes.impl.FullCPTNode;
import aima.core.probability.proposition.AssignmentProposition;
import aima.core.probability.util.ProbabilityTable;

import java.util.*;

public abstract class CustomEliminationAsk extends EliminationAsk {

    private static final ProbabilityTable _identity = new ProbabilityTable(new double[] { 1.0 });
    public boolean pruneNet = true;

    @Override public CategoricalDistribution eliminationAsk(final RandomVariable[] X,
                                                            final AssignmentProposition[] e,
                                                            final BayesianNetwork bn) {
        //ottengo la rete ottimizzata
        BayesianNetwork newBn = bn;
        if(pruneNet)
            newBn = getPrunedBayesNet(X, e, newBn);


        //si ottengono le variabili secondo l'ordine topologico della rete non potata
        Set<RandomVariable> hidden = new HashSet<RandomVariable>();
        List<RandomVariable> VARS = new ArrayList<RandomVariable>();
        calculateVariables(X, e, bn, hidden, VARS);

        //se la rete viene potata, si mantengono solo i nodi presenti nella nuova rete
        // (si assicura lo stesso ordinamento delle variabili rispetto alla rete non potata)
        if(pruneNet)
        {
            VARS.retainAll(newBn.getVariablesInTopologicalOrder());
            hidden.retainAll(newBn.getVariablesInTopologicalOrder());
        }

        //lista dei fattori
        List<Factor> factors = new ArrayList<Factor>();

        //ottengo l'ordinamento delle variabili
        List<RandomVariable> orderVars = order(newBn, VARS);
        System.out.println("Order of vars:" + orderVars.toString());

        //inizializzo quante volte ciascuna variable random è presente nelle varie cpt
        HashMap<RandomVariable, Integer> countNode = new HashMap<>();
        for (RandomVariable var : orderVars) {
            Node node = newBn.getNode(var);

            //una variabile è presente una volta per ciascun figlio, più la sua CPT personale
            countNode.put(var, node.getChildren().size() + 1);
        }


        for (RandomVariable var : orderVars) {

            //creo il fattore
            Factor fct = makeFactor(var, e, newBn);
            factors.add(0, fct);

            //effettuo la sum-out delle variabili che non sono più presenti nelle cpt
            for(RandomVariable subRand : fct.getArgumentVariables()){

                //decremento di 1 il conteggio
                int newCount = countNode.get(subRand) - 1;
                countNode.put(subRand, newCount);

                //sum-out se la variabile è hidden
                if (newCount == 0 && hidden.contains(subRand)) {
                    factors = sumOut(subRand, factors, newBn);
                }
            }
        }


        // return NORMALIZE(POINTWISE-PRODUCT(factors))
        Factor product = pointwiseProduct(factors);

        // Note: Want to ensure the order of the product matches the
        // query variables
        return ((ProbabilityTable) product.pointwiseProductPOS(_identity, X)).normalize();
    }

    @Override
    public abstract List<RandomVariable> order(BayesianNetwork bn, Collection<RandomVariable> vars);


    private BayesianNetwork getPrunedBayesNet(RandomVariable[] X,
                                              AssignmentProposition[] e,
                                              BayesianNetwork originalBn) {

        //ottengo l'insieme delle variabili di evidenza
        Set<RandomVariable> evidenceVars = new HashSet<>();
        for(AssignmentProposition aP : e)
            evidenceVars.add(aP.getTermVariable());

        //vengono rimossi i nodi che non sono antenati né di variabili di query né di quelle di evidenza
        List<RandomVariable> usefulNodes = removeUselessNodes(originalBn, evidenceVars, X);
        //System.out.println("Nodes after 'removeUselessNodes': " + usefulNodes.toString());

        //si effettua il pruning secondo la logica del Moral Graph
        usefulNodes = removeNodeForMoralGraph(X, evidenceVars, originalBn, usefulNodes);
        //System.out.println("Nodes after 'removeNodeForMoralGraph': " + usefulNodes.toString());

        //rimuovo gli archi inutili (relativi alle variabili di evidenza)
        Node[] rootNodes = removeUselessArcs(originalBn, usefulNodes, evidenceVars, e);

        //restituisco la nuova rete Bayesiana
        return new BayesNet(rootNodes);
    }

    private List<RandomVariable> removeUselessNodes(BayesianNetwork bn, Set<RandomVariable> evidenceVars, RandomVariable[] X)
    {
        //lista dei nodi da mantenere
        List<RandomVariable> usefulNodes = new ArrayList<>();

        //parto dai nodi foglia salendo fino alla radice
        List<RandomVariable> allNodes = new ArrayList<>(bn.getVariablesInTopologicalOrder());
        Collections.reverse(allNodes);

        for (RandomVariable rnd : allNodes){
            //se rnd è una variabile di evidenza o query non va rimossa...
            if(Arrays.asList(X).contains(rnd) || evidenceVars.contains(rnd)) {
                usefulNodes.add(rnd);
            }
            else{

                //... altrimenti si controlla che i figli della rnd siano stati inclusi
                Set<Node> childrenNodes = bn.getNode(rnd).getChildren();
                for(Node child : childrenNodes){
                    if(usefulNodes.contains(child.getRandomVariable())){
                        //ok
                        usefulNodes.add(rnd);
                        break;
                    }
                }
            }
        }

        return usefulNodes;
    }

    private List<RandomVariable> removeNodeForMoralGraph(RandomVariable[] X,
                                                         Set<RandomVariable> evidenceVars,
                                                         BayesianNetwork bn, List<RandomVariable> rands) {

        //lista dei nodi da mantenere
        List<RandomVariable> usefulNodes = new ArrayList<>();

        //si mantengono le variabili di evidenza
        usefulNodes.addAll(evidenceVars);

        //i primi nodi da espandere saranno quelli delle query
        List<Node> varsToExpand = new ArrayList<>();
        for(RandomVariable x : X)
            varsToExpand.add(bn.getNode(x));

        //si mantiene la lista dei nodi già espansi, in maniera tale da non considerarli nuovamente
        Set<Node> expandedNodes = new HashSet<>();

        while (varsToExpand.size() > 0){

            //ottengo il nodo da espandere e lo rimuovo dalla lista
            Node node = varsToExpand.get(0);
            varsToExpand.remove(node);

            //ottengo la relativa random variable
            RandomVariable rnd = node.getRandomVariable();

            //se è una variabile di evidenza si termina l'espansione, in quanto formano una barriera
            //inoltre si controlla che la variabile non sia stata già esclusa da precedenti tagli
            //e che non sia stata già espansa
            if(evidenceVars.contains(rnd) || !rands.contains(rnd) || expandedNodes.contains(node))
                continue;

            //aggiungo il nodo all'elenco dei nodi da mantenere e da espandere
            usefulNodes.add(rnd);
            expandedNodes.add(node);

            //per definizione sono questi i nodi che sono prossimi alla frontiera
            varsToExpand.addAll(node.getMarkovBlanket());
        }

        return usefulNodes;
    }

    private Node[] removeUselessArcs(BayesianNetwork originalBn,
                                     List<RandomVariable> usefulNodes,
                                     Set<RandomVariable> evidenceVars,
                                     AssignmentProposition[] e){

        //elenco dei nodi radice
        ArrayList<FiniteNode> createdRootNodes = new ArrayList<>();
        ArrayList<FiniteNode> createdNodes = new ArrayList<>();

        for (RandomVariable var : originalBn.getVariablesInTopologicalOrder()){

            //se è un nodo rimosso per il pruning, si salta la computazione
            if(!usefulNodes.contains(var))
                continue;

            FiniteNode node = (FiniteNode)originalBn.getNode(var);

            //ottengo random variable e fattore
            RandomVariable rnd = node.getRandomVariable();
            ProbabilityTable fct = null;

            //se è una variabile di evidenza il fattore viene creato ad hoc
            boolean isEvidence = false;
            for(AssignmentProposition evidenceAssign : e){

                //si ottiene l'assegnamento della variabile corrispondente
                if(evidenceAssign.getTermVariable() == var) {
                    isEvidence = true;

                    //ottengo tutte le evidenze tranne quella in questione
                    ArrayList<AssignmentProposition> otherEvidences = new ArrayList<>(Arrays.asList(e));
                    otherEvidences.remove(evidenceAssign);

                    fct = (ProbabilityTable)makeFactor(var, otherEvidences.toArray(new AssignmentProposition[0]), originalBn);

                    break;
                }
            }

            if(!isEvidence){
                fct = (ProbabilityTable)makeFactor(var, e, originalBn);
            }


            //ottengo genitori
            //i genitori sono tutti i nodi tranne quelli di evidenza
            ArrayList<Node> newParents = new ArrayList<>();

            if (!node.isRoot())
            {
                //nodo non radice
                for(Node parentNode : node.getParents()){

                    //se il parent non è una variabile di evidenza...
                    if(!evidenceVars.contains(parentNode.getRandomVariable())) {

                        if(usefulNodes.contains(parentNode.getRandomVariable())) {

                            //associo il nodo genitore dalla lista dei nuovi nodi
                            for (Node newSubNode : createdNodes) {
                                if (newSubNode.getRandomVariable() == parentNode.getRandomVariable()) {
                                    newParents.add(newSubNode);
                                    break;
                                }
                            }
                        }

                        else {
                            fct = fct.sumOut(parentNode.getRandomVariable()).normalize();
                        }
                    }
                }
            }

            FiniteNode newNode = new FullCPTNode(rnd, fct.getValues(), newParents.toArray(new Node[0]));
            createdNodes.add(newNode);

            //se non ha parent allora è radice
            if(newParents.size() == 0)
                createdRootNodes.add(newNode);
        }

        return createdRootNodes.toArray(new Node[0]);
    }

    public static double roundAvoid(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    private Factor makeFactor(RandomVariable var, AssignmentProposition[] e,
                              BayesianNetwork bn) {

        Node n = bn.getNode(var);

        if (!(n instanceof FiniteNode)) {
            throw new IllegalArgumentException(
                    "Elimination-Ask only works with finite Nodes.");
        }
        FiniteNode fn = (FiniteNode) n;
        List<AssignmentProposition> evidence = new ArrayList<AssignmentProposition>();
        for (AssignmentProposition ap : e) {
            if (fn.getCPT().contains(ap.getTermVariable())) {
                evidence.add(ap);
            }
        }

        return fn.getCPT().getFactorFor(
                evidence.toArray(new AssignmentProposition[evidence.size()]));
    }

    private List<Factor> sumOut(RandomVariable var, List<Factor> factors,
                                BayesianNetwork bn) {
        List<Factor> summedOutFactors = new ArrayList<Factor>();
        List<Factor> toMultiply = new ArrayList<Factor>();
        for (Factor f : factors) {
            if (f.contains(var)) {
                toMultiply.add(f);
            } else {
                // This factor does not contain the variable
                // so no need to sum out - see AIMA3e pg. 527.
                summedOutFactors.add(f);
            }
        }

        summedOutFactors.add(pointwiseProduct(toMultiply).sumOut(var));

        return summedOutFactors;
    }

    private Factor pointwiseProduct(List<Factor> factors) {

        Factor product = factors.get(0);
        for (int i = 1; i < factors.size(); i++) {
            product = product.pointwiseProduct(factors.get(i));
        }

        return product;
    }
}

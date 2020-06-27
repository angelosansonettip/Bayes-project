package di.unito.eliminationask;

import aima.core.probability.CategoricalDistribution;
import aima.core.probability.Factor;
import aima.core.probability.RandomVariable;
import aima.core.probability.bayes.BayesianNetwork;
import aima.core.probability.bayes.FiniteNode;
import aima.core.probability.bayes.Node;
import aima.core.probability.proposition.AssignmentProposition;
import aima.core.probability.util.ProbabilityTable;

import java.util.*;

public class CustomDynamicAsk {

    private static final ProbabilityTable _identity = new ProbabilityTable(new double[] { 1.0 });

    public CategoricalDistribution [] ask (CustomDynamicBayesianNetwork dbn,
                                           AssignmentProposition[][] assignmentProposition,
                                           RandomVariable[] query,
                                           CustomEliminationAsk elAsk){

        //creo i fattori per X 0
        List<Factor> factors = new ArrayList<>();
        for(RandomVariable randomVariable : dbn.getX_0()){
            Factor fct = makeFactor(randomVariable, new AssignmentProposition[0], dbn);
            factors.add(0, fct);
        }

        List<Factor> initial_factors = new ArrayList<>();
        initial_factors.addAll(factors);

        //ottengo l'ordinamento delle variabili, escludendo le X0, dato che sono già fattorizzate
        List<RandomVariable> orderVars = elAsk.order(dbn.getPriorNetwork(), dbn.getVariablesInTopologicalOrder());
        orderVars.removeAll(dbn.getX_0());
        System.out.println("Ordine delle variabili:" + orderVars.toString());

        //numero di steps
        int n = assignmentProposition.length;
        CategoricalDistribution[] result = new CategoricalDistribution[n];

        for(int i=0; i<n; i++){

            //inizializzo quante volte ciascuna variable random è presente nelle varie cpt
            HashMap<RandomVariable, Integer> countNode = new HashMap<>();
            for (RandomVariable var : dbn.getVariablesInTopologicalOrder()) {
                Node node = dbn.getPriorNetwork().getNode(var);

                //una variabile è presente una volta per ciascun figlio, più la sua CPT personale
                //per le variabili X0, dato che sono già fattorizzate, non si calcola la CPT personale
                int count = node.getChildren().size();
                if(!dbn.getX_0().contains(var))
                    count+=1;

                countNode.put(var, count);
            }


            //inizializzo la lista dei fattori
            factors.clear();
            factors.addAll(initial_factors);

            //creo i fattori in base alle evidenze dello step i-esimo
            AssignmentProposition[] assignment = assignmentProposition[i];
            for (RandomVariable var : orderVars) {

                //creo il fattore
                Factor fct = makeFactor(var, assignment, dbn.getPriorNetwork());
                factors.add(0, fct);

                //effettuo la sum-out delle variabili che non sono più presenti nelle cpt
                for(RandomVariable subRand : fct.getArgumentVariables()){

                    //decremento di 1 il conteggio
                    int newCount = countNode.get(subRand) - 1;
                    countNode.put(subRand, newCount);

                    //sum-out se la variabile è hidden
                    if (newCount == 0 && !dbn.getX_1().contains(subRand)) {
                        factors = sumOut(subRand, factors, dbn.getPriorNetwork());
                    }
                }
            }

            //raggruppo i fattori tenendo conto delle variabili random in comune
            factors = pointwiseProductSmart(factors, dbn);

            //effettuo la sum-out di tutte le variabili che non sono di query
            ArrayList<RandomVariable> varsToSumOut = new ArrayList<>(dbn.getX_1());
            varsToSumOut.removeAll(Arrays.asList(query));

            ProbabilityTable queryFactor = ((ProbabilityTable)pointwiseProduct(factors))
                                            .sumOut(varsToSumOut.toArray(new RandomVariable[0]));

            //aggiungo il risultato ottenuto
            result[i] = queryFactor.pointwiseProductPOS(_identity, query).normalize();

            //inizializzo nuovamente la lista dei fattori iniziali
            initial_factors = new ArrayList<>();

            for(Factor fct : factors){

                List<RandomVariable> newRnds = new ArrayList<>();
                for(RandomVariable rnd : fct.getArgumentVariables())
                    newRnds.add(dbn.getX_1_to_X_0().get(rnd));

                double[] factorValues = ((ProbabilityTable)fct).normalize().getValues();
                initial_factors.add(new ProbabilityTable(factorValues, newRnds.toArray(new RandomVariable[0])));
            }
        }

        return result;
    }

    private Factor makeFactor(RandomVariable var, AssignmentProposition[] e,
                              BayesianNetwork bn) {

        Node n = bn.getNode(var);

        if (!(n instanceof FiniteNode)) {
            throw new IllegalArgumentException(
                    "Elimination-Ask only works with finite Nodes.");
        }
        FiniteNode fn = (FiniteNode) n;
        List<AssignmentProposition> evidence = new ArrayList<>();
        for (AssignmentProposition ap : e) {
            if (fn.getCPT().contains(ap.getTermVariable())) {
                evidence.add(ap);
            }
        }

        return fn.getCPT().getFactorFor(
                evidence.toArray(new AssignmentProposition[0]));
    }

    private Factor pointwiseProduct(List<Factor> factors) {

        Factor product = factors.get(0);
        for (int i = 1; i < factors.size(); i++) {
            product = product.pointwiseProduct(factors.get(i));
        }

        return product;
    }

    //variabile utilizzata per ottenere l'elenco dei fattori raggruppati, data una variabile random
    HashMap<RandomVariable, Integer> randomToFactors = null;

    private List<Factor> pointwiseProductSmart(List<Factor> factors, CustomDynamicBayesianNetwork dbn) {

        List<Factor> factorsToConsider = new ArrayList<>(factors);
        List<Factor> result = new ArrayList<>();

        if(randomToFactors == null) {

            //inizializzo variabile a supporto per le query successive
            randomToFactors = new HashMap<>();
            int i = 0;

            while (factorsToConsider.size() > 0) {

                //prendo il primo dei fattori da considerare e lo rimuovo dalla lista
                Factor factor = factorsToConsider.get(0);
                factorsToConsider.remove(factor);

                //memorizzo i fattori collegati tra loro
                List<Factor> boundFactors = new ArrayList<>();
                boundFactors.add(factor);

                //memorizzo le variabili random appartenenti ai vari fattori
                Set<RandomVariable> variablesInFactor = new HashSet<>(factor.getArgumentVariables());

                boolean continueSearch = true;
                while (continueSearch) {
                    continueSearch = false;

                    Factor foundFactor = null;

                    //per ciascun fattore controllo se ha qualche variabile random in comune con l'insieme delle variabili random
                    //dei fattori correnti
                    for (Factor searchFactor : factorsToConsider) {
                        for (RandomVariable searchVariable : variablesInFactor) {
                            if (searchFactor.getArgumentVariables().contains(searchVariable)) {

                                //le sue variabili diventano collegate
                                foundFactor = searchFactor;
                                break;
                            }
                        }

                        if (foundFactor != null)
                            break;
                    }

                    //se ho trovato un fattore, lo includo nella lista dei fattori collegati e aggiorno le altre liste
                    if (foundFactor != null) {

                        variablesInFactor.addAll(foundFactor.getArgumentVariables());
                        boundFactors.add(foundFactor);
                        factorsToConsider.remove(foundFactor);

                        continueSearch = true;
                    }
                }

                //aggiungo prob. congiunta dei fattori
                result.add(pointwiseProduct(boundFactors));

                //per ciascuna variabile random indico l'elenco dei fattori ad essa associati
                //in questa maniera le ricerche successive saranno piuttosto veloci
                for(RandomVariable rnd : variablesInFactor){
                    randomToFactors.put(rnd, i);
                }

                i++;
            }
        }
        else{

            HashMap<Integer, List<Factor>> groupedFactors = new HashMap<>();
            for(Integer i : randomToFactors.values()){
                groupedFactors.put(i, new ArrayList<>());
            }

            while (factorsToConsider.size() > 0) {

                //prendo il primo dei fattori
                Factor factor = factorsToConsider.get(0);
                factorsToConsider.remove(factor);

                //ottengo i fattori collegati (l'indicizzazione è fatta con le x0)
                RandomVariable rnd = factor.getArgumentVariables().iterator().next();
                Integer indexFactor = randomToFactors.get(rnd);
                groupedFactors.get(indexFactor).add(factor);
            }

            //faccio il prodotto dei fattori raggruppati
            for(List<Factor> gfactors : groupedFactors.values()){
                result.add(pointwiseProduct(gfactors));
            }
        }
        return result;
    }

    private List<Factor> sumOut(RandomVariable var, List<Factor> factors,
                                BayesianNetwork bn) {
        List<Factor> summedOutFactors = new ArrayList<>();
        List<Factor> toMultiply = new ArrayList<>();
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

}

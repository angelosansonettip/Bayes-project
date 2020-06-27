package di.unito;

import aima.core.probability.CategoricalDistribution;
import aima.core.probability.RandomVariable;
import aima.core.probability.bayes.*;
import aima.core.probability.bayes.approx.ParticleFiltering;
import aima.core.probability.bayes.impl.BayesNet;
import aima.core.probability.bayes.impl.FullCPTNode;
import aima.core.probability.domain.FiniteDomain;
import aima.core.probability.proposition.AssignmentProposition;
import di.unito.bnparser.BifReader;
import di.unito.eliminationask.*;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

public class Main {

    static String[] elimination_methods = {
            "ReverseEliminationAsk",
            "MinDegreeEliminationAsk",
            "MinFillEliminationAsk"};

    static String staticNetsPath = "nets/static";
    static String dynamicNetsPath = "nets/dynamic";
    static String stats_static_nets_csv_name_file = "risultati_statiche_tempo.csv";
    static String stats_dynamic_nets_csv_name_file = "risultati_dinamiche_tempo.csv";
    static int dynamicNetsNumberOfSteps = 10;

    public static void main(String[] args) throws ClassNotFoundException, IllegalAccessException, InstantiationException {

        try {
             staticBNs();
             dynamicBNs();
        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex){
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
    }


    static void staticBNs() throws ClassNotFoundException, InstantiationException, IllegalAccessException {

        //ottengo l'insieme delle reti
        File dir = new File(staticNetsPath);
        File[] files = dir.listFiles((dir1, name) -> name.endsWith(".xml"));
        String[] query_type = new String[]{"FirstQuery", "LastQuery", "MediumQuery"};
        Random randomNumbers = new Random();

        //Matrice contenente per ogni rete e per ogni tipologia di query le statistiche in termini di tempo di elaborazione
        //All'interno dell'oggetto stats, per ogni rete e tipologia di query viene mantenuta un'altra matrice dove abbiamo
        //una colonna per ogni metodo di eliminazone e due righe per distimguere le varianti con o senza pruning
        Stats[][] stats = new Stats[files.length][3];
        //Array che tiene traccia per ogni rete del fattore più grande
        int[] max_factor = new int[files.length];
        //Arrau che tiene traccia per ogni rete della sua dimensione
        int [] net_size = new int[files.length];
        int file_number = 0;

        //Loop delle reti
        for (File xmlfile : files) {

            System.out.println("\n---------------------------\nELABORAZIONE DELLA RETE " + xmlfile.getName() + "\n");
            BayesianNetwork bn = BifReader.readBIF(xmlfile);

            RandomVariable[] randomVariables = bn.getVariablesInTopologicalOrder().toArray(new RandomVariable[0]);

            int max = Integer.MIN_VALUE;
            //Dimensione della rete
            net_size[file_number] = randomVariables.length;
            //Calcolo del fattore più grande della rete
            for (RandomVariable randomVariable : randomVariables) {
                int number_of_factors = bn.getNode(randomVariable).getParents().size() + 1;
                if (number_of_factors > max) {
                    max = number_of_factors;
                    max_factor[file_number] = max;
                }
            }
            int n = randomVariables.length;
            int numberQuery = n / 4;
            int numberEvidence = n / 4;
            int query_number = 0;

            //Loop sulle query
            for (String method : query_type) {
                stats[file_number][query_number] = new Stats();
                //query
                RandomVariable[] qrv = new RandomVariable[numberQuery];

                //evidenza
                AssignmentProposition[] ap = new AssignmentProposition[numberEvidence];

                //genero le variabili di query e quelle di evidenza in maniera automatica secondo il tipo di rete
                int indexQuery;
                int indexRandVars;

                System.out.println("\n\t--- Metodo di query: " + method);
                switch (method) {
                    case "FirstQuery":

                        indexQuery = 0;
                        indexRandVars = 0;

                        while (indexQuery < numberQuery) {

                            qrv[indexQuery] = randomVariables[indexRandVars];

                            indexRandVars += 2;
                            indexQuery++;
                        }

                        //evidenza
                        indexQuery = 0;
                        indexRandVars = n - 1;

                        while (indexQuery < numberEvidence) {

                            RandomVariable rnd = randomVariables[indexRandVars];
                            FiniteDomain dom = (FiniteDomain) rnd.getDomain();
                            ap[indexQuery] = new AssignmentProposition(rnd, dom.getValueAt(randomNumbers.nextInt(dom.size())));

                            indexRandVars -= 2;
                            indexQuery++;
                        }

                        break;
                    case "LastQuery":

                        indexQuery = 0;
                        indexRandVars = n - 1;

                        while (indexQuery < numberQuery) {

                            qrv[indexQuery] = randomVariables[indexRandVars];

                            indexRandVars -= 2;
                            indexQuery++;
                        }

                        //evidenza
                        indexQuery = 0;
                        indexRandVars = 0;

                        while (indexQuery < numberEvidence) {

                            RandomVariable rnd = randomVariables[indexRandVars];
                            FiniteDomain dom = (FiniteDomain) rnd.getDomain();
                            ap[indexQuery] = new AssignmentProposition(rnd, dom.getValueAt(randomNumbers.nextInt(dom.size())));

                            indexRandVars += 2;
                            indexQuery++;
                        }

                        break;
                    case "MediumQuery":

                        indexQuery = 0;
                        indexRandVars = n * 3 / 8;

                        while (indexQuery < numberQuery) {

                            qrv[indexQuery] = randomVariables[indexRandVars];

                            indexRandVars += 2;
                            indexQuery++;
                        }

                        //evidenza
                        indexQuery = 0;
                        indexRandVars = 0;

                        while (indexQuery < numberEvidence) {

                            RandomVariable rnd = randomVariables[indexRandVars];
                            FiniteDomain dom = (FiniteDomain) rnd.getDomain();
                            ap[indexQuery++] = new AssignmentProposition(rnd, dom.getValueAt(randomNumbers.nextInt(dom.size())));

                            //controllo se siamo arrivati alla fine
                            if (indexQuery >= numberEvidence)
                                break;

                            rnd = randomVariables[n - 1 - indexRandVars];
                            dom = (FiniteDomain) rnd.getDomain();
                            ap[indexQuery++] = new AssignmentProposition(rnd, dom.getValueAt(randomNumbers.nextInt(dom.size())));

                            indexRandVars += 2;
                        }

                        break;
                }

                //stampo le variabili di query e di evidenza
                for (RandomVariable rnd : qrv)
                    System.out.println("Query:" + rnd.toString());
                for (AssignmentProposition rnd : ap)
                    System.out.println("Evidenza:" + rnd.getTermVariable().toString() + " = " + rnd.getValue());

                //effettuo inferenza in base ai tre tipi di ordinamento
                boolean[] pruned = {true, false};
                int pruned_number = 0;
                for (boolean temp : pruned) {
                    int elimination_number = 0;
                    for (String elimination_method : elimination_methods) {
                        Class<?> classe = Class.forName("di.unito.eliminationask." + elimination_method);
                        CustomEliminationAsk customEliminationAsk = (CustomEliminationAsk) classe.newInstance();
                        customEliminationAsk.pruneNet = temp;
                        BayesInference bi = (BayesInference) customEliminationAsk;
                        System.out.println("\n\tQuery con " + bi.getClass());

                        //stampo distribuzione di probabilità per la query
                        //Analisi del tempo di elaborazione della ask
                        StopWatch stopwatch = new StopWatch();
                        stopwatch.start();
                        CategoricalDistribution cd = bi.ask(qrv, ap, bn);
                        stopwatch.stop();
                        //Memorizzazione del tempo della ask per rete-query-opzione di pruning-metodo di eliminazione corrente
                        stats[file_number][query_number].ask_time[pruned_number][elimination_number] = stopwatch.getTotalTimeMillis();
                        System.out.print("P(Query|Evidenze) = <");

                        for (int i = 0; i < cd.getValues().length; i++) {
                            System.out.print(cd.getValues()[i]);

                            if (i < (cd.getValues().length - 1)) {
                                System.out.print(", ");
                            } else {
                                System.out.println(">");
                            }
                        }
                        elimination_number++;
                    }
                    pruned_number++;
                }
                query_number++;
            }
            file_number++;
        }
        //Creazione file csv per memorizzazione risultati analisi
        try (PrintWriter writer = new PrintWriter(new File(stats_static_nets_csv_name_file))) {

            StringBuilder sb = new StringBuilder();
            sb.append("," + elimination_methods[0] + "(pruned)," + elimination_methods[1] + "(pruned)," + elimination_methods[2] + "(pruned)," + elimination_methods[0] + "," + elimination_methods[1] + "," + elimination_methods[2] + "\n");
            int i, j;

            //File
            for (i = 0; i < files.length; i++) {
                //Query
                for (j = 0; j < query_type.length; j++) {
                    sb.append("Rete "+files[i].getName() +" con nodi "+ net_size[i] + " e con fattore massimo = " + max_factor[i] + " +" + query_type[j] + ",");
                    //Elimination
                    sb.append(stats[i][j].ask_time[0][0] + "," + stats[i][j].ask_time[0][1] + "," + stats[i][j].ask_time[0][2] + ",");
                    sb.append(stats[i][j].ask_time[1][0] + "," + stats[i][j].ask_time[1][1] + "," + stats[i][j].ask_time[1][2] + "\n");
                }
                sb.append(",,,,,,\n");
            }

            writer.write(sb.toString());
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }


    static void dynamicBNs() throws ClassNotFoundException, InstantiationException, IllegalAccessException {

        //ottengo l'insieme delle reti
        File dir = new File(dynamicNetsPath);
        File[] files = dir.listFiles((dir1, name) -> name.endsWith(".xml"));

        Random randomNumbers = new Random();
        //Matrice che tiene traccia per ogni rete e per ogni metodo di eliminazioe, dei tempi di elaborazione
        long[][] time = new long[files.length][3];
        int file_number = 0;
        //Array che tiene traccia del fattore più grande della rete
        int[] max_factor = new int[files.length];
        //Array che tiene traccia della dimensione della rete
        int [] net_size = new int[files.length];
        //Loop sulle reti
        for (File xmlfile : files) {

            System.out.println("\n---------------------------\nELABORAZIONE DELLA RETE DINAMICA " + xmlfile.getName() + "\n");
            BayesianNetwork bn = BifReader.readBIF(xmlfile);
            List<RandomVariable> randomVariables = bn.getVariablesInTopologicalOrder();
            //Memorizzazione dimensione della rete
            net_size[file_number] = randomVariables.size();
            //Calcolo del fattore più grande della rete
            int max = Integer.MIN_VALUE;
            for (RandomVariable randomVariable : randomVariables) {
                int number_of_factors = bn.getNode(randomVariable).getParents().size() + 1;
                if (number_of_factors > max) {
                    max = number_of_factors;
                    max_factor[file_number] = max;
                }
            }

            //mapping dalle variabili di stato
            Map<RandomVariable, RandomVariable> X_0_to_X1 = new HashMap<>();

            //variabili di evidenza
            Set<RandomVariable> E_1 = new HashSet<>();

            //nodi radice (X_0)
            ArrayList<Node> rootNodes = new ArrayList<>();

            //ottengo i nodi X0, X1 ed E1
            for (RandomVariable var : randomVariables) {

                if (var.getName().endsWith("_E")) {
                    //nodo di evidenza
                    E_1.add(var);
                } else if (var.getName().endsWith("_0")) {

                    //nodo di stato 0
                    rootNodes.add(bn.getNode(var));

                    //ottengo il nodo corrispondente di stato 1
                    String x1Name = var.getName().replace("_0", "");
                    for (RandomVariable subRand : randomVariables) {
                        if (subRand.getName().equals(x1Name)) {
                            X_0_to_X1.put(var, subRand);
                            break;
                        }
                    }
                }
            }

            //creo la rete dinamica
            CustomDynamicBayesianNetwork dbn = new CustomDynamicBayesianNetwork(bn, X_0_to_X1, E_1, rootNodes.toArray(new Node[0]));

            //valorizzo le variabili di evidenza per tot. passi in maniera casuale
            AssignmentProposition[][] assignments = new AssignmentProposition[dynamicNetsNumberOfSteps][E_1.size()];
            for (int n = 0; n < dynamicNetsNumberOfSteps; n++) {
                ArrayList<AssignmentProposition> assignment = new ArrayList<>();
                for (RandomVariable ev : E_1) {
                    FiniteDomain dom = (FiniteDomain) ev.getDomain();
                    assignment.add(new AssignmentProposition(ev, dom.getValueAt(randomNumbers.nextInt(dom.size()))));
                }
                assignments[n] = assignment.toArray(new AssignmentProposition[0]);
            }

            //tutte le variabili X_1 sono di query
            RandomVariable[] query = dbn.getX_1().toArray(new RandomVariable[0]);

            //stampo le variabili di query e di evidenza
            for (RandomVariable rnd : query)
                System.out.println("Query:" + rnd.toString());
            for (int n = 0; n < dynamicNetsNumberOfSteps; n++) {
                AssignmentProposition[] ap = assignments[n];
                System.out.print("Evidenza step " + n + ": ");
                for (AssignmentProposition rnd : ap)
                    System.out.print(rnd.getTermVariable().toString() + " = " + rnd.getValue() + "; ");
                System.out.println();
            }
            System.out.println();

            //test particle (solo per piccole reti)
            if(randomVariables.size() < 10)
                testParticle(rootNodes, X_0_to_X1, E_1, assignments);

            //effettuo inferenza in base ai tre tipi di ordinamento
            int elimination_number = 0;
            for (String elimination_method : elimination_methods) {
                Class<?> classe = Class.forName("di.unito.eliminationask." + elimination_method);
                BayesInference bi = (BayesInference) classe.newInstance();
                System.out.println("\n\tQuery con " + bi.getClass());

                //Calcolo dei tempi di elaborazione della ask
                StopWatch stopwatch = new StopWatch();
                stopwatch.start();
                CategoricalDistribution[] cdS = new CustomDynamicAsk().ask(dbn, assignments, query, (CustomEliminationAsk) classe.newInstance());
                stopwatch.stop();
                //Memorizzazione dei tempi di elaborazione per ogni rete e metodo di eliminazione
                time[file_number][elimination_number] = stopwatch.getTotalTimeMillis();

                //stampo distribuzione di probabilità per la query per ciascun passo
                System.out.println();
                for (int n = 0; n < dynamicNetsNumberOfSteps; n++) {
                    CategoricalDistribution cd = cdS[n];

                    System.out.print("P(Query_" + (n + 1) + "|Evidenze_" + (n + 1) + ") = <");
                    for (int i = 0; i < cd.getValues().length; i++) {
                        System.out.print(cd.getValues()[i]);

                        if (i < (cd.getValues().length - 1)) {
                            System.out.print(", ");
                        } else {
                            System.out.println(">");
                        }
                    }
                }
                elimination_number++;
            }
            file_number++;
        }
        //Creazione del file csv per memorizzazione dei risultati
        try (PrintWriter writer = new PrintWriter(new File(stats_dynamic_nets_csv_name_file))) {

            StringBuilder sb = new StringBuilder();
            sb.append("," + elimination_methods[0] + "," + elimination_methods[1] + "," + elimination_methods[2] + "\n");
            int i;
            for (i = 0; i < files.length; i++) {
                sb.append("Rete "+files[i].getName() +" con nodi "+net_size[i] +" e con fattore massimo = " + max_factor[i] + ",");
                sb.append(time[i][0] + "," + time[i][1] + "," + time[i][2] + ",");
                sb.append(",,,\n");
            }
            writer.write(sb.toString());
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }

    static void testParticle(ArrayList<Node> rootNodes, Map<RandomVariable, RandomVariable> X_0_to_X1,
                             Set<RandomVariable> E_1, AssignmentProposition[][] assignments ){

        System.out.println("TEST PARTICLE");

        //ottengo i soli nodi radice
        Node[] newRoots = new Node[rootNodes.size()];
        for(int i = 0;i < rootNodes.size(); i++){
            FullCPTNode rootNode = (FullCPTNode)rootNodes.get(i);
            newRoots[i] = new FullCPTNode(rootNode.getRandomVariable(), rootNode.getCPT().getFactorFor().getValues());
        }

        //istanzio la dynamic bayes netword
        CustomDynamicBayesianNetwork dbnP = new CustomDynamicBayesianNetwork(new BayesNet(newRoots),
                X_0_to_X1, E_1, rootNodes.toArray(new Node[0]));

        //avvio il particle filtering
        int n_samples = 10000;
        ParticleFiltering pf = new ParticleFiltering(n_samples, dbnP);

        for (int i=0; i<assignments.length; i++) {
            AssignmentProposition[][] S = pf.particleFiltering(assignments[i]);
            System.out.println("Time " + (i+1));
            printSamples(S, n_samples);
        }
    }


    private static void printSamples(AssignmentProposition[][] S, int n) {
        HashMap<String,Integer> hm = new HashMap<String,Integer>();

        int nstates = S[0].length;

        for (int i = 0; i < n; i++) {
            String key = "";
            for (int j = 0; j < nstates; j++) {
                AssignmentProposition ap = S[i][j];
                key += ap.getValue().toString();
            }
            Integer val = hm.get(key);
            if (val == null) {
                hm.put(key, 1);
            } else {
                hm.put(key, val + 1);
            }
        }

        for (String key : hm.keySet()) {
            System.out.println(key + ": " + hm.get(key)/(double)n);
        }
    }



}
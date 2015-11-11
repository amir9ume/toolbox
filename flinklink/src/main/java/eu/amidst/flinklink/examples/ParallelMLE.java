package eu.amidst.flinklink.examples;

import eu.amidst.core.datastream.DataInstance;
import eu.amidst.core.datastream.DataStream;
import eu.amidst.core.io.BayesianNetworkLoader;
import eu.amidst.core.io.DataStreamWriter;
import eu.amidst.core.models.BayesianNetwork;
import eu.amidst.core.utils.BayesianNetworkSampler;
import eu.amidst.core.variables.Variable;
import eu.amidst.flinklink.core.data.DataFlink;
import eu.amidst.flinklink.core.io.DataSetLoader;
import eu.amidst.flinklink.core.learning.parametric.ParallelMaximumLikelihood;

/**
 * Created by Hanen on 07/10/15.
 */
public class ParallelMLE {

    public static void main(String[] args) throws Exception {

        // load the true Asia Bayesian network
        BayesianNetwork originalBnet = BayesianNetworkLoader.loadFromFile(args[0]);

        System.out.println("\n Network \n " + args[0]);
        //System.out.println(originalBnet.getDAG().outputString());
        //System.out.println(originalBnet.outputString());

        //Sampling from Asia BN
        BayesianNetworkSampler sampler = new BayesianNetworkSampler(originalBnet);
        sampler.setSeed(0);
        //Load the sampled data

        int sizeData = Integer.parseInt(args[1]);
        DataStream<DataInstance> data = sampler.sampleToDataStream(sizeData);

        DataStreamWriter.writeDataToFile(data, "./tmp.arff");

        DataFlink<DataInstance> dataFlink = DataSetLoader.loadData("./tmp.arff");

        //Structure learning is excluded from the test, i.e., we use directly the initial Asia network structure
        // and just learn then test the parameter learning

        long start = System.nanoTime();

        //Parameter Learning
        ParallelMaximumLikelihood parallelMaximumLikelihood = new ParallelMaximumLikelihood();
        parallelMaximumLikelihood.setDAG(originalBnet.getDAG());
        parallelMaximumLikelihood.setDataFlink(dataFlink);
        parallelMaximumLikelihood.runLearning();
        BayesianNetwork LearnedBnet = parallelMaximumLikelihood.getLearntBayesianNetwork();

        //Check if the probability distributions of each node
        for (Variable var : originalBnet.getVariables()) {
            System.out.println("\n------ Variable " + var.getName() + " ------");
            System.out.println("\nTrue distribution:\n"+ originalBnet.getConditionalDistribution(var));
            System.out.println("\nLearned distribution:\n" + LearnedBnet.getConditionalDistribution(var));
        }

        if (LearnedBnet.equalBNs(originalBnet, 0.1))
            System.out.println("\n The true and learned networks are equals :-) \n ");
        else
            System.out.println("\n The true and learned networks are NOT equals!!! \n ");


        long duration = (System.nanoTime() - start) / 1;
        double seconds = duration / 1000000000.0;
        System.out.println("Running time: \n" + seconds + " secs");

    }
}
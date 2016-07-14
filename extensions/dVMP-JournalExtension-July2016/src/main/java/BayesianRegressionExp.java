/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and limitations under the License.
 *
 */

import eu.amidst.core.datastream.DataInstance;
import eu.amidst.core.models.BayesianNetwork;
import eu.amidst.core.models.DAG;
import eu.amidst.core.variables.Variable;
import eu.amidst.core.variables.Variables;
import eu.amidst.flinklink.core.data.DataFlink;
import eu.amidst.flinklink.core.io.DataFlinkLoader;
import eu.amidst.flinklink.core.io.DataFlinkWriter;
import eu.amidst.flinklink.core.learning.parametric.dVMP;
import eu.amidst.flinklink.core.utils.BayesianNetworkSampler;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.configuration.Configuration;

import java.io.FileNotFoundException;
import java.util.Random;

/**
 * Created by andresmasegosa on 14/7/16.
 */
public class BayesianRegressionExp {

    public static DAG getDAG(int atts) {
        Variables variables  = new Variables();

        for (int i = 0; i < atts; i++) {
            variables.newGaussianVariable("Att"+i);
        }

        Variable predVar = variables.newGaussianVariable("PRED");

        DAG dag = new DAG(variables);

        for (Variable variable : variables) {
            if (variable==predVar)
                continue;
            dag.getParentSet(predVar).addParent(variable);
        }

        return dag;
    }

    public static void generateData(ExecutionEnvironment env, int atts, int samples, int batchsize, String file) throws Exception {

        DAG dag = getDAG(atts);
        BayesianNetwork bn = new BayesianNetwork(dag);
        bn.randomInitialization(new Random(0));

        BayesianNetworkSampler sampler = new BayesianNetworkSampler(bn);
        sampler.setBatchSize(batchsize);

        DataFlinkWriter.writeDataToARFFFolder(sampler.sampleToDataFlink(env,samples),file);

    }

    public static void learn(ExecutionEnvironment env, int batchsize, String dataFile) throws FileNotFoundException {

        dVMP dvmp = new dVMP();
        dvmp.setDAG(getDAG(10));

        dvmp.setLocalThreshold(0.01);
        dvmp.setGlobalThreshold(0.001);
        dvmp.setMaximumLocalIterations(100);
        dvmp.setMaximumGlobalIterations(10);
        dvmp.setBatchSize(batchsize);

        dvmp.initLearning();

        DataFlink<DataInstance> data = DataFlinkLoader.loadDataFromFolder(env,dataFile,false);
        dvmp.updateModel(data);

        System.out.println(dvmp.getLearntBayesianNetwork().toString());

    }

    public static void main(String[] args) throws Exception {
        args = new String[]{"4", "3", "500", "100"};

        int ncores = Integer.parseInt(args[0]);
        int natts = Integer.parseInt(args[1]);
        int nsamples = Integer.parseInt(args[2]);
        int batchsize = Integer.parseInt(args[3]);

        Configuration conf = new Configuration();
        conf.setInteger("taskmanager.network.numberOfBuffers", 12000);
        conf.setInteger("taskmanager.numberOfTaskSlots",ncores);
        ExecutionEnvironment env = ExecutionEnvironment.createLocalEnvironment(conf);
        env.setParallelism(ncores);
        env.getConfig().disableSysoutLogging();

        generateData(env,natts,nsamples,batchsize, "./tmp.arff");
        learn(env,batchsize, "./tmp.arff");
    }
}

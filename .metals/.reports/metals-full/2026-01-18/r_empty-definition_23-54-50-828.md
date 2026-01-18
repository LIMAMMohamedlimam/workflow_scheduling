error id: file://<WORKSPACE>/src/main/java/org/example/CustomCostSimulation.java:
file://<WORKSPACE>/src/main/java/org/example/CustomCostSimulation.java
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1241
uri: file://<WORKSPACE>/src/main/java/org/example/CustomCostSimulation.java
text:
```scala
package org.example;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Array;
import java.util.*;

public class CustomCostSimulation {
    private static final int IS_MultiObjective = 1;
    private static  String SCHEDULING_ALGORITHM = "RDA"; // Options: ROUNDROBIN, FCFS, SA_MultiObjective
    private static  String DATASET_USED = "task80";
    

    public static@@ void main(String[] args) {

        boolean generateEvalData = false; // Set to true to generate evaluation data
        String input_data = "";
        String algorithm = "";

        input_data = args.length > 0 ? args[0] : DATASET_USED;
        algorithm = args.length > 1 ? args[1] : SCHEDULING_ALGORITHM;
        generateEvalData = args.length > 2 ? Boolean.parseBoolean(args[2]) : false;

        if (!generateEvalData){
            System.out.println("Running single simulation with Algorithm: " + algorithm + " on Dataset: " + input_data);
            runSimulation(algorithm, input_data);
        }
        if (generateEvalData){
            ArrayList<String> algorithms = new ArrayList<>(Arrays.asList(
                "SA_MultiObjective", "MOWOA", "WOASA", "WOARDA", "RDA"
            ));

            ArrayList<String> datasets = new ArrayList<>(Arrays.asList(
                "task40", "task80", "task120", "task160" , "task200", "task240", "task280"
            ));

            for (String dataset : datasets) {
                for (String alg : algorithms) {
                    for (int i=0 ;i<100;i++){
                        System.out.println("\n\n==============================");
                        System.out.println("Running simulation number " + i + " with Algorithm: " + alg + " on Dataset: " + dataset);
                        System.out.println("==============================\n");
                        runSimulation(alg, dataset);
                    }
                }
            }}
    }

    public static void runSimulation(String alg , String dataset) {
        String DATASET_USED = dataset;
        String SCHEDULING_ALGORITHM = alg;
        CloudSimPlus simulation = new CloudSimPlus(0.1);
        
        List<NodeSpec> nodes = loadNodesFromCSV(DATASET_USED + "_NodeDetails.csv");
        List<TaskSpec> tasks = loadTasksFromCSV(DATASET_USED + "_TaskDetails.csv");
        
        Map<String, NodeSpec> nodeMap = new HashMap<>();
        for (NodeSpec node : nodes) {
            node.datacenter = createDatacenter(simulation, node);
            nodeMap.put(node.nodeId, node);
        }
        
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
        broker.setVmDestructionDelay(10.0);

        // Create VMs with SpaceShared scheduler
        List<Vm> vms = new ArrayList<>();
        // System.out.println("nodes size: " + nodes.size());
        for (NodeSpec node : nodes) {
            Vm vm = new VmSimple(node.mipsRating, 2)
                    .setRam(2048)
                    .setBw(10000)
                    .setSize(50000)
                    .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vms.add(vm);
            broker.submitVm(vm);
        }

        

         // UPDATED: Create cloudlets WITHOUT assigning VMs yet
    List<Cloudlet> cloudlets = new ArrayList<>();
    // System.out.println("Creating " + tasks.size() + " cloudlets...");
    
    for (int i = 0; i < tasks.size(); i++) {
        TaskSpec task = tasks.get(i);
        
        Cloudlet cloudlet = new CloudletSimple(task.instructions, 2)
                .setFileSize(task.inputFileSize * 1024)
                .setOutputSize(task.outputFileSize * 1024)
                .setUtilizationModelCpu(new UtilizationModelFull())
                .setUtilizationModelRam(new UtilizationModelFull())
                .setUtilizationModelBw(new UtilizationModelFull());
        cloudlet.setId((long) i ) ; // Set cloudlet ID to index for easier mapping
       
        
        cloudlets.add(cloudlet);
        
        // Optional: Add finish listener
        cloudlet.addOnFinishListener(info -> {
            System.out.println("  Cloudlet " + info.getCloudlet().getId() + 
                             " finished at time " + String.format("%.2f", info.getTime()));
        });
    }
        SchedulingAlgorithm scheduler = null;
    
        // UPDATED: Apply dynamic scheduling
        System.out.println("\n=== Applying " + SCHEDULING_ALGORITHM + " Scheduling ===");
        if (IS_MultiObjective == 1) {
            scheduler = getSchedulingAlgorithm(SCHEDULING_ALGORITHM , 0.5 , 0.3 , 0.2);
        }else{
            scheduler = getSchedulingAlgorithm(SCHEDULING_ALGORITHM);
        }
        Map<Integer, Integer> cloudletToVmMap = scheduler.schedule(cloudlets, vms, tasks, nodes);
        
        

        // Submit all cloudlets after scheduling
        broker.submitCloudletList(cloudlets);

        System.out.println("\nStarting simulation...\n");
        simulation.start();

        System.out.println("\n=== Simulation completed at time: " + 
                        String.format("%.2f", simulation.clock()) + " ===");

        // Calculate and print metrics
        WorkflowMetrics metrics = new WorkflowMetrics(
            simulation, nodes, tasks, cloudlets, vms, cloudletToVmMap
        );
        System.out.println("Algorithm used: " + SCHEDULING_ALGORITHM);
        metrics.calculateAndPrint(SCHEDULING_ALGORITHM , DATASET_USED);
    }

    private static List<NodeSpec> loadNodesFromCSV(String filename) {
        System.out.println("Loading nodes from CSV: " + filename);
        List<NodeSpec> nodes = new ArrayList<>();
        
        try (Reader reader = new InputStreamReader(
                CustomCostSimulation.class.getClassLoader().getResourceAsStream(filename));
             CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
            
            String[] line;
            while ((line = csvReader.readNext()) != null) {
                System.out.println("Read line: " + Arrays.toString(line));
                if (line.length >= 5) {
                    String nodeId = line[0].trim();
                    double mips = Double.parseDouble(line[1].trim());
                    double cpuCost = Double.parseDouble(line[2].trim());
                    double memCost = Double.parseDouble(line[3].trim());
                    double bwCost = Double.parseDouble(line[4].trim());

                    double idlePower = 130.0;   // Default idle power in Watts
                    double activePower = 230.0; // Default active power in Watts
                    nodes.add(new NodeSpec(nodeId, mips, cpuCost, memCost, bwCost, idlePower, activePower));
                    System.out.println("Loaded node: " + nodeId);
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading nodes CSV: " + e.getMessage());
            e.printStackTrace();
        }
        
        return nodes;
    }
    
    private static List<TaskSpec> loadTasksFromCSV(String filename) {
        List<TaskSpec> tasks = new ArrayList<>();
        
        try (Reader reader = new InputStreamReader(
                CustomCostSimulation.class.getClassLoader().getResourceAsStream(filename));
             CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
            
            String[] line;
            while ((line = csvReader.readNext()) != null) {
                if (line.length >= 5) {
                    String taskId = line[0].trim();
                    long instructions = (long)(Double.parseDouble(line[1].trim()) * 1000);
                    long memory = Long.parseLong(line[2].trim());
                    long inputSize = Long.parseLong(line[3].trim());
                    long outputSize = Long.parseLong(line[4].trim());
                    
                    tasks.add(new TaskSpec(taskId, instructions, memory, inputSize, outputSize));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading tasks CSV: " + e.getMessage());
            e.printStackTrace();
        }
        
        return tasks;
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation, NodeSpec spec) {
    List<Pe> peList = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
        peList.add(new PeSimple(spec.mipsRating));
    }

    long hostRam = 819200;
    long hostStorage = 5000000;
    long hostBw = 500000;

    Host host = new HostSimple(hostRam, hostBw, hostStorage, peList);
    host.enableUtilizationStats();
    host.setPowerModel(new PowerModelHostSimple(1000, 100));
    // REMOVE THIS LINE: host.setPowerModel(new PowerModelLinear(spec.activePower, spec.idlePower));
    
    List<Host> hostList = new ArrayList<>();
    hostList.add(host);

    DatacenterSimple dc = new DatacenterSimple(simulation, hostList);
    dc.getCharacteristics()
        .setCostPerSecond(spec.costPerCpuSecond)
        .setCostPerMem(spec.costPerMem)
        .setCostPerBw(spec.costPerBw)
        .setCostPerStorage(spec.costPerStorage);
    

    return dc;
}

// Add this method to CustomCostSimulation class
private static SchedulingAlgorithm getSchedulingAlgorithm(String algorithmName) {
    switch (algorithmName.toUpperCase()) {
        case "RR":
            return new RoundRobinScheduling();
        case "FCFS":
            return new FCFSScheduling();
        case "MINMIN":
                return new MinMinScheduling();
            case "MAXMIN":
                return new MaxMinScheduling();
            case "SOA":
                return new SOAScheduling();
        
        default:
            System.out.println("Unknown algorithm: " + algorithmName + ", using Round Robin");
            return new RoundRobinScheduling();
    }
}

private static SchedulingAlgorithm getSchedulingAlgorithm(String algorithmName , double wmakespan , double wcost , double wenergy) {
    switch (algorithmName.toUpperCase()) {
        
        case "SA_MULTIOBJECTIVE":
            return new MultiObjectiveSAScheduling(wmakespan, wcost, wenergy);
        case "MOWOA":
            return new MOWOAScheduling(wmakespan, wcost, wenergy);
        case "WOASA":
            return new HybridWOASAScheduling(wmakespan, wcost, wenergy);
        case "WOARDA":
            return new WOARDAScheduling(wmakespan, wcost, wenergy);
        case "RDA":
            return new MORDAScheduling(wmakespan, wcost, wenergy);
        default:
            System.out.println("Unknown algorithm: " + algorithmName + ", using Round Robin");
            return new RoundRobinScheduling();
    }
}


}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 
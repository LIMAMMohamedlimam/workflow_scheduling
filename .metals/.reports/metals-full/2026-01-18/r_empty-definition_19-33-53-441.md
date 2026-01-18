error id: file://<WORKSPACE>/src/main/java/org/example/CustomCostSimulation.java:java/lang/Throwable#getMessage().
file://<WORKSPACE>/src/main/java/org/example/CustomCostSimulation.java
empty definition using pc, found symbol in pc: java/lang/Throwable#getMessage().
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 16493
uri: file://<WORKSPACE>/src/main/java/org/example/CustomCostSimulation.java
text:
```scala
// package org.example;

// import org.cloudsimplus.brokers.DatacenterBroker;
// import org.cloudsimplus.brokers.DatacenterBrokerSimple;
// import org.cloudsimplus.cloudlets.Cloudlet;
// import org.cloudsimplus.cloudlets.CloudletSimple;
// import org.cloudsimplus.core.CloudSimPlus;
// import org.cloudsimplus.datacenters.Datacenter;
// import org.cloudsimplus.datacenters.DatacenterSimple;
// import org.cloudsimplus.hosts.Host;
// import org.cloudsimplus.hosts.HostSimple;
// import org.cloudsimplus.resources.Pe;
// import org.cloudsimplus.resources.PeSimple;
// import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
// import org.cloudsimplus.vms.Vm;
// import org.cloudsimplus.vms.VmSimple;
// import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
// import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;


// import com.opencsv.CSVReader;
// import com.opencsv.CSVReaderBuilder;

// import java.io.InputStreamReader;
// import java.io.Reader;
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;

// public class CustomCostSimulation {
    
//     public static class NodeSpec {
//         String nodeId;
//         double mipsRating;
//         double costPerCpuSecond; 
//         double costPerMem;       
//         double costPerBw;        
//         double costPerStorage;   
//         Datacenter datacenter;   // Associated datacenter

//         public NodeSpec(String nodeId, double mips, double cpuCost, double memCost, double bwCost) {
//             this.nodeId = nodeId;
//             this.mipsRating = mips;
//             this.costPerCpuSecond = cpuCost;
//             this.costPerMem = memCost;
//             this.costPerBw = bwCost;
//             this.costPerStorage = 0.001; 
//         }
//     }
    
//     public static class TaskSpec {
//         String taskId;
//         long instructions;  // In Million Instructions (MI)
//         long memoryRequired; // In MB
//         long inputFileSize;  // In MB
//         long outputFileSize; // In MB

//         public TaskSpec(String taskId, long instructions, long memory, long inputSize, long outputSize) {
//             this.taskId = taskId;
//             this.instructions = instructions;
//             this.memoryRequired = memory;
//             this.inputFileSize = inputSize;
//             this.outputFileSize = outputSize;
//         }
//     }

//     public static void main(String[] args) {
//         CloudSimPlus simulation = new CloudSimPlus(0.1);
        
//         List<NodeSpec> nodes = loadNodesFromCSV("task40_nodes.csv");
//         List<TaskSpec> tasks = loadTasksFromCSV("task40_TaskDetails.csv");

//         System.out.println("Loaded " + nodes.size() + " nodes and " + tasks.size() + " tasks.");
        
//         Map<String, NodeSpec> nodeMap = new HashMap<>();
//         for (NodeSpec node : nodes) {
//             node.datacenter = createDatacenter(simulation, node);
//             nodeMap.put(node.nodeId, node);
//         }
        
//         DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
//         broker.setVmDestructionDelay(10.0); // Prevent early VM destruction

//         // Create VMs with reduced resources
//         List<Vm> vms = new ArrayList<>();
//         for (NodeSpec node : nodes) {
//             Vm vm = new VmSimple(node.mipsRating, 2)
//                     .setRam(8192)  // 8 GB RAM
//                     .setBw(20000)
//                     .setSize(50000)
//                     .setCloudletScheduler(new CloudletSchedulerSpaceShared()); // Add this line;
//             vms.add(vm);
//             broker.submitVm(vm);
//         }

//         // Create cloudlets from tasks and assign round-robin to VMs
//         List<Cloudlet> cloudlets = new ArrayList<>();
//         for (int i = 0; i < tasks.size(); i++) {
//             TaskSpec task = tasks.get(i);
//             Vm assignedVm = vms.get(i % vms.size()); // Round-robin assignment
            
//             Cloudlet cloudlet = new CloudletSimple(task.instructions, 2)
//                     .setFileSize(task.inputFileSize * 1024)  // Convert MB to KB
//                     .setOutputSize(task.outputFileSize * 1024) // Convert MB to KB
//                     .setUtilizationModelCpu(new UtilizationModelFull())
//                     .setUtilizationModelRam(new UtilizationModelFull())
//                     .setUtilizationModelBw(new UtilizationModelFull());
            
//             cloudlet.setVm(assignedVm); // Assign to specific VM
//             cloudlets.add(cloudlet);
//             broker.submitCloudlet(cloudlet);
//         }

//         simulation.start();

//         new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        
//         System.out.println("\n--- Cost Breakdown by Node ---");
//         double totalSimulationCost = 0;
        
//         // Group cloudlets by node for cost calculation
//         for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
//             NodeSpec node = nodes.get(nodeIndex);
//             double nodeProcessingCost = 0;
//             double nodeMemoryCost = 0;
//             double nodeBwCost = 0;
            
//             // Calculate costs for all cloudlets assigned to this node's VM
//             for (int i = nodeIndex; i < tasks.size(); i += nodes.size()) {
//                 Cloudlet cloudlet = cloudlets.get(i);
//                 TaskSpec task = tasks.get(i);
                
//                 nodeProcessingCost += cloudlet.getFinishTime() * node.costPerCpuSecond;
//                 nodeMemoryCost += task.memoryRequired * node.costPerMem;
//                 nodeBwCost += (task.inputFileSize + task.outputFileSize) * node.costPerBw;
//             }
            
//             double nodeTotalCost = nodeProcessingCost + nodeMemoryCost + nodeBwCost;
//             totalSimulationCost += nodeTotalCost;
            
//             System.out.printf("%s:%n", node.nodeId);
//             System.out.printf("  Processing Cost: $%.4f%n", nodeProcessingCost);
//             System.out.printf("  Memory Cost: $%.4f%n", nodeMemoryCost);
//             System.out.printf("  Bandwidth Cost: $%.4f%n", nodeBwCost);
//             System.out.printf("  Total Cost: $%.4f%n", nodeTotalCost);
//             System.out.println();
//         }
        
//         System.out.printf("Total Simulation Cost: $%.4f%n", totalSimulationCost);
//     }

//     private static List<NodeSpec> loadNodesFromCSV(String filename) {
//         List<NodeSpec> nodes = new ArrayList<>();
        
//         try (Reader reader = new InputStreamReader(
//                 CustomCostSimulation.class.getClassLoader().getResourceAsStream(filename));
//              CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
            
//             String[] line;
//             while ((line = csvReader.readNext()) != null) {
//                 if (line.length >= 5) {
//                     String nodeId = line[0].trim();
//                     double mips = Double.parseDouble(line[1].trim());
//                     double cpuCost = Double.parseDouble(line[2].trim());
//                     double memCost = Double.parseDouble(line[3].trim());
//                     double bwCost = Double.parseDouble(line[4].trim());
                    
//                     nodes.add(new NodeSpec(nodeId, mips, cpuCost, memCost, bwCost));
//                 }
//             }
//         } catch (Exception e) {
//             System.err.println("Error reading nodes CSV: " + e.getMessage());
//             e.printStackTrace();
//         }
        
//         return nodes;
//     }
    
//     private static List<TaskSpec> loadTasksFromCSV(String filename) {
//         List<TaskSpec> tasks = new ArrayList<>();
        
//         try (Reader reader = new InputStreamReader(
//                 CustomCostSimulation.class.getClassLoader().getResourceAsStream(filename));
//              CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
            
//             String[] line;
//             while ((line = csvReader.readNext()) != null) {
//                 if (line.length >= 5) {
//                     String taskId = line[0].trim();
//                     // Convert from 10^9 instructions to Million Instructions (MI)
//                     long instructions = (long)(Double.parseDouble(line[1].trim()) * 1000);
//                     long memory = Long.parseLong(line[2].trim());
//                     long inputSize = Long.parseLong(line[3].trim());
//                     long outputSize = Long.parseLong(line[4].trim());
                    
//                     tasks.add(new TaskSpec(taskId, instructions, memory, inputSize, outputSize));
//                 }
//             }
//         } catch (Exception e) {
//             System.err.println("Error reading tasks CSV: " + e.getMessage());
//             e.printStackTrace();
//         }
        
//         return tasks;
//     }

//     private static Datacenter createDatacenter(CloudSimPlus simulation, NodeSpec spec) {
//         List<Host> hostList = new ArrayList<>();
    
//         // Create 4 hosts per datacenter instead of 1
//         for (int hostNum = 0; hostNum < 13; hostNum++) {
//             List<Pe> peList = new ArrayList<>();
//             for (int i = 0; i < 4; i++) {
//                 peList.add(new PeSimple(spec.mipsRating));
//             }

//             long hostRam = 204800; 
//             long hostStorage = 1000000; 
//             long hostBw = 100000; 

//             Host host = new HostSimple(hostRam, hostBw, hostStorage, peList);
//             hostList.add(host);
//         }

//         DatacenterSimple dc = new DatacenterSimple(simulation, hostList);

//         dc.getCharacteristics()
//             .setCostPerSecond(spec.costPerCpuSecond)
//             .setCostPerMem(spec.costPerMem)
//             .setCostPerBw(spec.costPerBw)
//             .setCostPerStorage(spec.costPerStorage);

//         return dc;
//     }
// }


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
import java.util.*;

public class CustomCostSimulation {
    

    public static void main(String[] args) {
        CloudSimPlus simulation = new CloudSimPlus(0.01);
        
        List<NodeSpec> nodes = loadNodesFromCSV("task40_nodes.csv");
        List<TaskSpec> tasks = loadTasksFromCSV("task40_TaskDetails.csv");
        
        Map<String, NodeSpec> nodeMap = new HashMap<>();
        for (NodeSpec node : nodes) {
            node.datacenter = createDatacenter(simulation, node);
            nodeMap.put(node.nodeId, node);
        }
        
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
        broker.setVmDestructionDelay(10.0);
        

        // Create VMs with SpaceShared scheduler
        List<Vm> vms = new ArrayList<>();
        System.out.println("nodes size: " + nodes.size());
        for (NodeSpec node : nodes) {
            Vm vm = new VmSimple(node.mipsRating, 2)
                    .setRam(2048)
                    .setBw(10000)
                    .setSize(50000)
                    .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vms.add(vm);
            broker.submitVm(vm);
        }

        // Create cloudlets with round-robin assignment
        List<Cloudlet> cloudlets = new ArrayList<>();
        Map<Integer, Integer> cloudletToVmMap = new HashMap<>();
        
        for (int i = 0; i < tasks.size(); i++) {
            TaskSpec task = tasks.get(i);
            System.out.println("Creating cloudlet for task: " + task.taskId);
            System.out.println("  Instructions: " + task.instructions
                + ", Input Size: " + task.inputFileSize + " MB"
                + ", Output Size: " + task.outputFileSize + " MB"
            );
            System.out.println(vms.size() + " VMs available for assignment.");
            Vm assignedVm = vms.get(i % vms.size());
            
            Cloudlet cloudlet = new CloudletSimple(task.instructions, 2)
                    .setFileSize(task.inputFileSize * 1024)
                    .setOutputSize(task.outputFileSize * 1024)
                    .setUtilizationModelCpu(new UtilizationModelFull())
                    .setUtilizationModelRam(new UtilizationModelFull())
                    .setUtilizationModelBw(new UtilizationModelFull());
            
            cloudlet.setVm(assignedVm);
            cloudlets.add(cloudlet);
            broker.submitCloudlet(cloudlet);
            
            cloudletToVmMap.put((int)cloudlet.getId(), (int)assignedVm.getId());
        }

        simulation.start();

        // Calculate and print metrics
        WorkflowMetrics metrics = new WorkflowMetrics(
            simulation, nodes, tasks, cloudlets, vms, cloudletToVmMap
        );
        metrics.calculateAndPrint();
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
            System.err.println("Error reading tasks CSV: " + e.get@@Message());
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
    host.setPowerModel(new PowerModelHostSimple(300, 100));
    // REMOVE THIS LINE: host.setPowerModel(new PowerModelLinear(spec.activePower, spec.idlePower));
    
    List<Host> hostList = new ArrayList<>();
    hostList.add(host);

    DatacenterSimple dc = new DatacenterSimple(simulation, hostList);
    dc.getCharacteristics()
        .setCostPerSecond(spec.costPerCpuSecond)
        .setCostPerMem(spec.costPerMem)
        .setCostPerBw(spec.costPerBw)
        .setCostPerStorage(spec.costPerStorage);
    dc.setSchedulingInterval(1) ;

    return dc;
}

}

```


#### Short summary: 

empty definition using pc, found symbol in pc: java/lang/Throwable#getMessage().
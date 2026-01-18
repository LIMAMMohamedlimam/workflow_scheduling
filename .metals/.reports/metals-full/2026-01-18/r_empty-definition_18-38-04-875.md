error id: file://<WORKSPACE>/src/main/java/org/example/WorkflowMetrics.java:_empty_/Datacenter#
file://<WORKSPACE>/src/main/java/org/example/WorkflowMetrics.java
empty definition using pc, found symbol in pc: _empty_/Datacenter#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 5344
uri: file://<WORKSPACE>/src/main/java/org/example/WorkflowMetrics.java
text:
```scala
package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import java.util.List;
import java.util.Map;

public class WorkflowMetrics {
    private final CloudSimPlus simulation;
    private final List<NodeSpec> nodes;
    private final List<TaskSpec> tasks;
    private final List<Cloudlet> cloudlets;
    private final List<Vm> vms;
    private final Map<Integer, Integer> cloudletToVmMap;

    public WorkflowMetrics(CloudSimPlus simulation, List<NodeSpec> nodes, List<TaskSpec> tasks,
                          List<Cloudlet> cloudlets, List<Vm> vms, Map<Integer, Integer> cloudletToVmMap) {
        this.simulation = simulation;
        this.nodes = nodes;
        this.tasks = tasks;
        this.cloudlets = cloudlets;
        this.vms = vms;
        this.cloudletToVmMap = cloudletToVmMap;
    }

    public void calculateAndPrint() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKFLOW EXECUTION METRICS - MATHEMATICAL MODEL");
        System.out.println("=".repeat(80));

        // 1. MAKESPAN
        double makespan = calculateMakespan();
        System.out.printf("\n1. MAKESPAN: %.4f seconds\n", makespan);

        // 2. TOTAL COST
        double totalCost = calculateTotalCost();
        System.out.printf("\n2. TOTAL COST: $%.4f\n", totalCost);

        // 3. ENERGY CONSUMPTION
        double totalEnergy = calculateTotalEnergy();
        System.out.printf("\n3. TOTAL ENERGY: %.4f Watt-Seconds (Joules)\n", totalEnergy);

        // 4. DETAILED BREAKDOWN
        printDetailedMetrics();
    }

    private double calculateMakespan() {
        double maxFinishTime = 0.0;
        for (Cloudlet cloudlet : cloudlets) {
            if (cloudlet.getFinishTime() > maxFinishTime) {
                maxFinishTime = cloudlet.getFinishTime();
            }
        }
        return maxFinishTime;
    }

    private double calculateTotalCost() {
        double totalCost = 0.0;

        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            TaskSpec task = tasks.get(i);
            Vm vm = vms.get(cloudletToVmMap.get(cloudlet.getId()));
            NodeSpec node = findNodeByVm(vm);

            // Computation cost: CTᵢ × Cᵖʳᵒᶜⱼ
            double computationTime = cloudlet.getActualCpuTime();
            double computationCost = computationTime * node.costPerCpuSecond;

            // Memory cost during execution
            double memoryCost = (task.memoryRequired / 1024.0) * node.costPerMem * computationTime;

            // Bandwidth cost for data transfer
            double dataTransferSize = (task.inputFileSize + task.outputFileSize) / 1024.0; // Convert to GB
            double bandwidthCost = dataTransferSize * node.costPerBw;

            totalCost += computationCost + memoryCost + bandwidthCost;
        }

        return totalCost;
    }

   private double calculateTotalEnergy() {
    double totalEnergy = 0.0;

    // Energy from hosts (built-in computation)
    for (NodeSpec node : nodes) {
        Datacenter dc = node.datacenter;
        for (Host host : dc.getHostList()) {
            // Enable utilization history tracking
            host.getUtilizationHistory().enable();
            
            // Get total energy consumption in Watt-Seconds (Joules)
            double hostEnergy = host.getUtilizationHistory().getEnergyConsumption();
            totalEnergy += hostEnergy;
        }
    }

    return totalEnergy;
}

private void printDetailedMetrics() {
    System.out.println("\n" + "-".repeat(80));
    System.out.println("DETAILED TASK METRICS");
    System.out.println("-".repeat(80));
    System.out.printf("%-10s %-15s %-15s %-15s %-15s\n",
            "Task ID", "VM ID", "Exec Time (s)", "Cost ($)", "Energy (WS)");

    for (int i = 0; i < cloudlets.size(); i++) {
        Cloudlet cloudlet = cloudlets.get(i);
        TaskSpec task = tasks.get(i);
        Vm vm = vms.get(cloudletToVmMap.get(cloudlet.getId()));
        NodeSpec node = findNodeByVm(vm);

        double execTime = cloudlet.getActualCpuTime();
        double computationCost = execTime * node.costPerCpuSecond;
        double memoryCost = (task.memoryRequired / 1024.0) * node.costPerMem * execTime;
        double bandwidthCost = ((task.inputFileSize + task.outputFileSize) / 1024.0) * node.costPerBw;
        double taskCost = computationCost + memoryCost + bandwidthCost;

        // Host energy consumption (simplified)
        Host host = vm.getHost();
        host.getUtilizationHistory().enable();
        double hostEnergy = host.getUtilizationHistory().getEnergyConsumption();
        
        // Approximate task energy based on execution time proportion
        double totalHostTime = host.getUtilizationHistory().getHistory().keySet().stream()
                                .mapToDouble(Double::doubleValue).max().orElse(1.0);
        double taskEnergy = (execTime / totalHostTime) * hostEnergy;

        System.out.printf("%-10s %-15d %-15.4f %-15.4f %-15.4f\n",
                task.taskId, vm.getId(), execTime, taskCost, taskEnergy);
    }
}
    private NodeSpec findNodeByVm(Vm vm) {
        for (NodeSpec node : nodes) {
            Datace@@nter dc = node.datacenter;
            if (dc.getVmList().contains(vm)) {
                return node;
            }
        }
        throw new RuntimeException("Node for VM ID " + vm.getId() + " not found.");
    }

    

}
```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/Datacenter#
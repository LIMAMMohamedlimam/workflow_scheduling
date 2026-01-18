error id: file://<WORKSPACE>/src/main/java/org/example/WorkflowMetrics.java:_empty_/Cloudlet#getTotalExecutionTime#
file://<WORKSPACE>/src/main/java/org/example/WorkflowMetrics.java
empty definition using pc, found symbol in pc: _empty_/Cloudlet#getTotalExecutionTime#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2578
uri: file://<WORKSPACE>/src/main/java/org/example/WorkflowMetrics.java
text:
```scala
package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.power.models.PowerModelHostSimple;
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
            Vm vm = vms.get(cloudletToVmMap.get((int)cloudlet.getId()));
            NodeSpec node = findNodeByVm(vm);

            // Computation cost: CTᵢ × Cᵖʳᵒᶜⱼ
            double computationTime = cloudlet.getTotalExecutio@@nTime();
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

        // Energy from hosts using mean CPU utilization
        for (NodeSpec node : nodes) {
            Datacenter dc = node.datacenter;
            for (Host host : dc.getHostList()) {
                double hostEnergy = calculateHostEnergy(host);
                totalEnergy += hostEnergy;
            }
        }

        return totalEnergy;
    }

    private double calculateHostEnergy(Host host) {
        // Get CPU utilization statistics
        var cpuStats = host.getCpuUtilizationStats();
        
        // Get mean CPU utilization
        double utilizationMean = cpuStats.getMean();
        
        // Get power model
        PowerModelHost powerModel = host.getPowerModel();
        
        // Calculate mean power consumption in Watts
        double meanPower = powerModel.getPower(utilizationMean);
        
        // Calculate total active time for this host
        double hostActiveTime = 0.0;
        for (Vm vm : host.getVmCreatedList()) {
            // Get the maximum finish time of cloudlets on this VM
            for (Cloudlet cloudlet : cloudlets) {
                if (cloudlet.getVm().equals(vm) && cloudlet.getFinishTime() > hostActiveTime) {
                    hostActiveTime = cloudlet.getFinishTime();
                }
            }
        }
        
        // If host has no VMs, use simulation clock
        if (hostActiveTime == 0.0) {
            hostActiveTime = simulation.clock();
        }
        
        // Energy = Power × Time (in Watt-Seconds/Joules)
        return meanPower * hostActiveTime;
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

            double execTime = cloudlet.getTotalExecutionTime();
            double computationCost = execTime * node.costPerCpuSecond;
            double memoryCost = (task.memoryRequired / 1024.0) * node.costPerMem * execTime;
            double bandwidthCost = ((task.inputFileSize + task.outputFileSize) / 1024.0) * node.costPerBw;
            double taskCost = computationCost + memoryCost + bandwidthCost;

            // Calculate task energy
            double taskEnergy = calculateTaskEnergy(cloudlet, vm);

            System.out.printf("%-10s %-15d %-15.4f %-15.4f %-15.4f\n",
                    task.taskId, vm.getId(), execTime, taskCost, taskEnergy);
        }
    }

    private double calculateTaskEnergy(Cloudlet cloudlet, Vm vm) {
        Host host = vm.getHost();
        PowerModelHost powerModel = host.getPowerModel();
        
        // Get host static power if using PowerModelHostSimple
        double hostStaticPower = 0.0;
        if (powerModel instanceof PowerModelHostSimple) {
            hostStaticPower = ((PowerModelHostSimple) powerModel).getStaticPower();
        }
        
        // Calculate VM's relative CPU utilization
        double vmRelativeCpuUtilization = vm.getCpuUtilizationStats().getMean() / host.getVmCreatedList().size();
        
        // Calculate VM power (share of host power)
        double hostStaticPowerByVm = hostStaticPower / host.getVmCreatedList().size();
        double vmPower = powerModel.getPower(vmRelativeCpuUtilization) - hostStaticPower + hostStaticPowerByVm;
        
        // Energy = Power × Time
        double execTime = cloudlet.getTotalExecutionTime();
        return vmPower * execTime;
    }

    private NodeSpec findNodeByVm(Vm vm) {
        for (NodeSpec node : nodes) {
            Datacenter dc = node.datacenter;
            for (Host host : dc.getHostList()) {
                if (host.getVmCreatedList().contains(vm)) {
                    return node;
                }
            }
        }
        return nodes.get(0);
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/Cloudlet#getTotalExecutionTime#
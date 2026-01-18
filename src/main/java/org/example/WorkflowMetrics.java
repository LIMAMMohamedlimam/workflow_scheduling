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
        double totalEnergyNew = calculateTotalEnergyNew();
        System.out.printf("\n3. TOTAL ENERGY: %.4f Watt-Seconds (Joules)\n", totalEnergyNew);

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
            double computationTime = cloudlet.getTotalExecutionTime();
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

    // private double calculateTotalEnergy() {
    //     double totalEnergy = 0.0;

    //     // Energy from hosts using mean CPU utilization
    //     for (NodeSpec node : nodes) {
    //         Datacenter dc = node.datacenter;
    //         for (Host host : dc.getHostList()) {
    //             double hostEnergy = calculateHostEnergy(host);
    //             totalEnergy += hostEnergy;
    //         }
    //     }

    //     return totalEnergy;
    // }

    private double calculateTotalEnergyNew() {
        double totalEnergy = 0.0;
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm vm = vms.get(cloudletToVmMap.get((int)cloudlet.getId()));


            // Calculate task energy using the simple method
            double taskEnergy = calculateTaskEnergySimple(cloudlet, vm);

            totalEnergy += taskEnergy;
        }
        return totalEnergy;
    }


    private double calculateTotalEnergy() {
    double totalEnergy = 0.0;

    for (NodeSpec node : nodes) {
        Datacenter dc = node.datacenter;
        for (Host host : dc.getHostList()) {
            if (host.getPowerModel() == null || host.getPowerModel() == PowerModelHost.NULL) {
                continue;
            }
            
            PowerModelHost powerModel = host.getPowerModel();
            
            // Get host mean utilization
            double meanUtilization = host.getCpuUtilizationStats().getMean();
            
            // If mean is 0, try to calculate from simulation time
            if (meanUtilization == 0.0 && !host.getVmCreatedList().isEmpty()) {
                // Estimate utilization from VMs
                double totalUtil = 0.0;
                for (Vm vm : host.getVmCreatedList()) {
                    totalUtil += vm.getCpuUtilizationStats().getMean() * vm.getPesNumber();
                }
                meanUtilization = totalUtil / host.getWorkingPesNumber();
            }
            
            // Calculate mean power
            double meanPower = powerModel.getPower(meanUtilization);
            
            // Calculate host active time (time when it had VMs)
            double hostActiveTime = simulation.clock();
            
            // Energy = Power × Time
            double hostEnergy = meanPower * hostActiveTime;
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

    // private void printDetailedMetrics() {
    //     System.out.println("\n" + "-".repeat(80));
    //     System.out.println("DETAILED TASK METRICS");
    //     System.out.println("-".repeat(80));
    //     System.out.printf("%-10s %-15s %-15s %-15s %-15s\n",
    //             "Task ID", "VM ID", "Exec Time (s)", "Cost ($)", "Energy (WS)");

    //     for (int i = 0; i < cloudlets.size(); i++) {
    //         Cloudlet cloudlet = cloudlets.get(i);
    //         TaskSpec task = tasks.get(i);
    //         Vm vm = vms.get(cloudletToVmMap.get((int)cloudlet.getId()));
    //         NodeSpec node = findNodeByVm(vm);

    //         double execTime = cloudlet.getTotalExecutionTime();
    //         double computationCost = execTime * node.costPerCpuSecond;
    //         double memoryCost = (task.memoryRequired / 1024.0) * node.costPerMem * execTime;
    //         double bandwidthCost = ((task.inputFileSize + task.outputFileSize) / 1024.0) * node.costPerBw;
    //         double taskCost = computationCost + memoryCost + bandwidthCost;

    //         // Calculate task energy
    //         double taskEnergy = calculateTaskEnergy(cloudlet, vm);

    //         System.out.printf("%-10s %-15d %-15.4f %-15.4f %-15.4f\n",
    //                 task.taskId, vm.getId(), execTime, taskCost, taskEnergy);
    //     }
    // }

    private void printDetailedMetrics() {
    System.out.println("\n" + "-".repeat(80));
    System.out.println("DETAILED TASK METRICS");
    System.out.println("-".repeat(80));
    System.out.printf("%-10s %-15s %-15s %-15s %-15s\n",
            "Task ID", "VM ID", "Exec Time (s)", "Cost ($)", "Energy (WS)");

    for (int i = 0; i < cloudlets.size(); i++) {
        Cloudlet cloudlet = cloudlets.get(i);
        TaskSpec task = tasks.get(i);
        Vm vm = vms.get(cloudletToVmMap.get((int)cloudlet.getId()));
        NodeSpec node = findNodeByVm(vm);

        double execTime = cloudlet.getTotalExecutionTime();
        double computationCost = execTime * node.costPerCpuSecond;
        double memoryCost = (task.memoryRequired / 1024.0) * node.costPerMem * execTime;
        double bandwidthCost = ((task.inputFileSize + task.outputFileSize) / 1024.0) * node.costPerBw;
        double taskCost = computationCost + memoryCost + bandwidthCost;

        // Calculate task energy using the simple method
        double taskEnergy = calculateTaskEnergySimple(cloudlet, vm);

        System.out.printf("%-10s %-15d %-15.4f %-15.4f %-15.4f\n",
                task.taskId, vm.getId(), execTime, taskCost, taskEnergy);
    }
}


    // private double calculateTaskEnergy(Cloudlet cloudlet, Vm vm) {
    //     Host host = vm.getHost();
    //     PowerModelHost powerModel = host.getPowerModel();
        
    //     // Get host static power if using PowerModelHostSimple
    //     double hostStaticPower = 0.0;
    //     if (powerModel instanceof PowerModelHostSimple) {
    //         hostStaticPower = ((PowerModelHostSimple) powerModel).getStaticPower();
    //     }
        
    //     // Calculate VM's relative CPU utilization
    //     double vmRelativeCpuUtilization = vm.getCpuUtilizationStats().getMean() / host.getVmCreatedList().size();
        
    //     // Calculate VM power (share of host power)
    //     double hostStaticPowerByVm = hostStaticPower / host.getVmCreatedList().size();
    //     double vmPower = powerModel.getPower(vmRelativeCpuUtilization) - hostStaticPower + hostStaticPowerByVm;
        
    //     // Energy = Power × Time
    //     double execTime = cloudlet.getTotalExecutionTime();
    //     return vmPower * execTime;
    // }

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

    private double calculateTaskEnergy(Cloudlet cloudlet, Vm vm) {
    Host host = vm.getHost();
    
    // Check if host has a power model
    if (host.getPowerModel() == null || host.getPowerModel() == PowerModelHost.NULL) {
        System.err.println("Warning: Host " + host.getId() + " has no PowerModel set. Energy will be 0.");
        return 0.0;
    }
    
    PowerModelHost powerModel = host.getPowerModel();
    
    // Get execution time
    double execTime = cloudlet.getTotalExecutionTime();
    if (execTime == 0.0) {
        return 0.0;
    }
    
    // Get host static power
    double hostStaticPower = 0.0;
    if (powerModel instanceof PowerModelHostSimple) {
        hostStaticPower = ((PowerModelHostSimple) powerModel).getStaticPower();
    } else {
        // Estimate static power: power at 0% utilization
        hostStaticPower = powerModel.getPower(0.0);
    }
    
    // Calculate VM's share of host resources
    double vmPesTotalHostPes = (double) vm.getPesNumber() / host.getWorkingPesNumber();
    
    // Get mean CPU utilization for the host during cloudlet execution
    double hostMeanUtilization = host.getCpuUtilizationStats().getMean();
    
    // If host stats are zero, estimate from VM or cloudlet
    if (hostMeanUtilization == 0.0) {
        double vmUtil = vm.getCpuUtilizationStats().getMean();
        if (vmUtil > 0) {
            hostMeanUtilization = vmUtil * vmPesTotalHostPes;
        } else {
            // Fallback: assume cloudlet uses its allocated PEs fully
            hostMeanUtilization = vmPesTotalHostPes;
        }
    }
    
    // Calculate power consumption at the host's mean utilization
    double hostMeanPower = powerModel.getPower(hostMeanUtilization);
    
    // VM's share of the host power (proportional to its PEs)
    double vmStaticPowerShare = hostStaticPower * vmPesTotalHostPes;
    double hostDynamicPower = hostMeanPower - hostStaticPower;
    double vmDynamicPowerShare = hostDynamicPower * vmPesTotalHostPes;
    
    double vmTotalPower = vmStaticPowerShare + vmDynamicPowerShare;
    
    // Energy = Power × Time (in Watt-Seconds/Joules)
    return vmTotalPower * execTime;
}

// Simpler alternative that's more reliable
private double calculateTaskEnergySimple(Cloudlet cloudlet, Vm vm) {
    Host host = vm.getHost();
    
    if (host.getPowerModel() == null || host.getPowerModel() == PowerModelHost.NULL) {
        return 0.0;
    }
    
    PowerModelHost powerModel = host.getPowerModel();
    double execTime = cloudlet.getTotalExecutionTime();
    
    if (execTime == 0.0) {
        return 0.0;
    }
    
    // Calculate power based on host's mean utilization
    double hostMeanUtilization = host.getCpuUtilizationStats().getMean();
    
    // If stats are not available, estimate utilization
    if (hostMeanUtilization == 0.0) {
        // Estimate based on PE allocation
        double vmPeRatio = (double) vm.getPesNumber() / host.getWorkingPesNumber();
        hostMeanUtilization = Math.min(1.0, vmPeRatio);
    }
    
    // Calculate host power at mean utilization
    double hostPower = powerModel.getPower(hostMeanUtilization);
    
    // Distribute power proportionally to PE count
    double cloudletPowerShare = hostPower * ((double) cloudlet.getPesNumber() / host.getWorkingPesNumber());
    
    // Energy = Power × Time
    return cloudletPowerShare * execTime;
}


}

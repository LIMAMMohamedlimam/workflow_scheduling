// SOAScheduling.java (Shortest Operation Assignment - Cost-aware)
package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class SOAScheduling implements SchedulingAlgorithm {
    @Override
    public Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms, 
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        Map<Integer, Integer> mapping = new HashMap<>();
        
        // Track VM availability
        Map<Vm, Double> vmReadyTime = new HashMap<>();
        for (Vm vm : vms) {
            vmReadyTime.put(vm, 0.0);
        }
        
        // For each cloudlet, assign to VM with shortest operation cost
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            TaskSpec task = tasks.get(i);
            
            double minCost = Double.MAX_VALUE;
            Vm selectedVm = null;
            
            for (int j = 0; j < vms.size(); j++) {
                Vm vm = vms.get(j);
                NodeSpec node = nodes.get(j);
                
                // Calculate execution time
                double execTime = (double)task.instructions / vm.getMips();
                
                // Calculate total cost
                double computeCost = execTime * node.costPerCpuSecond;
                double memoryCost = (task.memoryRequired / 1024.0) * node.costPerMem * execTime;
                double bwCost = ((task.inputFileSize + task.outputFileSize) / 1024.0) * node.costPerBw;
                double totalCost = computeCost + memoryCost + bwCost;
                
                if (totalCost < minCost) {
                    minCost = totalCost;
                    selectedVm = vm;
                }
            }
            
            cloudlet.setVm(selectedVm);
            mapping.put((int)cloudlet.getId(), (int)selectedVm.getId());
            
            double execTime = (double)task.instructions / selectedVm.getMips();
            vmReadyTime.put(selectedVm, vmReadyTime.get(selectedVm) + execTime);
        }
        
        System.out.println("✓ SOA (Shortest Operation Assignment) scheduling completed");
        return mapping;
    }
}

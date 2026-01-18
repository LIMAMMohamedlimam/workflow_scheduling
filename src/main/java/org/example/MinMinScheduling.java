// MinMinScheduling.java
package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class MinMinScheduling implements SchedulingAlgorithm {
    @Override
    public Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms, 
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        Map<Integer, Integer> mapping = new HashMap<>();
        
        // Track VM availability time
        Map<Vm, Double> vmReadyTime = new HashMap<>();
        for (Vm vm : vms) {
            vmReadyTime.put(vm, 0.0);
        }
        
        // Create list of unscheduled cloudlets
        Set<Integer> unscheduled = new HashSet<>();
        for (int i = 0; i < cloudlets.size(); i++) {
            unscheduled.add(i);
        }
        
        // Min-Min: repeatedly select task with minimum completion time
        while (!unscheduled.isEmpty()) {
            double minCompletionTime = Double.MAX_VALUE;
            int selectedTask = -1;
            Vm selectedVm = null;
            
            // For each unscheduled task, find VM that gives minimum completion time
            for (int i : unscheduled) {
                for (Vm vm : vms) {
                    double execTime = (double)tasks.get(i).instructions / vm.getMips();
                    double completionTime = vmReadyTime.get(vm) + execTime;
                    
                    if (completionTime < minCompletionTime) {
                        minCompletionTime = completionTime;
                        selectedTask = i;
                        selectedVm = vm;
                    }
                }
            }
            
            // Assign selected task to selected VM
            Cloudlet cloudlet = cloudlets.get(selectedTask);
            cloudlet.setVm(selectedVm);
            mapping.put((int)cloudlet.getId(), (int)selectedVm.getId());
            
            // Update VM ready time
            double execTime = (double)tasks.get(selectedTask).instructions / selectedVm.getMips();
            vmReadyTime.put(selectedVm, vmReadyTime.get(selectedVm) + execTime);
            
            unscheduled.remove(selectedTask);
        }
        
        System.out.println("✓ Min-Min scheduling completed");
        return mapping;
    }
}

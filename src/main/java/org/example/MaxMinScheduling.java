// MaxMinScheduling.java
package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class MaxMinScheduling implements SchedulingAlgorithm {
    @Override
    public Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms, 
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        Map<Integer, Integer> mapping = new HashMap<>();
        
        Map<Vm, Double> vmReadyTime = new HashMap<>();
        for (Vm vm : vms) {
            vmReadyTime.put(vm, 0.0);
        }
        
        Set<Integer> unscheduled = new HashSet<>();
        for (int i = 0; i < cloudlets.size(); i++) {
            unscheduled.add(i);
        }
        
        // Max-Min: repeatedly select task with maximum minimum completion time
        while (!unscheduled.isEmpty()) {
            double maxMinCompletionTime = -1;
            int selectedTask = -1;
            Vm selectedVm = null;
            
            // For each unscheduled task, find its minimum completion time across all VMs
            for (int i : unscheduled) {
                double minCompletionTime = Double.MAX_VALUE;
                Vm bestVm = null;
                
                for (Vm vm : vms) {
                    double execTime = (double)tasks.get(i).instructions / vm.getMips();
                    double completionTime = vmReadyTime.get(vm) + execTime;
                    
                    if (completionTime < minCompletionTime) {
                        minCompletionTime = completionTime;
                        bestVm = vm;
                    }
                }
                
                // Select task with maximum minimum completion time
                if (minCompletionTime > maxMinCompletionTime) {
                    maxMinCompletionTime = minCompletionTime;
                    selectedTask = i;
                    selectedVm = bestVm;
                }
            }
            
            // Assign selected task
            Cloudlet cloudlet = cloudlets.get(selectedTask);
            cloudlet.setVm(selectedVm);
            mapping.put((int)cloudlet.getId(), (int)selectedVm.getId());
            
            double execTime = (double)tasks.get(selectedTask).instructions / selectedVm.getMips();
            vmReadyTime.put(selectedVm, vmReadyTime.get(selectedVm) + execTime);
            
            unscheduled.remove(selectedTask);
        }
        
        System.out.println("✓ Max-Min scheduling completed");
        return mapping;
    }
}

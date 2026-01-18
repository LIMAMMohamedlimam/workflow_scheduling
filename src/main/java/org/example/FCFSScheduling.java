package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class FCFSScheduling implements SchedulingAlgorithm {
    @Override
    public Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms, 
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        Map<Integer, Integer> mapping = new HashMap<>();
        
        // Track VM availability time
        Map<Vm, Double> vmAvailabilityTime = new HashMap<>();
        for (Vm vm : vms) {
            vmAvailabilityTime.put(vm, 0.0);
        }
        
        // Assign each cloudlet to the first available VM
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            
            // Find VM that becomes available earliest
            Vm earliestVm = Collections.min(vmAvailabilityTime.entrySet(), 
                                           Map.Entry.comparingByValue()).getKey();
            
            cloudlet.setVm(earliestVm);
            mapping.put((int)cloudlet.getId(), (int)earliestVm.getId());
            
            // Update VM availability time
            double execTime = (double)tasks.get(i).instructions / earliestVm.getMips();
            vmAvailabilityTime.put(earliestVm, vmAvailabilityTime.get(earliestVm) + execTime);
        }
        
        System.out.println("✓ FCFS scheduling completed");
        return mapping;
    }
}

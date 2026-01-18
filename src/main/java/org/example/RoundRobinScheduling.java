// RoundRobinScheduling.java
package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class RoundRobinScheduling implements SchedulingAlgorithm {
    @Override
    public Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms, 
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        Map<Integer, Integer> mapping = new HashMap<>();
        
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = vms.get(i % vms.size());
            
            cloudlet.setVm(assignedVm);
            mapping.put((int)cloudlet.getId(), (int)assignedVm.getId());
            // System.out.println("  Cloudlet " + cloudlet.getId() + " assigned to VM " + assignedVm.getId());
        }
        
        System.out.println("✓ Round Robin scheduling completed");
        return mapping;
    }
}

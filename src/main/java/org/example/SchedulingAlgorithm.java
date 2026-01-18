// SchedulingAlgorithm.java
package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.List;
import java.util.Map;

public interface SchedulingAlgorithm {
    /**
     * Assigns cloudlets to VMs and returns the mapping
     * @param cloudlets List of cloudlets to schedule
     * @param vms List of available VMs
     * @param tasks Task specifications
     * @param nodes Node specifications
     * @return Map of cloudlet ID to VM ID
     */
    Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms, 
                                   List<TaskSpec> tasks, List<NodeSpec> nodes);
}
